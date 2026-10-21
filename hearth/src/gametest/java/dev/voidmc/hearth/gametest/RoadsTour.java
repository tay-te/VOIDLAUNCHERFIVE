package dev.voidmc.hearth.gametest;

import dev.voidmc.hearth.api.RoadNetwork;
import dev.voidmc.hearth.api.TravelService.TravelKind;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.roads.Band;
import dev.voidmc.hearth.sim.roads.Journey;
import dev.voidmc.hearth.sim.roads.RoadBook;
import dev.voidmc.hearth.sim.roads.RoadWorld;
import dev.voidmc.hearth.sim.roads.Roads;
import dev.voidmc.hearth.sim.roads.Route;
import dev.voidmc.hearth.sim.roads.Way;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The roads that are travelled (docs/LIVING_WORLD_2.md §1.8, {@code -Dhearth.tour=roads}): on the dev seed, a historic
 * king's road between two towns from above, a signpost, a covered wagon with its guards, pilgrims, a camp by the road
 * at night, a bandit ambush at dusk (the demand on the conversation page), a road crew at the head of a road being
 * built — with the network's figures and the roads' and traffic's tick timings logged as {@code [hearth tour] roads:}.
 * Screenshots are {@code hearth_roads_*}.
 */
public class RoadsTour implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Tours.wanted("roads")) {
			return;
		}
		context.runOnClient(mc -> mc.options.renderDistance().set(10));
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("20261003");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			new Run(context, world).run();
		}
	}

	private static final class Run {
		final ClientGameTestContext context;
		final TestSingleplayerContext world;

		Run(ClientGameTestContext context, TestSingleplayerContext world) {
			this.context = context;
			this.world = world;
		}

		void run() {
			this.world.getServer().runCommand("gamerule spawn_monsters false");
			this.world.getServer().runCommand("gamerule advance_weather false");
			this.world.getServer().runCommand("weather clear");
			this.world.getServer().runCommand("time set 2000");
			settle(8_000);
			Way road = findRoad();
			if (road == null) {
				log("no historic road found near spawn");
				return;
			}
			Route r = road.route();
			log(String.format(Locale.ROOT, "road %s: %s - %s, %s, %.0f blocks, %d plots", Long.toHexString(road.id()), road.nameA(), road.nameB(),
				road.kind() == RoadNetwork.RoadClass.KINGS_ROAD ? "king's road" : "track", road.length(), road.segments().size()));
			// from above, a third of the way along
			int mid = r.size() / 3;
			BlockPos m = new BlockPos(r.xs()[mid], r.ys()[mid], r.zs()[mid]);
			int dir = mid + 12 < r.size() ? mid + 12 : mid - 12;
			float yaw = yaw(r.xs()[mid], r.zs()[mid], r.xs()[dir], r.zs()[dir]);
			teleport(m.getX() - Mth.sin(-yaw * Mth.DEG_TO_RAD) * 40, m.getY() + 60, m.getZ() - Mth.cos(yaw * Mth.DEG_TO_RAD) * 40, yaw, 50);
			settle(15_000);
			checkWritten(road, mid);
			shoot("road_above", m.getX() - Mth.sin(-yaw * Mth.DEG_TO_RAD) * 40, m.getY() + 60, m.getZ() - Mth.cos(yaw * Mth.DEG_TO_RAD) * 40, yaw, 50);
			shoot("road_low", m.getX() - Mth.sin(-yaw * Mth.DEG_TO_RAD) * 10, m.getY() + 6, m.getZ() - Mth.cos(yaw * Mth.DEG_TO_RAD) * 10, yaw, 15);
			// the signpost out past its first town's streets
			for (var seg : road.segments()) {
				String sign = seg.spec().params().getStringOr("sign_start", "");
				if (!sign.isEmpty()) {
					int[] pts = seg.spec().params().getIntArray("points").orElse(new int[0]);
					BlockPos sp = new BlockPos(pts[0], pts[1], pts[2]);
					log("signpost: \"" + sign.replace('\n', ' ') + "\" near " + sp.toShortString());
					aim("signpost", sp, 7, 3);
					break;
				}
			}
			// a bridge or a ford on it, if it crosses water
			for (var seg : road.segments()) {
				if (seg.bridge()) {
					BlockPos b = new BlockPos((seg.minX() + seg.maxX()) / 2, 0, (seg.minZ() + seg.maxZ()) / 2);
					log("bridge: " + seg.spec().params().getStringOr("road_kind", "") + " span of " + (seg.to() - seg.from()) + " columns at " + b.toShortString());
					aim("bridge", b, 14, 6);
					break;
				}
			}
			int fords = 0;
			for (byte t : r.types()) {
				fords += t == Route.FORD ? 1 : 0;
			}
			log("fords: " + fords + " columns");
			adopt(road);
			travellers(road);
			ambush(road);
			crew(road);
			stats();
		}

		/** A historic road built in history near spawn (the regions round it are worked out on the server's roads). */
		@Nullable Way findRoad() {
			return this.world.getServer().computeOnServer(server -> {
				SimWorld w = SimServer.world(server.overworld());
				RoadWorld net = w == null ? null : w.roadWorld();
				if (net == null) {
					return null;
				}
				BlockPos spawn = server.overworld().getRespawnData().pos();
				long t0 = System.nanoTime();
				Way best = null;
				double bestD = Double.MAX_VALUE;
				int rx0 = Atlas.region(spawn.getX());
				int rz0 = Atlas.region(spawn.getZ());
				int ways = 0;
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						RoadWorld.Region region = net.region(rx0 + dx, rz0 + dz);
						for (Way way : region.ways()) {
							ways++;
							if (!way.edge().historic() || way.route() == null) {
								continue;
							}
							// a king's road first, then the nearest
							double d = Math.hypot(way.ax() - spawn.getX(), way.az() - spawn.getZ())
								+ (way.kind() == RoadNetwork.RoadClass.KINGS_ROAD ? 0 : 3000);
							if (d < bestD) {
								bestD = d;
								best = way;
							}
						}
					}
				}
				log(String.format(Locale.ROOT, "network: 25 regions round spawn worked out in %.0f ms: %d ways; %s", (System.nanoTime() - t0) / 1e6, ways,
					net.describe()));
				return best;
			});
		}

		/** Checks the road is in the world where world generation wrote it (or the applier did, live). */
		void checkWritten(Way road, int at) {
			String report = this.world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				Route r = road.route();
				int road0 = 0;
				int other = 0;
				for (int i = Math.max(0, at - 40); i < Math.min(r.size(), at + 40); i++) {
					BlockPos p = new BlockPos(r.xs()[i], r.ys()[i] - 1, r.zs()[i]);
					if (!level.isLoaded(p)) {
						continue;
					}
					var st = level.getBlockState(p);
					String n = st.getBlock().getDescriptionId();
					if (n.contains("path") || n.contains("gravel") || n.contains("dirt") || n.contains("cobble") || n.contains("stone") || n.contains("planks")
						|| n.contains("brick") || n.contains("slab") || n.contains("stairs") || n.contains("grass")) {
						road0++;
					} else {
						other++;
					}
				}
				return road0 + " road-like surfaces, " + other + " others under 80 centreline columns";
			});
			log("written: " + report);
		}

		/** The road's two towns made live (their chunks loaded), so the traffic has somewhere to come from. */
		void adopt(Way road) {
			for (int end = 0; end < 2; end++) {
				int x = end == 0 ? road.ax() : road.bx();
				int z = end == 0 ? road.az() : road.bz();
				teleport(x, surface(new BlockPos(x, 0, z)) + 30, z, 0, 40);
				settle(8_000);
			}
			String live = this.world.getServer().computeOnServer(server -> {
				SimWorld w = SimServer.world(server.overworld());
				return w == null ? "?" : w.settlement(road.a()) + " / " + w.settlement(road.b());
			});
			log("adopted: " + live);
		}

		/** Journeys of several kinds sent from the road's first town, met on the road. */
		void travellers(Way road) {
			this.world.getServer().runCommand("time set 3000");
			for (TravelKind kind : new TravelKind[]{TravelKind.CARAVAN, TravelKind.PILGRIMS, TravelKind.PEDLAR, TravelKind.MESSENGER, TravelKind.CARTER}) {
				Object[] sent = this.world.getServer().computeOnServer(server -> {
					Roads.State st = Roads.state(SimServer.world(server.overworld()));
					SimSettlement a = st == null ? null : st.world().settlement(road.a());
					SimSettlement b = st == null ? null : st.world().settlement(road.b());
					if (a == null || b == null) {
						return null;
					}
					// it set out a while ago: it is out on the road now, past the town's fields
					long depart = st.world().clock() - (kind == TravelKind.MESSENGER ? 1500 : 4500);
					List<java.util.UUID> people = kind == TravelKind.PEDLAR ? List.of() : dev.voidmc.hearth.sim.roads.Traffic.pick(a, 3);
					CompoundTag d = new CompoundTag();
					if (kind == TravelKind.CARAVAN) {
						d.putIntArray("cargo", new int[]{20, 0, 60, 0, 0, 0, 0, 0, 0, 0, 30});
					}
					long id = st.traffic().send(kind, a, b, people, depart, 0, kind == TravelKind.PILGRIMS || kind == TravelKind.CARTER, 0, 1200, d);
					Journey j = st.traffic().journey(id);
					return j == null ? null : new Object[]{id, st.traffic().position(j, st.world().clock())};
				});
				if (sent == null) {
					log(kind + ": not sent");
					continue;
				}
				BlockPos at = (BlockPos) sent[1];
				teleport(at.getX() + 10, surface(at) + 6, at.getZ() + 10, 135, 25);
				settle(9_000);
				String seen = this.world.getServer().computeOnServer(server -> String.format(Locale.ROOT, "%d parties, %d travellers on the road",
					dev.voidmc.hearth.npc.travel.Parties.parties(), dev.voidmc.hearth.npc.travel.Parties.travellers()));
				log(kind + " #" + sent[0] + " near " + at.toShortString() + ": " + seen);
				BlockPos now = this.world.getServer().computeOnServer(server -> {
					Roads.State st = Roads.state(SimServer.world(server.overworld()));
					Journey j = st.traffic().journey((long) sent[0]);
					return j == null ? at : st.traffic().position(j, st.world().clock());
				});
				aim(kind.name().toLowerCase(Locale.ROOT), now, 9, 4);
			}
			// the night: they camp by the road
			this.world.getServer().runCommand("time set 16000");
			settle(8_000);
			BlockPos camp = this.world.getServer().computeOnServer(server -> {
				Roads.State st = Roads.state(SimServer.world(server.overworld()));
				for (Journey j : st.traffic().journeys()) {
					if (j.kind() == TravelKind.CARAVAN || j.kind() == TravelKind.PILGRIMS) {
						return st.traffic().position(j, st.world().clock());
					}
				}
				return null;
			});
			if (camp != null) {
				teleport(camp.getX() + 8, surface(camp) + 5, camp.getZ() + 8, 135, 30);
				settle(10_000);
				aim("camp_night", camp, 7, 5);
				String camps = this.world.getServer().computeOnServer(server -> {
					var l = SimServer.world(server.overworld()).data(dev.voidmc.hearth.npc.travel.Parties.campKey());
					return l.size() + " camp blocks standing";
				});
				log("camp: " + camps);
			}
		}

		/** A band on the road, met at dusk: the leader's demand on the conversation page. */
		void ambush(Way road) {
			this.world.getServer().runCommand("time set 13000");
			BlockPos spot = this.world.getServer().computeOnServer(server -> {
				Roads.State st = Roads.state(SimServer.world(server.overworld()));
				Band b = st.traffic().addBand(road, net.minecraft.util.RandomSource.create(9));
				var r = road.route();
				double acc = 0;
				for (int i = 1; i < r.size(); i++) {
					acc += Math.hypot(r.xs()[i] - r.xs()[i - 1], r.zs()[i] - r.zs()[i - 1]);
					if (acc >= b.along()) {
						return new BlockPos(r.xs()[i], r.ys()[i], r.zs()[i]);
					}
				}
				return null;
			});
			if (spot == null) {
				log("ambush: no spot");
				return;
			}
			// on foot, as a traveller would be (bandits do not step out at a creative flier)
			teleport(spot.getX() + 30, surface(spot) + 1, spot.getZ() + 30, 135, 5);
			settle(4_000);
			this.world.getServer().runCommand("gamemode survival @a");
			teleport(spot.getX() + 20, surface(spot.offset(20, 0, 20)) + 1, spot.getZ() + 20, 135, 5);
			settle(15_000);
			this.context.takeScreenshot("hearth_roads_ambush");
			String state = this.world.getServer().computeOnServer(server -> String.format(Locale.ROOT, "%d parties, %d travellers",
				dev.voidmc.hearth.npc.travel.Parties.parties(), dev.voidmc.hearth.npc.travel.Parties.travellers()));
			log("ambush at " + spot.toShortString() + ": " + state);
			this.world.getServer().runCommand("gamemode creative @a");
			this.context.setScreen(() -> null);
		}

		/** A road being built: a settlement founded off the road's first town, its track begun, a crew at its head. */
		void crew(Way road) {
			this.world.getServer().runCommand("time set 4000");
			Object[] head = this.world.getServer().computeOnServer(server -> {
				Roads.State st = Roads.state(SimServer.world(server.overworld()));
				SimSettlement a = st.world().settlement(road.a());
				if (a == null) {
					return null;
				}
				// a hamlet 400 blocks off, away from the road
				// on dry land: round from the side away from the road until the ground is out of the water
				var probe = st.world().probe();
				BlockPos at = null;
				for (int k = 0; k < 16 && at == null; k++) {
					double ang = Math.atan2(road.bz() - road.az(), road.bx() - road.ax()) + Math.PI * (0.6 + k * 0.125);
					BlockPos c = BlockPos.containing(a.center().getX() + Math.cos(ang) * 420, 0, a.center().getZ() + Math.sin(ang) * 420);
					if (!probe.isWater(c.getX(), c.getZ()) && probe.groundY(c.getX(), c.getZ()) >= 63) {
						at = c;
					}
				}
				if (at == null) {
					return null;
				}
				SimSettlement h = st.world().found(at.atY(st.world().probe().groundY(at.getX(), at.getZ())), null, "Roadsend");
				st.live().dayPassed(h, st.world().day());
				return new Object[]{h.id()};
			});
			if (head == null) {
				log("crew: no hamlet");
				return;
			}
			// its road is found off the server thread
			settle(6_000);
			BlockPos at = this.world.getServer().computeOnServer(server -> {
				Roads.State st = Roads.state(SimServer.world(server.overworld()));
				for (RoadBook.LiveRoad r : st.live().book().live()) {
					if ((r.a() == (long) head[0] || r.b() == (long) head[0]) && r.way() != null) {
						st.live().advance(r, true, 300, st.world().settlement(r.a()));
						var seg = r.way().segments().get(Math.max(0, r.head(true)));
						return new BlockPos((seg.minX() + seg.maxX()) / 2, 0, (seg.minZ() + seg.maxZ()) / 2);
					}
				}
				return null;
			});
			if (at == null) {
				log("crew: the hamlet's road was not routed: " + this.world.getServer().computeOnServer(server ->
					Roads.state(SimServer.world(server.overworld())).live().describe()));
				return;
			}
			teleport(at.getX() + 12, surface(at) + 8, at.getZ() + 12, 135, 30);
			settle(12_000);
			aim("crew", at, 10, 5);
			log("crew: " + this.world.getServer().computeOnServer(server -> Roads.state(SimServer.world(server.overworld())).live().describe()));
			detour((long) head[0]);
		}

		/**
		 * Risk 1: a player's house across the hamlet's road ahead of its crew. When the road is built that far, the plot
		 * under the house is refused and a detour goes round it.
		 */
		void detour(long hamlet) {
			BlockPos house = this.world.getServer().computeOnServer(server -> {
				Roads.State st = Roads.state(SimServer.world(server.overworld()));
				ServerLevel level = server.overworld();
				for (RoadBook.LiveRoad r : st.live().book().live()) {
					if ((r.a() != hamlet && r.b() != hamlet) || r.way() == null || r.spur()) {
						continue;
					}
					int head = r.head(true);
					int k = head + 2;
					if (head < 0 || k >= r.way().segments().size() - 1) {
						return null;
					}
					var seg = r.way().segments().get(k);
					Route route = r.way().route();
					int mid = (seg.from() + seg.to()) / 2;
					int x = route.xs()[mid];
					int z = route.zs()[mid];
					level.getChunk(x >> 4, z >> 4);
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					// a player's planked store, seven by seven, right across the way (solid, so every column of the road meets it)
					for (int dx = -3; dx <= 3; dx++) {
						for (int dz = -3; dz <= 3; dz++) {
							for (int dy = 0; dy <= 4; dy++) {
								level.setBlockAndUpdate(new BlockPos(x + dx, y + dy, z + dz), net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS.defaultBlockState());
							}
						}
					}
					// the road is built on past it
					st.live().advance(r, true, 6_000, st.world().settlement(r.a()));
					return new BlockPos(x, y, z);
				}
				return null;
			});
			if (house == null) {
				log("detour: no plot ahead of the crew");
				return;
			}
			teleport(house.getX() + 16, house.getY() + 24, house.getZ() + 16, 135, 45);
			settle(15_000);
			log("detour: " + this.world.getServer().computeOnServer(server -> {
				Roads.State st = Roads.state(SimServer.world(server.overworld()));
				int detours = 0;
				for (RoadBook.LiveRoad r : st.live().book().live()) {
					detours += r.detour() ? 1 : 0;
				}
				return detours + " detours in the book; " + st.applier().describe();
			}));
			// from above the house, looking down on the road going round it
			shoot("detour", house.getX() + 0.5, house.getY() + 36, house.getZ() + 22, 180, 58);
		}

		void stats() {
			String s = this.world.getServer().computeOnServer(server -> {
				Roads.State st = Roads.state(SimServer.world(server.overworld()));
				StringBuilder b = new StringBuilder();
				b.append("network: ").append(st.net().describe()).append('\n');
				b.append("building: ").append(st.live().describe()).append('\n');
				b.append(st.applier().describe()).append('\n');
				b.append("traffic: ").append(st.traffic().describe()).append('\n');
				for (String line : LivingWorldStats.lines()) {
					b.append(line).append('\n');
				}
				b.append(String.format(Locale.ROOT, "server tick %.2f ms avg", server.getAverageTickTimeNanos() / 1e6));
				return b.toString();
			});
			for (String line : s.split("\n")) {
				log(line);
			}
		}

		static float yaw(int fx, int fz, int tx, int tz) {
			return (float) (Mth.atan2(tz - fz, tx - fx) * Mth.RAD_TO_DEG) - 90.0F;
		}

		int surface(BlockPos at) {
			return this.world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				level.getChunk(at.getX() >> 4, at.getZ() >> 4);
				return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
			});
		}

		void teleport(double x, double y, double z, float yaw, float pitch) {
			this.world.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
				p.getAbilities().flying = p.isCreative();
				p.onUpdateAbilities();
				p.teleportTo(server.overworld(), x, y, z, Set.of(), Mth.wrapDegrees(yaw), pitch, true);
			});
		}

		/** A shot of {@code target} from {@code back} blocks off to its south-east and {@code up} above, looking at it. */
		void aim(String name, BlockPos target, int back, int up) {
			int ty = surface(target);
			double x = target.getX() + back * 0.7;
			double z = target.getZ() + back * 0.7;
			double y = surface(BlockPos.containing(x, 0, z)) + up;
			double dx = target.getX() + 0.5 - x;
			double dz = target.getZ() + 0.5 - z;
			double dy = ty + 1 - y;
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			float pitch = (float) (-Mth.atan2(dy, Math.hypot(dx, dz)) * Mth.RAD_TO_DEG);
			shoot(name, x, y, z, yaw, pitch);
		}

		void shoot(String name, double x, double y, double z, float yaw, float pitch) {
			teleport(x, y, z, yaw, pitch);
			settle(3_000);
			this.context.takeScreenshot("hearth_roads_" + name);
			log("shot " + name);
		}

		void settle(long ms) {
			long until = System.currentTimeMillis() + ms;
			while (System.currentTimeMillis() < until) {
				this.context.waitTick();
			}
		}

		static void log(String line) {
			System.out.println("[hearth tour] roads: " + line);
		}
	}
}
