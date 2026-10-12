package dev.voidmc.mettle.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.cards.Mythics;
import dev.voidmc.mettle.client.overlay.MettleActions;
import dev.voidmc.mettle.client.overlay.MettleState;
import dev.voidmc.mettle.core.CardData;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.Curves;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.zip.Deflater;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.impl.attachment.sync.AttachmentSync;
import net.fabricmc.fabric.impl.attachment.sync.clientbound.ClientboundAttachmentSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * Audit only (branch audit-server): Mettle on a real dedicated server with two real clients.
 *
 * <p>Role A ({@code -Dmettle.audit.role=A}, run through runClientGameTest so the project's EULA opt-in for test runs
 * applies) hosts Fabric's in-process {@code DedicatedServer} on port 25599 and joins it over TCP as Alice. Role B (a second
 * client JVM, {@code tools/audit/game.sh}) joins the same port as Bob. They step through the script together using marker
 * files in {@code -Dmettle.audit.dir}: background, a draft kept and slotted, a trade between them, a world-first race, the
 * public Mythics each sees on the other, and (Bob) payload sizes and bandwidth with a 300-card binder. Each writes
 * {@code report-<role>.txt}. Role A then keeps the server up until {@code extras.done} so other clients (no Mettle, vanilla,
 * an older Mettle) can try to join.
 */
public class ServerAudit implements FabricClientGameTest {
	static final String ROLE = System.getProperty("mettle.audit.role", "");
	static final Path DIR = Path.of(System.getProperty("mettle.audit.dir", "audit"));
	static final int PORT = Integer.getInteger("mettle.audit.port", 25599);
	static final ResourceKey<Skill> MINING = Progress.skill("mining");
	static final String MINING_ID = "mettle:mining";

	static final AtomicLong WIRE_IN = new AtomicLong();
	static final AtomicLong RAW_IN = new AtomicLong();
	static final Map<String, AtomicLong> PACKETS = new ConcurrentHashMap<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (ROLE.isEmpty()) {
			return;
		}
		try {
			Files.createDirectories(DIR);
			log("role " + ROLE + " start");
			if (ROLE.equals("A") || ROLE.equals("H")) {
				host(context);
			} else if (ROLE.equals("M")) {
				mettleOnBareServer(context);
			} else {
				guest(context);
			}
			log("role " + ROLE + " end");
		} catch (Throwable t) {
			StringWriter w = new StringWriter();
			t.printStackTrace(new PrintWriter(w));
			log("FAIL " + w);
			mark("failed");
			if (t instanceof RuntimeException r) {
				throw r;
			}
			if (t instanceof Error e) {
				throw e;
			}
			throw new RuntimeException(t);
		}
	}

	// ------------------------------------------------------------------ roles

	private static void host(ClientGameTestContext c) throws IOException {
		// Server-side Mettle config differs from the defaults a remote client has (cards.json lives on the server only).
		Path cfg = Path.of("config", "mettle", "cards.json");
		Files.createDirectories(cfg.getParent());
		Files.writeString(cfg, "{\"binder_max\": 280, \"attune_ticks\": 20, \"far_horizon_blocks\": 200}");
		Properties props = new Properties();
		props.setProperty("server-port", String.valueOf(PORT));
		props.setProperty("max-players", "20");
		props.setProperty("white-list", "false");
		props.setProperty("enforce-whitelist", "false");
		props.setProperty("online-mode", "false");
		props.setProperty("view-distance", "6");
		props.setProperty("simulation-distance", "4");
		props.setProperty("pause-when-empty-seconds", "0");
		try (TestDedicatedServerContext server = c.worldBuilder().createServer(props)) {
			server.runCommand("difficulty peaceful");
			server.runCommand("gamerule fall_damage false");
			server.runCommand("gamerule spawn_mobs false");
			log("server up: dedicated=" + server.computeOnServer(s -> s.isDedicatedServer()) + " port=" + PORT);
			mark("server");
			try (TestDedicatedServerConnection connection = server.connect()) {
				installCounters(c);
				c.waitTicks(20);
				String me = c.computeOnClient(mc -> mc.getUser().getName());
				write("name", me);
				log("host player name " + me);
				server.runCommand("op " + me);
				if (ROLE.equals("H")) {
					// Host only: straight to other clients trying to join.
					mark("waiting_extras");
					waitFile(DIR.resolve("extras.done"), 1500, c);
					return;
				}
				await("B", "joined", 180, c);
				server.runCommand("op Bob");
				mark("opped");

				common(c, "B", server);

				// A world-first race: both teleported past far_horizon_blocks in one command (one tick).
				await("B", "ready_race", 120, c);
				server.runCommand("execute as @a at @s run tp @s ~260 ~ ~");
				mark("tp");
				race(c, "B");
				// Death and respawn of the Mythic holder: do the others still see their public Mythics?
				String holder = server.computeOnServer(sv -> sv.getPlayerList().getPlayers().stream()
					.filter(pl -> pl.getAttached(Mythics.PUBLIC) != null).map(pl -> pl.getName().getString()).findFirst().orElse(""));
				log("RESPAWN holder before death: " + holder);
				if (!holder.isEmpty()) {
					server.runCommand("gamerule immediate_respawn true");
					server.runCommand("kill " + holder);
				}
				mark("killed");
				respawn(c, "B", server);

				await("B", "done", 600, c);
				log("server avg tick ms " + server.computeOnServer(s -> s.getAverageTickTimeNanos() / 1e6));
				ledgerReport(server);
				mark("waiting_extras");
				log("waiting for extras.done (other clients trying to join)");
				waitFile(DIR.resolve("extras.done"), 1500, c);
				log("server avg tick ms at end " + server.computeOnServer(s -> s.getAverageTickTimeNanos() / 1e6));
			}
		}
	}

	private static void guest(ClientGameTestContext c) {
		waitFile(DIR.resolve("A.server"), 300, c);
		c.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString("localhost:" + PORT),
			new ServerData("audit", "localhost:" + PORT, ServerData.Type.OTHER), false, null));
		until(c, "joined the server", 120, () -> c.computeOnClient(mc -> mc.player != null && mc.level != null));
		installCounters(c);
		mark("joined");
		await("A", "opped", 120, c);
		common(c, "A", null);
		mark("ready_race");
		await("A", "tp", 120, c);
		race(c, "A");
		await("A", "killed", 120, c);
		respawn(c, "A", null);
		bandwidth(c);
		c.runOnClient(mc -> mc.disconnectFromWorld(Component.literal("audit done")));
		c.waitTicks(40);
		c.setScreen(TitleScreen::new);
		mark("done");
	}

	/** Role M: a client with Mettle joins a Fabric server without Mettle (port from mettle.audit.port), then uses Mettle. */
	private static void mettleOnBareServer(ClientGameTestContext c) {
		waitFile(DIR.resolve("bare.server"), 300, c);
		c.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString("localhost:" + PORT),
			new ServerData("bare", "localhost:" + PORT, ServerData.Type.OTHER), false, null));
		boolean joined = untilSoft(c, 60, () -> c.computeOnClient(mc -> mc.player != null && mc.level != null));
		log("M joined a server without Mettle: " + joined + "; screen " + c.computeOnClient(mc -> String.valueOf(mc.gui.screen())));
		if (!joined) {
			c.takeScreenshot("audit_mettle_on_bare_refused");
			mark("done");
			return;
		}
		mark("joined");
		c.waitTicks(40);
		for (String action : new String[] {"chooseBackground", "keepCard", "slotCard", "tradeRequest", "state"}) {
			String result = c.computeOnClient(mc -> {
				try {
					switch (action) {
						case "chooseBackground" -> {
							JsonObject req = new JsonObject();
							req.addProperty("background", "mettle:ranger");
							MettleActions.chooseBackground(req);
						}
						case "keepCard" -> MettleActions.keepCard(MINING_ID, 0, true);
						case "slotCard" -> MettleActions.slotCard("1");
						case "tradeRequest" -> MettleActions.tradeRequest("Nobody");
						default -> {
							return "state ok, " + MettleState.build(mc).toString().length() + " chars, background "
								+ CharacterStore.get(mc.player).background();
						}
					}
					return "sent";
				} catch (Throwable t) {
					return "THREW " + t;
				}
			});
			log("M " + action + ": " + result);
			c.waitTicks(10);
		}
		c.setScreen(() -> new dev.voidmc.mettle.client.character.CharacterScreen());
		c.waitTicks(10);
		c.takeScreenshot("audit_mettle_on_bare_k");
		c.setScreen(() -> null);
		sleep(c, 8);
		log("M still connected after 8 s: " + c.computeOnClient(mc -> mc.getConnection() != null && mc.getConnection().getConnection().isConnected())
			+ "; screen " + c.computeOnClient(mc -> String.valueOf(mc.gui.screen())));
		c.runOnClient(mc -> mc.disconnectFromWorld(Component.literal("audit done")));
		c.waitTicks(40);
		c.setScreen(TitleScreen::new);
		mark("done");
	}

	// ------------------------------------------------------------------ the shared script

	private static void common(ClientGameTestContext c, String other, TestDedicatedServerContext server) {
		// 1. A background, chosen through the real packet.
		c.waitTicks(40);
		c.runOnClient(mc -> {
			JsonObject req = new JsonObject();
			req.addProperty("background", "mettle:ranger");
			MettleActions.chooseBackground(req);
		});
		until(c, "background synced to the client", 30, () -> c.computeOnClient(mc -> CharacterStore.get(mc.player).background().isPresent()));
		c.setScreen(() -> null);
		log("background on client: " + c.computeOnClient(mc -> CharacterStore.get(mc.player).background().map(Object::toString).orElse("-")));

		// 2. XP -> levels -> drafts dealt; keep & slot the first, keep the second to the binder.
		double xp = Curves.skillXpFor(6) * 1.2;
		command(c, "mettle xp " + MINING_ID + " " + xp);
		until(c, "a Mining hand dealt", 30, () -> c.computeOnClient(mc -> !CharacterStore.get(mc.player).drafts().hand(MINING).isEmpty()));
		log("mining level " + c.computeOnClient(mc -> CharacterStore.get(mc.player).level(MINING)) + ", drafts queued "
			+ c.computeOnClient(mc -> CharacterStore.get(mc.player).drafts().queued(MINING)) + ", hand "
			+ c.computeOnClient(mc -> CharacterStore.get(mc.player).drafts().hand(MINING).size()));
		c.runOnClient(mc -> MettleActions.keepCard(MINING_ID, 0, true));
		until(c, "a kept card synced", 20, () -> c.computeOnClient(mc -> CardStore.get(mc.player).cards().size() >= 1));
		c.waitTicks(10);
		until(c, "a second hand", 20, () -> c.computeOnClient(mc -> !CharacterStore.get(mc.player).drafts().hand(MINING).isEmpty()));
		c.runOnClient(mc -> MettleActions.keepCard(MINING_ID, 0, false));
		until(c, "a second card synced", 20, () -> c.computeOnClient(mc -> CardStore.get(mc.player).cards().size() >= 2));
		c.waitTicks(10);
		List<CardData> mine = c.computeOnClient(mc -> CardStore.get(mc.player).cards());
		mine.forEach(card -> log("  card " + card.id() + " " + card.perk().identifier() + " " + card.rarity() + " place " + card.place().kind()
			+ " slotted=" + card.slotted() + " attunedAt=" + card.attunedAt()));
		long slotted = mine.stream().filter(CardData::slotted).count();
		log("DRAFT keep+slot: " + mine.size() + " cards, " + slotted + " slotted " + (slotted >= 1 ? "OK" : "PROBLEM"));
		if (server != null) {
			log("server truth for host player: " + server.computeOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().stream().filter(pl -> !pl.getName().getString().equals("Bob")).findFirst().orElseThrow();
				return CardStore.get(p).cards().size() + " cards, " + CardStore.get(p).slotted().size() + " slotted";
			}));
		}

		// 3. Config the server changed: what the remote client's overlay says.
		JsonObject cards = c.computeOnClient(mc -> MettleState.build(mc).getAsJsonObject("cards"));
		log("CONFIG overlay binder.max on client = " + cards.getAsJsonObject("binder").get("max") + " (server cards.json binder_max = 280)");
		mark("drafted");
		await(other, "drafted", 120, c);

		// 4. Trade: each offers its binder card.
		CardData binderCard = mine.stream().filter(card -> !card.slotted()).findFirst().orElse(null);
		if (binderCard == null) {
			log("TRADE skipped: no binder card");
			mark("traded");
			await(other, "traded", 60, c);
			return;
		}
		write("offerid", String.valueOf(binderCard.id()));
		mark("offer");
		await(other, "offer", 60, c);
		long theirs = Long.parseLong(read(other, "offerid"));
		// Attunement: drafted cards may be attuning; wait for server-side attune_ticks (20) to pass.
		c.waitTicks(40);
		if (ROLE.equals("A")) {
			c.runOnClient(mc -> MettleActions.tradeRequest("Bob"));
			mark("requested");
			await(other, "accepted", 60, c);
		} else {
			await(other, "requested", 60, c);
			c.waitTicks(20);
			c.runOnClient(mc -> MettleActions.tradeAccept());
			mark("accepted");
		}
		boolean open = untilSoft(c, 20, () -> c.computeOnClient(mc -> tradeView(mc) != null));
		log("TRADE view open on client: " + open + " " + c.computeOnClient(ServerAudit::tradeView));
		c.runOnClient(mc -> MettleActions.tradeOfferCard(String.valueOf(binderCard.id())));
		c.waitTicks(20);
		mark("offered");
		await(other, "offered", 60, c);
		c.waitTicks(20);
		c.runOnClient(mc -> MettleActions.tradeLock());
		mark("locked");
		await(other, "locked", 60, c);
		c.waitTicks(20);
		log("TRADE view before confirm: " + c.computeOnClient(ServerAudit::tradeView));
		if (ROLE.equals("A")) {
			c.runOnClient(mc -> MettleActions.tradeConfirm());
		}
		boolean got = untilSoft(c, 20, () -> c.computeOnClient(mc -> CardStore.get(mc.player).card(theirs).isPresent()
			&& CardStore.get(mc.player).card(binderCard.id()).isEmpty()));
		log("TRADE swap seen on this client: " + (got ? "OK" : "PROBLEM") + "; mine now " + c.computeOnClient(mc -> CardStore.get(mc.player).cards().stream()
			.map(cd -> cd.id() + ":" + cd.place().kind()).toList()) + "; feedback " + c.computeOnClient(mc -> MettleState.build(mc).get("feedback")));
		mark("traded");
		await(other, "traded", 60, c);
	}

	private static void race(ClientGameTestContext c, String other) {
		c.waitTicks(20);
		untilSoft(c, 15, () -> c.computeOnClient(mc -> CardStore.get(mc.player).cards().stream().anyMatch(cd -> cd.mythicId().isPresent())));
		c.waitTicks(60);
		List<String> myMythics = c.computeOnClient(mc -> CardStore.get(mc.player).cards().stream().flatMap(cd -> cd.mythicId().stream())
			.map(Identifier::toString).toList());
		log("RACE my Mythic cards: " + myMythics);
		JsonObject hall = c.computeOnClient(mc -> MettleState.build(mc).getAsJsonObject("cards").getAsJsonObject("mythics"));
		for (JsonElement e : hall.getAsJsonArray("all")) {
			JsonObject o = e.getAsJsonObject();
			if (o.get("claimed").getAsBoolean()) {
				log("RACE hall on this client: " + o.get("id") + " holder=" + o.get("holder"));
			}
		}
		log("RACE public Mythics seen on players: " + c.computeOnClient(mc -> {
			StringBuilder b = new StringBuilder();
			mc.level.players().forEach(p -> b.append(p.getName().getString()).append('=').append(p.getAttached(Mythics.PUBLIC)).append(' '));
			return b.toString();
		}));
		mark("raced");
		await(other, "raced", 60, c);
	}

	private static void respawn(ClientGameTestContext c, String other, TestDedicatedServerContext server) {
		sleep(c, 4);
		log("RESPAWN public Mythics seen on players after the holder died and respawned: " + c.computeOnClient(mc -> {
			StringBuilder b = new StringBuilder();
			mc.level.players().forEach(p -> b.append(p.getName().getString()).append('=').append(p.getAttached(Mythics.PUBLIC)).append(' '));
			return b.toString();
		}) + "; my cards still synced: " + c.computeOnClient(mc -> CardStore.get(mc.player).cards().size()));
		if (server != null) {
			log("RESPAWN server side: " + server.computeOnServer(sv -> {
				StringBuilder b = new StringBuilder();
				sv.getPlayerList().getPlayers().forEach(p -> b.append(p.getName().getString()).append('=').append(p.getAttached(Mythics.PUBLIC))
					.append(" cards=").append(CardStore.get(p).cards().stream().filter(cd -> cd.mythicId().isPresent()).count()).append(' '));
				return b.toString();
			}));
		}
		mark("respawn_checked");
		await(other, "respawn_checked", 60, c);
	}

	/** Bob: idle bandwidth, a 300-card binder, then one slot change and XP ticks with that binder. */
	private static void bandwidth(ClientGameTestContext c) {
		long[] s = snapshot();
		sleep(c, 5);
		report("idle 5 s", s);

		s = snapshot();
		for (int i = 0; i < 20; i++) {
			command(c, "mettle xp " + MINING_ID + " 0.5");
			c.waitTicks(1);
		}
		sleep(c, 2);
		report("20 small XP gains (2 cards)", s);

		s = snapshot();
		int want = 300;
		String[] perks = {"quarryman", "prospector", "tunneler", "deep_delver", "stonesense", "ore_sense"};
		for (int i = 0; i < want; i++) {
			command(c, "mettle cards give mettle:mining/" + perks[i % perks.length] + " epic");
			if (i % 5 == 4) {
				c.waitTicks(1);
			}
		}
		untilSoft(c, 120, () -> c.computeOnClient(mc -> CardStore.get(mc.player).cards().size() >= want));
		sleep(c, 3);
		report("300 cards given one by one", s);
		sizes(c);

		s = snapshot();
		for (int i = 0; i < 20; i++) {
			command(c, "mettle xp " + MINING_ID + " 0.5");
			c.waitTicks(1);
		}
		sleep(c, 2);
		report("20 small XP gains (300 cards)", s);

		long binderId = c.computeOnClient(mc -> CardStore.get(mc.player).cards().stream().filter(cd -> !cd.slotted() && cd.mythicId().isEmpty()
			&& cd.needs() <= CharacterStore.get(mc.player).level(MINING) && cd.perk().identifier().getPath().equals("mining/quarryman"))
			.mapToLong(CardData::id).max().orElse(-1));
		log("slotting binder card " + binderId);
		s = snapshot();
		c.runOnClient(mc -> MettleActions.slotCard(String.valueOf(binderId)));
		sleep(c, 3);
		report("one slot action (300 cards)", s);
		log("slotted now: " + c.computeOnClient(mc -> CardStore.get(mc.player).card(binderId).map(CardData::slotted).orElse(false)));
		log("CONFIG overlay binder count/max on client: " + c.computeOnClient(mc -> MettleState.build(mc).getAsJsonObject("cards").getAsJsonObject("binder").get("count"))
			+ "/" + c.computeOnClient(mc -> MettleState.build(mc).getAsJsonObject("cards").getAsJsonObject("binder").get("max")));
	}

	private static void sizes(ClientGameTestContext c) {
		c.runOnClient(mc -> {
			CardsState cards = CardStore.get(mc.player);
			CharacterState character = CharacterStore.get(mc.player);
			byte[] cb = encode(ByteBufCodecs.fromCodecTrusted(CardsState.CODEC), cards);
			byte[] hb = encode(ByteBufCodecs.fromCodecTrusted(CharacterState.CODEC), character);
			log("SIZE CardsState with " + cards.cards().size() + " cards: " + cb.length + " B raw, " + deflated(cb) + " B deflated; per card "
				+ cb.length / Math.max(1, cards.cards().size()) + " B; Fabric attachment limit " + AttachmentSync.DEFAULT_MAX_DATA_SIZE + " B -> fits up to ~"
				+ AttachmentSync.DEFAULT_MAX_DATA_SIZE / Math.max(1, cb.length / Math.max(1, cards.cards().size())) + " cards");
			log("SIZE CharacterState: " + hb.length + " B raw, " + deflated(hb) + " B deflated");
		});
	}

	// ------------------------------------------------------------------ helpers

	private static JsonElement tradeView(Minecraft mc) {
		JsonElement t = MettleState.build(mc).getAsJsonObject("cards").get("trade");
		return t == null || t.isJsonNull() ? null : t;
	}

	private static <T> byte[] encode(net.minecraft.network.codec.StreamCodec<ByteBuf, T> codec, T value) {
		ByteBuf b = Unpooled.buffer();
		codec.encode(b, value);
		byte[] out = new byte[b.readableBytes()];
		b.readBytes(out);
		return out;
	}

	private static int deflated(byte[] in) {
		Deflater d = new Deflater();
		d.setInput(in);
		d.finish();
		byte[] buf = new byte[in.length + 1024];
		int n = d.deflate(buf);
		d.end();
		return n;
	}

	private static void installCounters(ClientGameTestContext c) {
		c.runOnClient(mc -> {
			try {
				Connection conn = mc.getConnection().getConnection();
				Field f = Connection.class.getDeclaredField("channel");
				f.setAccessible(true);
				Channel ch = (Channel) f.get(conn);
				ch.pipeline().addFirst("audit_wire", new ChannelInboundHandlerAdapter() {
					@Override
					public void channelRead(ChannelHandlerContext ctx, Object msg) {
						if (msg instanceof ByteBuf b) {
							WIRE_IN.addAndGet(b.readableBytes());
						}
						ctx.fireChannelRead(msg);
					}
				});
				if (ch.pipeline().get("decompress") != null) {
					ch.pipeline().addAfter("decompress", "audit_raw", new ChannelInboundHandlerAdapter() {
						@Override
						public void channelRead(ChannelHandlerContext ctx, Object msg) {
							if (msg instanceof ByteBuf b) {
								RAW_IN.addAndGet(b.readableBytes());
							}
							ctx.fireChannelRead(msg);
						}
					});
				}
				ch.pipeline().addBefore("packet_handler", "audit_packets", new ChannelInboundHandlerAdapter() {
					@Override
					public void channelRead(ChannelHandlerContext ctx, Object msg) {
						count(msg);
						ctx.fireChannelRead(msg);
					}
				});
				log("counters on pipeline " + ch.pipeline().names());
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private static void count(Object msg) {
		if (msg instanceof ClientboundBundlePacket b) {
			b.subPackets().forEach(ServerAudit::count);
			return;
		}
		String key;
		if (msg instanceof ClientboundCustomPayloadPacket(var payload)) {
			key = payload instanceof ClientboundAttachmentSyncPayload a ? "attachment " + a.attachment().type().identifier() : "payload " + payload.type().id();
		} else if (msg instanceof Packet<?> p) {
			key = "packet " + p.type().id();
		} else {
			key = "other " + msg.getClass().getSimpleName();
		}
		PACKETS.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
	}

	private static long[] snapshot() {
		Map<String, Long> m = new java.util.TreeMap<>();
		PACKETS.forEach((k, v) -> m.put(k, v.get()));
		SNAP = m;
		return new long[] {WIRE_IN.get(), RAW_IN.get(), System.currentTimeMillis()};
	}

	private static Map<String, Long> SNAP = Map.of();

	private static void report(String what, long[] s) {
		long ms = System.currentTimeMillis() - s[2];
		long wire = WIRE_IN.get() - s[0];
		long raw = RAW_IN.get() - s[1];
		StringBuilder b = new StringBuilder();
		Map<String, Long> before = SNAP;
		new java.util.TreeMap<>(PACKETS).forEach((k, v) -> {
			long d = v.get() - before.getOrDefault(k, 0L);
			if (d > 0 && (k.contains("mettle") || k.contains("attachment") || d >= 20)) {
				b.append("\n      ").append(k).append(" x").append(d);
			}
		});
		log(String.format("BW %s: %.1f s, wire in %d B (%.1f KB/s), uncompressed %d B%s", what, ms / 1000.0, wire, wire / 1.024 / Math.max(1, ms), raw, b));
	}

	private static void command(ClientGameTestContext c, String cmd) {
		c.runOnClient(mc -> mc.player.connection.sendCommand(cmd));
	}

	private static void sleep(ClientGameTestContext c, int seconds) {
		long end = System.currentTimeMillis() + seconds * 1000L;
		while (System.currentTimeMillis() < end) {
			c.waitTicks(2);
		}
	}

	private static void until(ClientGameTestContext c, String what, int seconds, BooleanSupplier cond) {
		if (!untilSoft(c, seconds, cond)) {
			throw new AssertionError("timed out (" + seconds + " s): " + what);
		}
	}

	private static boolean untilSoft(ClientGameTestContext c, int seconds, BooleanSupplier cond) {
		long end = System.currentTimeMillis() + seconds * 1000L;
		while (!cond.getAsBoolean()) {
			if (System.currentTimeMillis() > end) {
				return false;
			}
			c.waitTicks(2);
		}
		return true;
	}

	private static void waitFile(Path p, int seconds, ClientGameTestContext c) {
		until(c, "file " + p, seconds, () -> Files.exists(p) || Files.exists(DIR.resolve(other() + ".failed")));
		if (Files.exists(DIR.resolve(other() + ".failed"))) {
			throw new AssertionError("the other role failed");
		}
	}

	private static String other() {
		return ROLE.equals("A") ? "B" : "A";
	}

	private static void await(String role, String step, int seconds, ClientGameTestContext c) {
		waitFile(DIR.resolve(role + "." + step), seconds, c);
	}

	private static void mark(String step) {
		write(step, "");
	}

	private static void write(String step, String text) {
		try {
			Files.writeString(DIR.resolve(ROLE + "." + step), text);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static String read(String role, String step) {
		try {
			return Files.readString(DIR.resolve(role + "." + step)).trim();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void ledgerReport(TestDedicatedServerContext server) {
		log("server ledger: " + server.computeOnServer(s -> dev.voidmc.mettle.character.cards.Cards.ledger(s).toString()));
	}

	static synchronized void log(String line) {
		String l = "[" + ROLE + " " + java.time.LocalTime.now().withNano(0) + "] " + line;
		dev.voidmc.mettle.Mettle.LOGGER.info("AUDIT " + l);
		try {
			Files.writeString(DIR.resolve("report-" + ROLE + ".txt"), l + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
