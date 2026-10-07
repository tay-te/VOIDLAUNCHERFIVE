package dev.voidmc.atlas.gametest;

import dev.voidmc.atlas.api.AtlasApi;
import dev.voidmc.atlas.api.AtlasSocialTransport;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerGlyph;
import dev.voidmc.atlas.api.Privacy;
import dev.voidmc.atlas.api.Source;
import dev.voidmc.atlas.api.Tier;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.AtlasSettings;
import dev.voidmc.atlas.client.FarSight;
import dev.voidmc.atlas.client.Social;
import dev.voidmc.atlas.client.data.DimensionData;
import dev.voidmc.atlas.client.data.LayerData;
import dev.voidmc.atlas.client.data.MapSession;
import dev.voidmc.atlas.client.ui.AtlasScreen;
import dev.voidmc.atlas.client.ui.MapPanel;
import dev.voidmc.atlas.client.ui.PresetScreen;
import dev.voidmc.atlas.client.ui.SettingsScreen;
import dev.voidmc.atlas.core.Coords;
import dev.voidmc.atlas.core.RegionRaw;
import dev.voidmc.atlas.core.social.FriendPositions;
import dev.voidmc.atlas.core.social.LoopbackTransport;
import dev.voidmc.atlas.core.social.Roster;
import dev.voidmc.atlas.core.social.SharedTiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * The tour's shots of section 8 (Figma 375:36237): the first-run presets, the settings' Map tab with Light on and the
 * quiet note under the minimap, the Journal tab, far sight on the map (made-up far tiles, as VOID LOD would hand them
 * over) with its legend and hover, friends (a loopback transport with three friends) on the map, a friend's card with
 * the tracking hairline, a party member's land, a friend at the minimap's edge, and a tracked quest step with its
 * beam.
 */
final class Section8Shots {
	private static final String SERVER = "tour-server";
	private static final String ME = "me";
	private static final String KESTREL = "kestrel";
	private static final String MARA = "mara";
	private static final String JUN = "jun";

	private final AtlasGameTest tour;
	private final ClientGameTestContext context;
	private final TestSingleplayerContext world;
	private final BlockPos spawn;
	private LoopbackTransport.Hub hub;
	private LoopbackTransport kestrelEnd;
	private LoopbackTransport maraEnd;
	private FriendPositions kestrelPos;
	private FriendPositions maraPos;
	private double[] kestrelAt;
	private double[] maraAt;
	private int beat;

	Section8Shots(AtlasGameTest tour, ClientGameTestContext context, TestSingleplayerContext world, BlockPos spawn) {
		this.tour = tour;
		this.context = context;
		this.world = world;
		this.spawn = spawn;
	}

	private void client(Consumer<Minecraft> action) {
		this.context.runOnClient(action::accept);
	}

	private void shot(String name) {
		this.tour.shootPublic(this.context, name);
	}

	/** The transport the tour hands Atlas: one end of a loopback hub, with a friends list. */
	private static final class TourTransport implements AtlasSocialTransport {
		final LoopbackTransport end;
		final List<Friend> roster;

		TourTransport(LoopbackTransport end, List<Friend> roster) {
			this.end = end;
			this.roster = roster;
		}

		@Override
		public void publish(String topic, byte[] payload) {
			this.end.publish(topic, payload);
		}

		@Override
		public AutoCloseable subscribe(String topic, BiConsumer<String, byte[]> handler) {
			return this.end.subscribe(topic, handler);
		}

		@Override
		public void presence(Presence state) {
			this.end.presence(state);
		}

		@Override
		public boolean available() {
			return true;
		}

		@Override
		public String localPlayer() {
			return ME;
		}

		@Override
		public java.util.Collection<String> friends() {
			return this.roster.stream().map(Friend::id).toList();
		}

		@Override
		public java.util.Collection<String> party() {
			return this.roster.stream().filter(Friend::inParty).map(Friend::id).toList();
		}

		@Override
		public List<Friend> roster() {
			return this.roster;
		}
	}

	void run() {
		int sx = this.spawn.getX();
		int sz = this.spawn.getZ();
		// 1. the first-run presets, on the first M
		this.client(mc -> {
			mc.gui.setScreen(null);
			AtlasClient.get().overlays().clearIslands();
			AtlasClient.get().settings().preset = null;
			mc.gui.setScreen(new PresetScreen(AtlasClient.get()));
		});
		this.context.waitTicks(10);
		this.shot("s8_presets");
		this.client(mc -> {
			AtlasClient.get().settings().apply(AtlasSettings.Preset.EXPLORER);
			mc.gui.setScreen(null);
		});

		// 2. Light mode: the quiet note under the minimap's readout, and the settings' Map tab
		System.setProperty("atlas.test.assumeLod", "true");
		this.client(mc -> AtlasClient.get().settings().performance = AtlasSettings.Performance.LIGHT);
		this.context.waitTicks(10);
		this.shot("s8_minimap_light");
		this.client(mc -> mc.gui.setScreen(new SettingsScreen(AtlasClient.get(), null).showTab(1)));
		this.context.waitTicks(10);
		this.shot("s8_settings_map_light");

		// 3. friends: a loopback hub with Kestrel_ (party, here), Mara (here) and Jun (elsewhere)
		this.client(mc -> {
			mc.gui.setScreen(null);
			// Quality for the rest: far sight and party tiles wait while Light is on, and a busy tour machine may go Light
			AtlasClient.get().settings().performance = AtlasSettings.Performance.QUALITY;
			Social.testServer = SERVER;
			this.hub = new LoopbackTransport.Hub();
			LoopbackTransport mine = this.hub.join(ME);
			this.kestrelEnd = this.hub.join(KESTREL);
			this.maraEnd = this.hub.join(MARA);
			List<AtlasSocialTransport.Friend> roster = List.of(
				new AtlasSocialTransport.Friend(KESTREL, "Kestrel_", UUID.nameUUIDFromBytes("kestrel".getBytes()), true, true, SERVER, "This world"),
				new AtlasSocialTransport.Friend(MARA, "Mara", UUID.nameUUIDFromBytes("mara".getBytes()), true, true, SERVER, "This world"),
				new AtlasSocialTransport.Friend(JUN, "Jun", UUID.nameUUIDFromBytes("jun".getBytes()), true, false, "other", "Hypixel lobby"));
			AtlasApi.get().useSocialTransport(new TourTransport(mine, roster));
			Roster theirs = new Roster();
			theirs.setParty(List.of(ME, KESTREL, MARA));
			this.kestrelPos = new FriendPositions(KESTREL, theirs);
			this.kestrelPos.local(SERVER, Privacy.PARTY);
			this.maraPos = new FriendPositions(MARA, theirs);
			this.maraPos.local(SERVER, Privacy.PARTY);
		});
		this.kestrelAt = new double[]{sx + 150, 80, sz - 110};
		this.maraAt = new double[]{sx + 470, 95, sz + 260};
		this.friendsTick(60);

		// 4. a party member's land: Kestrel_'s explored land, sent as tiles, east of what the player walked
		int[] east = this.client2(mc -> walkedEdge());
		int landX = east[0] + 260;
		int landZ = sz;
		this.client(mc -> this.sendLand(landX, landZ, 300));
		this.kestrelAt = new double[]{landX + 40, 90, landZ - 60};
		this.friendsTick(60);

		this.client(mc -> {
			AtlasClient.get().openMap();
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.rightTab = 0;
			screen.page.map.setKind(MapPanel.Kind.SURFACE);
			screen.page.map.centreOn(sx + 200, sz + 60);
			screen.page.map.setZoom(1);
			screen.page.map.selected = null;
		});
		this.friendsTick(30);
		this.shot("s8_friends_map");
		this.client(mc -> {
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.map.selected = Social.FRIEND_PREFIX + KESTREL;
			AtlasClient.get().settings().tracked = Social.FRIEND_PREFIX + KESTREL;
		});
		this.friendsTick(20);
		this.shot("s8_friend_card");
		this.client(mc -> {
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.map.selected = null;
			screen.page.rightTab = 1;
			screen.page.map.centreOn(landX - 120, landZ);
			screen.page.map.setZoom(0.5);
		});
		this.friendsTick(30);
		this.shot("s8_party_map");

		// 5. far sight: made-up far tiles round the walked land, as VOID LOD hands them over
		this.client(mc -> this.offerFar(walkedBox()));
		this.friendsTick(80);
		this.client(mc -> {
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.rightTab = 1;
			int[] box = walkedBox();
			screen.page.map.centreOn((box[0] + box[2]) / 2.0, box[3]);
			screen.page.map.setZoom(0.5);
		});
		this.friendsTick(40);
		this.shot("s8_far_map");
		this.hoverFar();
		this.friendsTick(10);
		this.shot("s8_far_hover");

		// 6. the Journal tab
		this.client(mc -> {
			AtlasClient.get().journalProgressNow();
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.rightTab = 2;
			screen.page.map.centreOn(sx, sz);
			screen.page.map.setZoom(1);
		});
		this.friendsTick(20);
		this.shot("s8_journal");

		// 7. a Hearth quest step, tracked: its target and chip, the tracked row, and its beam in the world
		int qx = sx + 90;
		int qz = sz + 70;
		int qy = this.world.getServer().computeOnServer(s -> {
			s.overworld().getChunk(qx >> 4, qz >> 4);
			s.overworld().getChunk(sx >> 4, sz >> 4);
			return s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, qx, qz);
		});
		int gy = this.world.getServer().computeOnServer(s -> s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, sx, sz));
		this.client(mc -> {
			AtlasApi.get().markers().registerProvider("hearth:quests", 0, (q, out) -> {
				if (q.dimension().equals("minecraft:overworld")) {
					out.accept(new Marker("hearth:quest:bridge", "hearth:quests", "Stone for the bridge", "96 / 128", "QUARRY ON GREYSTEP FELLS", qx + 0.5, qy,
						qz + 0.5, "minecraft:overworld", MarkerGlyph.QUEST, Marker.Style.QUEST, 0, null, Source.plugin("hearth:quests"), Tier.MAJOR));
				}
			});
			AtlasClient.get().settings().tracked = null;
			AtlasApi.get().track("hearth:quest:bridge");
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			screen.page.rightTab = 0;
			screen.page.map.centreOn(qx - 60, qz);
			screen.page.map.setZoom(1);
		});
		this.friendsTick(20);
		this.shot("s8_quest_map");
		this.client(mc -> mc.gui.setScreen(null));
		this.world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d -40 10", sx, Math.max(gy, qy) + 14, sz));
		this.friendsTick(40);
		this.shot("s8_quest_beam");

		// 8. a friend beyond the minimap's edge, and the tracked friend on the compass
		this.client(mc -> {
			AtlasClient.get().settings().tracked = Social.FRIEND_PREFIX + MARA;
			AtlasClient.get().settings().compass = true;
		});
		this.maraAt = new double[]{sx + 420, 95, sz - 380};
		this.friendsTick(30);
		this.shot("s8_minimap_friend_edge");
		this.client(mc -> {
			AtlasClient.get().settings().tracked = null;
			AtlasApi.get().markers().unregisterProvider("hearth:quests");
		});
	}

	private <T> T client2(java.util.function.Function<Minecraft, T> f) {
		return this.context.computeOnClient(f::apply);
	}

	/** Friends say where they are (twice a second) and the hub delivers, for {@code ticks}. */
	private void friendsTick(int ticks) {
		for (int t = 0; t < ticks; t += 10) {
			double wiggle = (this.beat++ & 1) * 1.5;
			this.client(mc -> {
				long now = System.currentTimeMillis();
				byte[] k = this.kestrelPos.outgoing("Kestrel_", "minecraft:overworld", this.kestrelAt[0] + wiggle, this.kestrelAt[1],
					this.kestrelAt[2], 0, now);
				if (k != null) {
					this.kestrelEnd.publish(AtlasSocialTransport.TOPIC_POSITION, k);
				}
				byte[] m = this.maraPos.outgoing("Mara", "minecraft:overworld", this.maraAt[0] + wiggle, this.maraAt[1], this.maraAt[2], 0, now);
				if (m != null) {
					this.maraEnd.publish(AtlasSocialTransport.TOPIC_POSITION, m);
				}
				this.hub.deliverAll(4);
			});
			this.context.waitTicks(10);
		}
	}

	/** The walked map's box in blocks (min x, min z, max x, max z), from the regions in memory. */
	static int[] walkedBox() {
		MapSession s = AtlasClient.get().session();
		LayerData walked = s.dimension("minecraft:overworld", false).layer(DimensionData.SURFACE);
		int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (int rz = -8; rz < 8; rz++) {
			for (int rx = -8; rx < 8; rx++) {
				RegionRaw r = walked.rawIfLoaded(rx, rz);
				if (r == null) {
					continue;
				}
				for (int lz = 0; lz < RegionRaw.SIZE; lz += 8) {
					for (int lx = 0; lx < RegionRaw.SIZE; lx += 8) {
						if (r.known(RegionRaw.index(lx, lz))) {
							int x = (rx << Coords.REGION_SHIFT) + lx;
							int z = (rz << Coords.REGION_SHIFT) + lz;
							box[0] = Math.min(box[0], x);
							box[1] = Math.min(box[1], z);
							box[2] = Math.max(box[2], x);
							box[3] = Math.max(box[3], z);
						}
					}
				}
			}
		}
		return box;
	}

	/** The walked map's east edge at the spawn's latitude (x, z). */
	private int[] walkedEdge() {
		int[] box = walkedBox();
		return new int[]{box[2], this.spawn.getZ()};
	}

	/** Made-up far tiles (cells of 8 blocks) on a ring 1 km round the walked land, the way VOID LOD sends them. */
	private void offerFar(int[] box) {
		int x0 = box[0] - 1024;
		int z0 = box[1] - 512;
		int x1 = box[2] + 1024;
		int z1 = box[3] + 1024;
		int n = 64;
		int cell = 8;
		int span = n * cell;
		long revision = 1;
		for (int tz = Math.floorDiv(z0, span) * span; tz < z1; tz += span) {
			for (int tx = Math.floorDiv(x0, span) * span; tx < x1; tx += span) {
				short[] heights = new short[n * n];
				int[] colours = new int[n * n];
				String[] blocks = new String[n * n];
				String[] biomes = new String[n * n];
				boolean[] seen = new boolean[n * n];
				for (int k = 0; k < n * n; k++) {
					int x = tx + (k % n) * cell + cell / 2;
					int z = tz + (k / n) * cell + cell / 2;
					int y = (int) Math.round(Synthetic.height(x, z));
					heights[k] = (short) Math.max(62, y);
					boolean water = y < 62;
					blocks[k] = water ? "minecraft:water" : y > 150 ? "minecraft:snow_block" : y > 112 ? "minecraft:stone" : (x * 31 + z * 17 & 7) < 3
						? "minecraft:oak_leaves" : "minecraft:grass_block";
					biomes[k] = water ? "minecraft:ocean" : y > 112 ? "minecraft:stony_peaks" : "minecraft:plains";
					colours[k] = water ? 0x3F76E4 : 0x5A8A3C;
					seen[k] = true;
				}
				AtlasClient.get().farSight.offer(new FarSight.Tile("minecraft:overworld", revision++, tx, tz, cell, n, heights, colours, blocks, biomes, seen));
			}
		}
	}

	/** Kestrel_'s explored land: a blob of made-up land {@code radius} round a point, sent as party tiles. */
	private void sendLand(int cx, int cz, int radius) {
		MapSession s = AtlasClient.get().session();
		SharedTiles.Outbox out = new SharedTiles.Outbox(KESTREL, s.blockNames, s.biomeNames).level(Social.SHARE_LEVEL).bytesPerSecond(1 << 22);
		out.local(SERVER, Privacy.PARTY);
		int grass = s.blockNames.id("minecraft:grass_block");
		int leaves = s.blockNames.id("minecraft:oak_leaves");
		int stone = s.blockNames.id("minecraft:stone");
		int plains = s.biomeNames.id("minecraft:plains");
		int forest = s.biomeNames.id("minecraft:forest");
		List<RegionRaw> raws = new ArrayList<>();
		for (int rz = Coords.region(cz - radius); rz <= Coords.region(cz + radius); rz++) {
			for (int rx = Coords.region(cx - radius); rx <= Coords.region(cx + radius); rx++) {
				RegionRaw raw = new RegionRaw(rx, rz);
				for (int lz = 0; lz < RegionRaw.SIZE; lz++) {
					for (int lx = 0; lx < RegionRaw.SIZE; lx++) {
						int x = (rx << Coords.REGION_SHIFT) + lx;
						int z = (rz << Coords.REGION_SHIFT) + lz;
						double edge = radius * (0.8 + 0.2 * Math.sin(Math.atan2(z - cz, x - cx) * 5));
						if (Math.hypot(x - cx, z - cz) > edge) {
							continue;
						}
						int y = Math.max(63, (int) Math.round(Synthetic.height(x, z)));
						boolean wood = (x * 7 + z * 13 & 15) < 6;
						raw.set(lx, lz, y, y > 112 ? stone : wood ? leaves : grass, 0, wood ? forest : plains, RegionRaw.KNOWN);
					}
				}
				raws.add(raw);
				out.changedRegion("minecraft:overworld", raw);
			}
		}
		long t = System.currentTimeMillis();
		for (int i = 0; i < 64 && out.pendingPieces() > 0; i++) {
			out.pump(this.kestrelEnd, t + i * 10_000L);
		}
		this.hub.deliverAll(64);
	}

	/** The cursor over far land, east of the walked map's edge. */
	private void hoverFar() {
		double[] at = this.client2(mc -> {
			AtlasScreen screen = (AtlasScreen) mc.gui.screen();
			var f = screen.page.map.frame();
			int[] box = walkedBox();
			double bx = box[2] + 300;
			double bz = (box[1] + box[3]) / 2.0;
			float unit = screen.unit();
			return new double[]{f.screenX(bx, bz) * unit, f.screenY(bx, bz) * unit};
		});
		this.context.getInput().setCursorPos(at[0], at[1]);
	}
}
