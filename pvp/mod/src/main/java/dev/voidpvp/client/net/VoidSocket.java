package dev.voidpvp.client.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidpvp.client.VoidLog;
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
 */
public final class VoidSocket implements LiveState.Sink {

    /** What the rest of the mod wants to know about the link. */
    public interface Listener {
        /** {@code init}: the whole world of state, library included, in full. */
        void onInit(Loadout loadout, List<Loadout> loadouts, GlobalSettings settings);

        void onLoadout(Loadout loadout);

        void onSettings(GlobalSettings settings);

        void onLinkChanged(boolean up);

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

    public void sendSession(double fpsAvg, long playedMs, String server, String loadoutId) {
        if (isUp()) {
            send(Protocol.session(fpsAvg, playedMs, server, loadoutId));
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
