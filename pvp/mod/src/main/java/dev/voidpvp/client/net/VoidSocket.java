package dev.voidpvp.client.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidpvp.client.VoidLog;
import dev.voidpvp.client.cosmetic.CosmeticState;
import dev.voidpvp.client.cosmetic.EquippedSet;
import dev.voidpvp.client.state.GlobalSettings;
import dev.voidpvp.client.state.HudItem;
import dev.voidpvp.client.state.LiveState;
import dev.voidpvp.client.state.Loadout;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The WS client to the Rust launcher (§6.9, {@code schema/protocol.json}).
 *
 * <p>Netty ships with Minecraft, so the transport costs us nothing. The
 * launcher binds {@code ws://127.0.0.1:<port>} and passes the port and a
 * per-spawn session token as {@code -Dvoid.port} and {@code -Dvoid.token}; we
 * connect, send {@code hello} with the token and wait for {@code init} before
 * applying anything, because {@code init} is the mod's entire world of state.
 * If the launcher is not there we retry with backoff and the game carries on
 * regardless — everything owed is replayed once it answers.</p>
 *
 * <p>It is also {@link CosmeticState}'s sink: the mod never talks HTTP, so who wears what goes
 * to the launcher here and comes back as {@code cosmetics} and remote {@code emote}.</p>
 */
public final class VoidSocket implements LiveState.Sink, CosmeticState.Sink {

    /** What the rest of the mod wants to know about the link. */
    public interface Listener {
        /** {@code init}: the whole world of state, library included, in full. */
        void onInit(Loadout loadout, List<Loadout> loadouts, GlobalSettings settings);

        void onLoadout(Loadout loadout);

        void onSettings(GlobalSettings settings);

        /**
         * Rust's {@code library}: the whole library after a launcher-side change, or after an
         * in-game one Rust could not store. Default no-op for listeners that predate it.
         */
        default void onLibrary(List<Loadout> loadouts) {
        }

        void onLinkChanged(boolean up);

        /**
         * {@code cosmetics}: who wears what. Either argument may be null, meaning unchanged:
         * {@code self} the local player's whole set (null when there is no online session),
         * {@code players} other VOID players' sets as a delta. The wire carries no ownership.
         * Called on {@code void-ws}.
         */
        void onCosmetics(EquippedSet self, List<EquippedSet> players);

        /**
         * Remote {@code emote}: another VOID player on this server started {@code id} at the
         * sender's wall-clock {@code atMs} (0 when unknown). Called on {@code void-ws}.
         */
        void onRemoteEmote(String uuid, String id, long atMs);

        /**
         * The launcher speaks another protocol version. Mod and launcher ship
         * together, so this only happens when they were mixed by hand (§7).
         */
        void onVersionMismatch(int launcherVersion);
    }

    private final int port;
    private final String token;
    private final String mcVersion;
    private final String modVersion;
    private final Listener listener;
    private final OutboundQueue queue = new OutboundQueue();
    private final Backoff backoff = Backoff.defaults();

    private EventLoopGroup group;
    private volatile Channel channel;
    private volatile boolean handshakeDone;
    private volatile boolean stopped;

    public VoidSocket(int port, String token, String mcVersion, String modVersion,
                      Listener listener) {
        this.port = port;
        this.token = token;
        this.mcVersion = mcVersion;
        this.modVersion = modVersion;
        this.listener = listener;
    }

    /** True when the link is up and the handshake finished. */
    public boolean isUp() {
        Channel ch = channel;
        return handshakeDone && ch != null && ch.isActive();
    }

    public void start() {
        if (group != null) {
            return;
        }
        group = new NioEventLoopGroup(1, new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "void-ws");
                t.setDaemon(true);
                return t;
            }
        });
        connect();
    }

    public void stop() {
        stopped = true;
        Channel ch = channel;
        if (ch != null && ch.isActive()) {
            try {
                ch.writeAndFlush(new CloseWebSocketFrame()).await(200, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // Shutting down; the socket dies with the process anyway.
            }
        }
        EventLoopGroup g = group;
        if (g != null) {
            g.shutdownGracefully(0, 200, TimeUnit.MILLISECONDS);
        }
    }

    private void connect() {
        if (stopped) {
            return;
        }
        final URI uri = URI.create("ws://127.0.0.1:" + port + "/");
        final WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders());
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, Boolean.TRUE)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Integer.valueOf(3000))
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new HttpClientCodec());
                        p.addLast(new HttpObjectAggregator(1 << 20));
                        p.addLast(new Handler(handshaker));
                    }
                });
        b.connect("127.0.0.1", port).addListener(new io.netty.channel.ChannelFutureListener() {
            @Override
            public void operationComplete(io.netty.channel.ChannelFuture f) {
                if (f.isSuccess()) {
                    channel = f.channel();
                } else {
                    scheduleReconnect();
                }
            }
        });
    }

    private void scheduleReconnect() {
        if (stopped || group == null) {
            return;
        }
        handshakeDone = false;
        long delay = backoff.nextDelayMs();
        group.schedule(new Runnable() {
            @Override
            public void run() {
                connect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    // -----------------------------------------------------------------
    // Sending
    // -----------------------------------------------------------------

    /** Sends now, or remembers it for the flush after the next {@code init}. */
    private void send(JsonObject message) {
        Channel ch = channel;
        if (handshakeDone && ch != null && ch.isActive()) {
            ch.writeAndFlush(new TextWebSocketFrame(message.toString()));
        }
    }

    @Override
    public void state(String loadoutId, Map<String, JsonElement> patch) {
        if (patch == null || patch.isEmpty()) {
            return;
        }
        if (isUp()) {
            send(Protocol.state(loadoutId, patch));
        } else {
            queue.addState(loadoutId, patch);
        }
    }

    @Override
    public void hud(String loadoutId, List<HudItem> items) {
        if (isUp()) {
            send(Protocol.hud(loadoutId, items));
        } else {
            queue.addHud(loadoutId, items);
        }
    }

    @Override
    public void globals(Map<String, JsonElement> patch) {
        if (patch == null || patch.isEmpty()) {
            return;
        }
        if (isUp()) {
            send(Protocol.globals(patch));
        } else {
            queue.addGlobals(patch);
        }
    }

    @Override
    public void loadoutSave(JsonObject loadout) {
        String id = loadout.has("id") ? loadout.get("id").getAsString() : "";
        if (isUp()) {
            send(Protocol.loadoutSave(loadout));
        } else {
            queue.addLoadoutSave(id, loadout);
        }
    }

    @Override
    public void loadoutDelete(String loadoutId) {
        if (isUp()) {
            send(Protocol.loadoutDelete(loadoutId));
        } else {
            queue.addLoadoutDelete(loadoutId);
        }
    }

    @Override
    public void loadoutSelect(String loadoutId) {
        if (isUp()) {
            send(Protocol.loadoutSelect(loadoutId));
        } else {
            queue.setSelected(loadoutId);
        }
    }

    public void sendServer(String host, boolean connected, int serverPort) {
        JsonObject message = Protocol.server(host, connected, serverPort);
        if (isUp()) {
            send(message);
        } else {
            queue.setServer(message);
        }
    }

    /**
     * {@code hotkey}: a global hotkey the player pressed (§6.3).
     *
     * <p>Dropped rather than queued when the link is down. It is a notification
     * about a key press that already happened — the state it produced travels in
     * its own {@code state} message and <em>is</em> queued — so replaying it after
     * a reconnect would tell the launcher about a keystroke from minutes ago.</p>
     */
    public void sendHotkey(String id) {
        if (isUp()) {
            send(Protocol.hotkey(id));
        }
    }

    // -----------------------------------------------------------------
    // CosmeticState.Sink
    // -----------------------------------------------------------------

    /**
     * {@code cosmetics_query}, chunked to {@link Protocol#MAX_QUERY_UUIDS} per frame.
     *
     * <p>Dropped rather than queued when the link is down, like {@code hotkey}: it asks about
     * the players on screen <em>now</em>, and a list replayed after a reconnect would ask about
     * players who have long since left. The caller asks again for whoever is still in view.</p>
     */
    @Override
    public void query(List<String> uuids) {
        if (uuids == null || uuids.isEmpty() || !isUp()) {
            return;
        }
        for (int i = 0; i < uuids.size(); i += Protocol.MAX_QUERY_UUIDS) {
            List<String> chunk = uuids.subList(i, Math.min(uuids.size(), i + Protocol.MAX_QUERY_UUIDS));
            JsonObject message = Protocol.cosmeticsQuery(chunk);
            if (message.getAsJsonArray("uuids").size() > 0) {
                send(message);
            }
        }
    }

    /**
     * {@code emote}: the local player started one. Dropped when the link is down: replayed
     * after a reconnect it would play minutes late on every other screen.
     */
    @Override
    public void emote(String id) {
        if (id != null && isUp()) {
            send(Protocol.emote(id));
        }
    }

    /**
     * {@code equip} for one slot, {@code {slot, id}}. Queued like {@code state} when the link is
     * down, and the queue keeps only the latest per slot, because each message is the whole slot.
     *
     * <p>{@code tint} is not sent: {@code protocol.json}'s {@code equip} has no tint field, so a
     * recolour chosen in game lasts the session until the protocol grows one. A tint change
     * still arrives here (CosmeticState reports the slot), and re-sending the same id is
     * harmless — the backend stores what it already had.</p>
     */
    @Override
    public void equip(String slot, String id, String tint) {
        if (slot == null) {
            return;
        }
        JsonObject message = Protocol.equip(slot, id);
        if (isUp()) {
            send(message);
        } else {
            queue.addEquip(message);
        }
    }

    /**
     * The emote wheel changed in game. <b>Nothing is sent</b>: {@code protocol.json} has no
     * message for the wheel, so it lasts the session; the launcher Store edits the stored one,
     * which comes back in {@code cosmetics.self.emotes}. Kept so the sink stays complete and the
     * day the wire grows a message this is the one place that changes.
     */
    @Override
    public void emotes(List<String> ids) {
        // Intentionally a no-op on the wire; see the javadoc.
    }

    public void sendSession(double fpsAvg, long playedMs, String server, String loadoutId) {
        sendSession(fpsAvg, -1, playedMs, server, loadoutId);
    }

    /** As {@link #sendSession(double, long, String, String)}, with the session's mean ping. */
    public void sendSession(double fpsAvg, double pingAvg, long playedMs, String server,
                            String loadoutId) {
        if (isUp()) {
            send(Protocol.session(fpsAvg, pingAvg, playedMs, server, loadoutId));
        }
        // Telemetry is a summary of a window that has passed; replaying a
        // stale one after a reconnect would double-count, so it is dropped.
    }

    private void flushQueue() {
        for (JsonObject message : queue.drain()) {
            send(message);
        }
    }

    // -----------------------------------------------------------------
    // Receiving
    // -----------------------------------------------------------------

    private void onText(String text) {
        Protocol.Inbound in = Protocol.parse(text);
        switch (in.kind) {
            case INIT:
                if (in.version != Protocol.VERSION) {
                    VoidLog.error("launcher speaks protocol v" + in.version + ", mod speaks v"
                            + Protocol.VERSION + " — ignoring its state");
                    listener.onVersionMismatch(in.version);
                    return;
                }
                listener.onInit(in.loadout, in.loadouts, in.settings);
                flushQueue();
                break;
            case LOADOUT:
                listener.onLoadout(in.loadout);
                break;
            case SETTINGS:
                listener.onSettings(in.settings);
                break;
            case LIBRARY:
                listener.onLibrary(in.loadouts);
                break;
            case COSMETICS:
                listener.onCosmetics(in.self, in.players);
                break;
            case EMOTE:
                listener.onRemoteEmote(in.uuid, in.emoteId, in.atMs);
                break;
            default:
                // Unknown `t`: ignored, never an error (§7).
                break;
        }
    }

    private final class Handler extends SimpleChannelInboundHandler<Object> {

        private final WebSocketClientHandshaker handshaker;

        Handler(WebSocketClientHandshaker handshaker) {
            this.handshaker = handshaker;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            handshaker.handshake(ctx.channel());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            boolean wasUp = handshakeDone;
            handshakeDone = false;
            channel = null;
            if (wasUp) {
                VoidLog.info("launcher link closed; reconnecting");
                listener.onLinkChanged(false);
            }
            scheduleReconnect();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            Channel ch = ctx.channel();
            if (!handshaker.isHandshakeComplete()) {
                if (msg instanceof FullHttpResponse) {
                    handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                    handshakeDone = true;
                    backoff.reset();
                    VoidLog.info("launcher link up on 127.0.0.1:" + port);
                    send(Protocol.hello(mcVersion, modVersion, token));
                    listener.onLinkChanged(true);
                }
                return;
            }
            if (msg instanceof TextWebSocketFrame) {
                onText(((TextWebSocketFrame) msg).text());
            } else if (msg instanceof PingWebSocketFrame) {
                ch.writeAndFlush(new PongWebSocketFrame(
                        ((WebSocketFrame) msg).content().retain()));
            } else if (msg instanceof CloseWebSocketFrame) {
                ch.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            // A launcher that is not running is the normal case, not an error.
            if (handshakeDone) {
                VoidLog.warn("launcher link error: " + cause);
            }
            ctx.close();
        }
    }
}
