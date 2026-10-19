package dev.voidmc.hearth.gametest.npc;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.client.npc.ConversationScreen;
import dev.voidmc.hearth.gametest.Tours;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.terrain.CultureVote;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;

/**
 * The folk's day and night in a town grown by the real sim, checked rather than photographed: who is in bed at
 * midnight and, for everyone who is not, where they stand, what they are doing, and whether the pathfinder finds them
 * a way to their bed from there; who is at work in the morning; that walking away and back folds and re-embodies
 * everyone without a duplicate or a lost resident; and that a conversation opens, answers and closes on goodbye.
 *
 * <p>{@code -Dhearth.tour=folknight}; {@code -Dhearth.night.days=N} (growth, default 100).
 */
public class FolkNight implements FabricClientGameTest {
	private static final int DAYS = Integer.getInteger("hearth.night.days", 100);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Tours.wanted("folknight")) {
			return;
		}
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("20261003");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			for (String c : List.of("gamerule advance_weather false", "gamerule spawn_monsters false", "weather clear", "time set 2000",
				"gamemode spectator @a", "tp @a 30000 300 30000")) {
				world.getServer().runCommand(c);
			}
			settle(context, 3_000);
			Long town = world.getServer().computeOnServer(FolkNight::foundTown);
			if (town == null) {
				log("no site for a temperate town");
				return;
			}
			world.getServer().runOnServer(server -> forceChunks(server, town, true));
			waitApplier(context, world);
			world.getServer().runOnServer(server -> forceChunks(server, town, false));
			world.getServer().runCommand("gamemode creative @a");
			BlockPos c = world.getServer().computeOnServer(server -> settlement(server, town).center());
			teleport(world, c.getX() + 0.5, surface(world, c) + 18, c.getZ() + 0.5, 0, 60);
			settle(context, 20_000);
			log("town: " + world.getServer().computeOnServer(server -> {
				SimSettlement s = settlement(server, town);
				return s.name() + " " + s.population() + " souls, plots " + s.plotCounts() + ", embodied " + s.embodied();
			}));
			world.getServer().runCommand("gamerule advance_time true");

			// evening, then let the clock run through bedtime as it would in play
			timeTo(world, 12_500);
			settle(context, 30_000);
			timeTo(world, 15_600);
			settle(context, 75_000);
			log("night 1: " + night(world, town));
			settle(context, 45_000);
			log("night 2: " + night(world, town));
			sleeperShot(context, world, town);

			// walk away (everyone folds) and come back (everyone re-embodies): no duplicate, nobody lost
			log("before leaving: " + census(world, town));
			teleport(world, c.getX() + 600.5, 200, c.getZ() + 0.5, 0, 30);
			settle(context, 15_000);
			log("away: " + census(world, town));
			teleport(world, c.getX() + 0.5, surface(world, c) + 18, c.getZ() + 0.5, 0, 60);
			settle(context, 20_000);
			log("back: " + census(world, town));

			// morning: at work?
			timeTo(world, 1_000);
			settle(context, 90_000);
			log("morning: " + morning(world, town));
			conversation(context, world, town);
			settle(context, 2_000);
		}
	}

	// ---------------------------------------------------------------- checks

	/** At night: who is asleep, and for everyone else what holds them up. */
	private static String night(TestSingleplayerContext world, long town) {
		return world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			SimSettlement s = settlement(server, town);
			int embodied = 0;
			int asleep = 0;
			int sat = 0;
			int up = 0;
			StringBuilder detail = new StringBuilder();
			for (Resident r : s.residents()) {
				Folk f = NpcModule.embodiment().entityOf(r.id);
				if (f == null || f.brain() == null) {
					continue;
				}
				embodied++;
				FolkBrain brain = f.brain();
				if (f.isSleeping()) {
					asleep++;
					continue;
				}
				String what = brain.describe();
				if (what.contains("asleep")) {
					sat++;
				} else {
					up++;
				}
				ResidentPlaces p = brain.places(level);
				BlockPos bed = p.bed;
				String path = "no bed";
				if (bed != null) {
					f.getNavigation().setRequiredPathLength(160);
					List<BlockPos> spots = dev.voidmc.hearth.npc.entity.FolkSpots.approaches(level, bed);
					Path way = spots.isEmpty() ? f.getNavigation().createPath(bed, 1) : f.getNavigation().createPath(new java.util.HashSet<>(spots), 0);
					f.getNavigation().setRequiredPathLength(16);
					path = way == null ? "no path" : way.canReach() ? "path ok (" + way.getNodeCount() + " nodes)"
						: "partial path to " + way.getEndNode().asBlockPos().toShortString();
				}
				SimPlot home = r.home == Resident.NO_PLOT ? null : s.plot(r.home);
				if (detail.length() < 6000) {
					detail.append(String.format(Locale.ROOT, "%n    - %s (%s, %s) home %s: at %s (in %s, on %s), bed %s (%.0f away, dy %d), %s; %s", f.folkName(),
						r.job.getSerializedName(), r.age.getSerializedName(), home == null ? "none" : home.spec().archetype().getPath() + " #" + home.id(),
						f.blockPosition().toShortString(), f.getInBlockState().getBlock().getName().getString(), f.getBlockStateOn().getBlock().getName().getString(),
						bed == null ? "-" : bed.toShortString(), bed == null ? 0.0 : Math.sqrt(bed.distSqr(f.blockPosition())),
						bed == null ? 0 : bed.getY() - f.getBlockY(), what, path));
				}
			}
			return String.format(Locale.ROOT, "time %d: %d embodied, %d asleep in bed, %d resting where they are, %d still up%s",
				level.getOverworldClockTime() % 24000, embodied, asleep, sat, up, detail);
		});
	}

	/** Entities against records: duplicates (two entities for one resident) and entities whose resident is gone. */
	private static String census(TestSingleplayerContext world, long town) {
		return world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			SimSettlement s = settlement(server, town);
			Map<UUID, Integer> seen = new HashMap<>();
			int strays = 0;
			int folk = 0;
			for (Folk f : level.getEntities(dev.voidmc.hearth.npc.entity.FolkEntities.FOLK, e -> true)) {
				folk++;
				UUID id = f.residentId();
				if (id == null) {
					continue;
				}
				seen.merge(id, 1, Integer::sum);
				if (f.settlementId() == town && s.resident(id) == null) {
					strays++;
				}
			}
			int dups = 0;
			for (int n : seen.values()) {
				if (n > 1) {
					dups++;
				}
			}
			int flagged = 0;
			int mismatched = 0;
			for (Resident r : s.residents()) {
				boolean entity = NpcModule.embodiment().entityOf(r.id) != null;
				if (s.isResidentEmbodied(r.id)) {
					flagged++;
				}
				if (s.isResidentEmbodied(r.id) != entity) {
					mismatched++;
				}
			}
			return String.format(Locale.ROOT, "%d souls, %d folk entities in the level, %d managed, %d flagged embodied, %d flag mismatches, %d duplicates, "
				+ "%d with no resident", s.population(), folk, NpcModule.embodiment().count(), flagged, mismatched, dups, strays);
		});
	}

	private static String morning(TestSingleplayerContext world, long town) {
		return world.getServer().computeOnServer(server -> {
			SimSettlement s = settlement(server, town);
			int workers = 0;
			int atWork = 0;
			int asleep = 0;
			StringBuilder detail = new StringBuilder();
			StringBuilder builders = new StringBuilder();
			for (Resident r : s.residents()) {
				Folk f = NpcModule.embodiment().entityOf(r.id);
				if (f == null || f.brain() == null) {
					continue;
				}
				if (f.isSleeping()) {
					asleep++;
				}
				if (r.job == dev.voidmc.hearth.api.JobType.BUILDER && builders.length() < 1500) {
					builders.append(String.format(Locale.ROOT, "%n    - builder %s at %s: %s", f.folkName(), f.blockPosition().toShortString(), f.brain().describe()));
				}
				SimPlot p = r.work == Resident.NO_PLOT ? null : s.plot(r.work);
				if (p == null) {
					continue;
				}
				workers++;
				BoundingBox reach = p.reach().inflatedBy(3);
				if (reach.isInside(f.blockPosition())) {
					atWork++;
				} else if (detail.length() < 3000) {
					detail.append(String.format(Locale.ROOT, "%n    - %s (%s) work %s #%d: at %s (%.0f from it), %s", f.folkName(), r.job.getSerializedName(),
						p.spec().archetype().getPath(), p.id(), f.blockPosition().toShortString(),
						Math.sqrt(f.blockPosition().distSqr(p.spec().origin())), f.brain().describe()));
				}
			}
			return String.format(Locale.ROOT, "time %d: %d with a work plot, %d at it, %d still asleep%s%s", server.overworld().getOverworldClockTime() % 24000,
				workers, atWork, asleep, detail, builders);
		});
	}

	/** A conversation with the nearest awake folk: it opens on the client, answers, and goodbye closes it. */
	private static void conversation(ClientGameTestContext context, TestSingleplayerContext world, long town) {
		Integer id = world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().get(0);
			Folk best = null;
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.brain() != null && !f.isSleeping() && f.brain().settlement.id() == town
					&& (best == null || f.distanceToSqr(player) < best.distanceToSqr(player))) {
					best = f;
				}
			}
			if (best == null) {
				return null;
			}
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
			player.teleportTo(server.overworld(), best.getX() + 1.5, best.getY(), best.getZ() + 0.5, java.util.Set.of(), 90, 0, true);
			NpcModule.conversations().open(player, best);
			return best.getId();
		});
		if (id == null) {
			log("conversation: nobody awake to talk to");
			return;
		}
		settle(context, 1_500);
		boolean opened = context.computeOnClient(mc -> mc.gui.screen() instanceof ConversationScreen);
		world.getServer().runOnServer(server -> NpcModule.conversations().choose(server.getPlayerList().getPlayers().get(0), id, "npc:about"));
		settle(context, 1_000);
		context.takeScreenshot("hearth_folknight_conversation");
		world.getServer().runOnServer(server -> NpcModule.conversations().choose(server.getPlayerList().getPlayers().get(0), id, "npc:bye"));
		settle(context, 1_000);
		boolean closed = context.computeOnClient(mc -> !(mc.gui.screen() instanceof ConversationScreen));
		log("conversation: opened " + opened + ", closed on goodbye " + closed);
	}

	private static void sleeperShot(ClientGameTestContext context, TestSingleplayerContext world, long town) {
		double[] cam = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.brain() == null || f.brain().settlement.id() != town || !f.isSleeping()) {
					continue;
				}
				BlockPos bed = f.getSleepingPos().orElse(f.blockPosition());
				for (int r = 2; r <= 3; r++) {
					for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
						BlockPos q = bed.relative(d, r);
						for (int dy = -1; dy <= 1; dy++) {
							BlockPos cc = q.above(dy);
							if (level.getBlockState(cc).getCollisionShape(level, cc).isEmpty() && level.getBlockState(cc.above()).getCollisionShape(level, cc.above()).isEmpty()
								&& !level.getBlockState(cc.below()).getCollisionShape(level, cc.below()).isEmpty() && !level.canSeeSky(cc)) {
								return new double[]{cc.getX() + 0.5, cc.getY(), cc.getZ() + 0.5, f.getX(), f.getY() + 0.3, f.getZ()};
							}
						}
					}
				}
			}
			return null;
		});
		if (cam == null) {
			return;
		}
		double dx = cam[3] - cam[0];
		double dy = cam[4] - (cam[1] + 1.62);
		double dz = cam[5] - cam[2];
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		teleport(world, cam[0], cam[1], cam[2], yaw, Mth.clamp(pitch, -60, 60));
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		settle(context, 3_000);
		context.takeScreenshot("hearth_folknight_sleeper");
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	// ---------------------------------------------------------------- the town

	private static @Nullable Long foundTown(MinecraftServer server) {
		ServerLevel level = server.overworld();
		SimWorld w = SimServer.world(level);
		if (w == null) {
			return null;
		}
		BlockPos spawn = level.getRespawnData().pos();
		BlockPos best = null;
		Culture bestCulture = null;
		int bestRelief = Integer.MAX_VALUE;
		java.util.Map<String, Integer> why = new java.util.TreeMap<>();
		// a temperate town if there is a site for one, else any culture's
		for (int pass = 0; pass < 2 && best == null; pass++) {
			for (int r = 128; r <= 1600 && (best == null || bestRelief > 3 || r < 512); r += 64) {
				for (int k = 0; k < 16; k++) {
					double a = k * Math.PI / 8 + r * 0.01;
					BlockPos at = new BlockPos(spawn.getX() + (int) (Math.cos(a) * r), 0, spawn.getZ() + (int) (Math.sin(a) * r));
					int g = w.terrain().groundY(at.getX(), at.getZ());
					if (g <= w.terrain().seaLevel() + 2) {
						why.merge("water", 1, Integer::sum);
						continue;
					}
					int lo = g;
					int hi = g;
					for (int dx = -40; dx <= 40; dx += 10) {
						for (int dz = -40; dz <= 40; dz += 10) {
							int h = w.terrain().groundY(at.getX() + dx, at.getZ() + dz);
							lo = Math.min(lo, h);
							hi = Math.max(hi, h);
						}
					}
					if (hi - lo >= bestRelief) {
						continue;
					}
					Culture c = CultureVote.at(at.getX(), at.getZ(), w.probe()::biomeHolder, dev.voidmc.hearth.api.HearthServices::culture);
					if (pass == 0 && c != Culture.TEMPERATE) {
						why.merge("culture " + c.getSerializedName(), 1, Integer::sum);
						continue;
					}
					String no = w.whyNotFound(at, SimWorld.FOUND_SPACING, true);
					if (no != null) {
						why.merge(no, 1, Integer::sum);
						continue;
					}
					bestRelief = hi - lo;
					best = at;
					bestCulture = c;
				}
			}
		}
		if (best == null) {
			log("no site: " + why);
			return null;
		}
		SimSettlement s = w.found(best, bestCulture, null);
		for (int d = 0; d < DAYS; d += 4) {
			for (Goods g : Goods.VALUES) {
				int want = s.stockpile().capacity(g) * 3 / 4 - s.stockpile().get(g);
				if (want > 0) {
					s.stockpile().add(g, want);
				}
			}
			if (s.treasury() < 3000) {
				s.deposit(3000 - s.treasury());
			}
			w.simulate(s, 4);
		}
		log(String.format(Locale.ROOT, "founded %s at %s (relief %d), grew %d days: %d souls, %s, plots %s", s.name(), s.center().toShortString(), bestRelief, DAYS,
			s.population(), s.tier().getSerializedName(), s.plotCounts()));
		return s.id();
	}

	private static SimSettlement settlement(MinecraftServer server, long id) {
		return SimServer.world(server.overworld()).settlement(id);
	}

	private static void forceChunks(MinecraftServer server, long town, boolean force) {
		ServerLevel level = server.overworld();
		BoundingBox b = settlement(server, town).bounds();
		for (int cx = (b.minX() >> 4) - 1; cx <= (b.maxX() >> 4) + 1; cx++) {
			for (int cz = (b.minZ() >> 4) - 1; cz <= (b.maxZ() >> 4) + 1; cz++) {
				level.setChunkForced(cx, cz, force);
			}
		}
	}

	private static void waitApplier(ClientGameTestContext context, TestSingleplayerContext world) {
		for (int i = 0; i < 240; i++) {
			settle(context, 500);
			int pending = world.getServer().computeOnServer(server -> SimServer.world(server.overworld()).applier().pending());
			if (pending == 0 && i > 6) {
				break;
			}
		}
	}

	private static int surface(TestSingleplayerContext world, BlockPos at) {
		return world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			level.getChunk(at.getX() >> 4, at.getZ() >> 4);
			return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		});
	}

	private static void timeTo(TestSingleplayerContext world, long t) {
		long now = world.getServer().computeOnServer(server -> server.overworld().getOverworldClockTime() % 24_000L);
		long add = ((t - now) % 24_000L + 24_000L) % 24_000L;
		world.getServer().runCommand("time add " + add);
	}

	private static void teleport(TestSingleplayerContext world, double x, double y, double z, float yaw, float pitch) {
		world.getServer().runOnServer(server -> {
			ServerPlayer p = server.getPlayerList().getPlayers().get(0);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
			p.teleportTo(server.overworld(), x, y, z, java.util.Set.of(), Mth.wrapDegrees(yaw), pitch, true);
		});
	}

	private static void settle(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}

	private static void log(String line) {
		System.out.println("[hearth folknight] " + line);
	}
}
