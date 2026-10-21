package dev.voidmc.hearth.gametest.npc;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.gametest.Tours;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.crowd.CrowdBehaviour;
import dev.voidmc.hearth.npc.crowd.CrowdStats;
import dev.voidmc.hearth.npc.crowd.Crowds;
import dev.voidmc.hearth.npc.crowd.Reaction;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.terrain.CultureVote;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Crowds that react (docs/LIVING_WORLD_2.md §5.8), in a town grown by the real sim: a drawn sword in the busiest street
 * (folk within 6 are 4+ blocks further within 3 s), a fight with its ring of onlookers, a brawl parted by a guard, a Hero
 * greeted, a guard's word on heavy armour, an Outlaw (folk indoors, doors shut), shutters closed at 21:30 and open at
 * 07:00, with the crowds' timings and barks in the log.
 *
 * <p>{@code -Dhearth.tour=crowds}; {@code -Dhearth.crowds.days=N} (growth, default 60).
 */
public class CrowdsTour implements FabricClientGameTest {
	private static final int DAYS = Integer.getInteger("hearth.crowds.days", 60);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Tours.wanted("crowds")) {
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
			for (String c : List.of("gamerule advance_weather false", "gamerule spawn_monsters false", "gamerule advance_time false", "weather clear",
				"time set 2500", "gamemode spectator @a", "tp @a 30000 300 30000")) {
				world.getServer().runCommand(c);
			}
			settle(context, 3_000);
			Long town = world.getServer().computeOnServer(CrowdsTour::foundTown);
			if (town == null) {
				log("no site for a town");
				return;
			}
			world.getServer().runOnServer(server -> forceChunks(server, town, true));
			waitApplier(context, world);
			world.getServer().runOnServer(server -> forceChunks(server, town, false));
			world.getServer().runCommand("gamemode creative @a");
			BlockPos c = world.getServer().computeOnServer(server -> settlement(server, town).center());
			teleport(world, c.getX() + 0.5, surface(world, c) + 18, c.getZ() + 0.5, 0, 60, true);
			settle(context, 25_000);
			log("town: " + world.getServer().computeOnServer(server -> {
				SimSettlement s = settlement(server, town);
				return s.name() + " " + s.population() + " souls, embodied " + s.embodied() + ", folk about " + NpcModule.embodiment().count();
			}));
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});

			BlockPos spot = world.getServer().computeOnServer(server -> busiest(server, town));
			if (spot == null) {
				log("nobody about");
				return;
			}
			stand(world, spot);
			settle(context, 8_000);
			blade(context, world, town, "blade");
			List<BlockPos> more = world.getServer().computeOnServer(server -> busySpots(server, town, spot));
			for (int i = 0; i < more.size(); i++) {
				stand(world, more.get(i));
				settle(context, 8_000);
				blade(context, world, town, "blade_" + (i + 2));
			}
			settle(context, 10_000);
			stand(world, spot);
			settle(context, 4_000);
			fight(context, world, town);
			settle(context, 8_000);
			brawl(context, world, town);
			settle(context, 6_000);
			stand(world, spot);
			hero(context, world, town);
			armour(context, world, town);
			shutters(context, world, town);
			stand(world, spot);
			settle(context, 4_000);
			outlaw(context, world, town);
			log(CrowdStats.line());
			log(dev.voidmc.hearth.build.BlueprintServiceImpl.Stats.line());
			for (String l : LivingWorldStats.lines()) {
				log(l);
			}
			for (String b : CrowdStats.recentBarks()) {
				log("bark: " + b);
			}
		}
	}

	// ---------------------------------------------------------------- the checks

	/** A sword drawn: who was within 6, and how much further they are 3 s and 5 s later. */
	private static void blade(ClientGameTestContext context, TestSingleplayerContext world, long town, String name) {
		// (guards are not counted: they stand their ground and come over, §5.1)
		Map<UUID, Double> before = world.getServer().computeOnServer(server -> {
			Map<UUID, Double> d = distances(server, town, 6);
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.brain() != null && dev.voidmc.hearth.npc.crowd.Role.of(f.brain().resident, false) == dev.voidmc.hearth.npc.crowd.Role.GUARD) {
					d.remove(f.brain().resident.id);
				}
			}
			return d;
		});
		world.getServer().runCommand("item replace entity @a weapon.mainhand with iron_sword");
		settle(context, 3_000);
		Map<UUID, Double> at3 = world.getServer().computeOnServer(server -> distances(server, town, 40));
		crowdShot(context, world, name);
		settle(context, 2_000);
		Map<UUID, Double> at5 = world.getServer().computeOnServer(server -> distances(server, town, 40));
		int moved3 = 0;
		int moved5 = 0;
		for (Map.Entry<UUID, Double> e : before.entrySet()) {
			if (at3.getOrDefault(e.getKey(), 99.0) >= e.getValue() + 4) {
				moved3++;
			}
			if (at5.getOrDefault(e.getKey(), 99.0) >= e.getValue() + 4) {
				moved5++;
			}
		}
		log(String.format(Locale.ROOT, name + ": %d folk within 6 (guards aside); %d are 4+ blocks further within 3 s, %d within 5 s; scatters %d, guard remarks %d",
			before.size(), moved3, moved5, CrowdStats.reactions(Reaction.SCATTER), CrowdStats.reactions(Reaction.REMARK)));
		for (Map.Entry<UUID, Double> e : before.entrySet()) {
			if (at3.getOrDefault(e.getKey(), 99.0) < e.getValue() + 4) {
				log(world.getServer().computeOnServer(server -> {
					for (Folk f : NpcModule.embodiment().entities()) {
						if (f.brain() != null && f.brain().resident.id.equals(e.getKey())) {
							var b = f.brain().behaviour();
							return String.format(Locale.ROOT, "  not away: %s (%s, %s) from %.1f to %.1f, doing %s, routine priority %d", f.folkName(), f.brain().resident.job,
								f.brain().resident.age, e.getValue(), at3.getOrDefault(e.getKey(), -1.0), b == null ? "-" : b.id(), f.brain().routinePriority());
						}
					}
					return "  not away: gone";
				}));
			}
		}
		world.getServer().runCommand("item replace entity @a weapon.mainhand with air");
	}

	/** A fight with a (tethered) pig before the player: the ring that gathers. */
	private static void fight(ClientGameTestContext context, TestSingleplayerContext world, long town) {
		Integer pigId = world.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			Pig pig = net.minecraft.world.entity.EntityTypes.PIG.create(server.overworld(), EntitySpawnReason.COMMAND);
			if (pig == null) {
				return null;
			}
			Direction d = p.getDirection();
			pig.setPos(p.getX() + d.getStepX() * 2.5, p.getY(), p.getZ() + d.getStepZ() * 2.5);
			pig.setNoAi(true);
			pig.setPermanentlyInvulnerable(true);
			server.overworld().addFreshEntity(pig);
			return pig.getId();
		});
		if (pigId == null) {
			log("fight: no pig");
			return;
		}
		int most = 0;
		for (int i = 0; i < 24; i++) {
			// the player keeps swinging at it: hurting something in the last five seconds is a fight
			world.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				var e = server.overworld().getEntity(pigId);
				if (e != null) {
					p.setLastHurtMob(e);
					p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				}
			});
			settle(context, 500);
			if (i == 16) {
				crowdShot(context, world, "fight");
			}
			if (i >= 8) {
				most = Math.max(most, world.getServer().computeOnServer(server -> watching(server, Reaction.GAWK)));
			}
		}
		log("fight: a ring of " + most + " onlookers at most (gawks issued " + CrowdStats.reactions(Reaction.GAWK) + ")");
		world.getServer().runOnServer(server -> {
			var e = server.overworld().getEntity(pigId);
			if (e != null) {
				e.discard();
			}
		});
	}

	/** Two folk come to blows; a ring gathers and the guard parts them. */
	private static void brawl(ClientGameTestContext context, TestSingleplayerContext world, long town) {
		String started = world.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			SimSettlement s = settlement(server, town);
			List<Folk> two = new ArrayList<>();
			for (Folk f : nearest(server, town, p.blockPosition(), 40)) {
				Resident r = f.brain().resident;
				if (r.age == dev.voidmc.hearth.api.AgeStage.ADULT && r.job != JobType.GUARD && !f.isSleeping()) {
					two.add(f);
				}
				if (two.size() == 2) {
					break;
				}
			}
			if (two.size() < 2) {
				return null;
			}
			boolean ok = Crowds.brawls().start(s, two.get(0).brain().resident.id, two.get(1).brain().resident.id, "tour");
			if (ok) {
				Folk a = two.get(0);
				p.teleportTo(server.overworld(), a.getX() + 7, a.getY() + 3, a.getZ() + 0.5, java.util.Set.of(), 90, 20, true);
			}
			return ok ? two.get(0).folkName() + " and " + two.get(1).folkName() : null;
		});
		if (started == null) {
			log("brawl: no two folk to start one");
			return;
		}
		int most = 0;
		String guard = world.getServer().computeOnServer(server -> Crowds.brawls().active().isEmpty() || Crowds.brawls().active().get(0).guard == null ? "none"
			: "sent");
		for (int i = 0; i < 40; i++) {
			settle(context, 500);
			if (i == 10) {
				crowdShot(context, world, "brawl");
			}
			most = Math.max(most, world.getServer().computeOnServer(server -> watching(server, Reaction.GAWK)));
			if (world.getServer().computeOnServer(server -> Crowds.brawls().active().isEmpty())) {
				break;
			}
		}
		log("brawl: " + started + ", guard " + guard + ", ring of " + most + ", still going " + world.getServer().computeOnServer(server -> !Crowds.brawls()
			.active().isEmpty()));
	}

	/** A Hero of the town walks into the street. */
	private static void hero(ClientGameTestContext context, TestSingleplayerContext world, long town) {
		long hails0 = CrowdStats.reactions(Reaction.HAIL);
		world.getServer().runCommand("hearth standing set @a[limit=1] 100");
		int most = 0;
		for (int i = 0; i < 12; i++) {
			settle(context, 500);
			most = Math.max(most, world.getServer().computeOnServer(server -> watching(server, Reaction.HAIL)));
			if (i == 5) {
				crowdShot(context, world, "hero");
			}
		}
		log("hero: " + most + " hailing at once, " + (CrowdStats.reactions(Reaction.HAIL) - hails0) + " hails in all");
		world.getServer().runCommand("hearth standing set @a[limit=1] 0");
		settle(context, 6_000);
	}

	/** Plate armour beside a guard. */
	private static void armour(ClientGameTestContext context, TestSingleplayerContext world, long town) {
		for (String slot : List.of("head:iron_helmet", "chest:iron_chestplate", "legs:iron_leggings", "feet:iron_boots")) {
			String[] a = slot.split(":");
			world.getServer().runCommand("item replace entity @a armor." + a[0] + " with " + a[1]);
		}
		String guard = world.getServer().computeOnServer(server -> {
			ServerPlayer p = player(server);
			for (Folk f : nearest(server, town, p.blockPosition(), 120)) {
				if (f.brain().resident.job == JobType.GUARD && !f.isSleeping()) {
					p.teleportTo(server.overworld(), f.getX() + 2.5, f.getY(), f.getZ() + 0.5, java.util.Set.of(), 90, 0, true);
					return f.folkName();
				}
			}
			// no guard about: send for one (as the law does), to stand beside the player
			SimSettlement s = settlement(server, town);
			for (Resident r : s.residents()) {
				if (r.job == JobType.GUARD && NpcModule.embodiment().entityOf(r.id) == null) {
					var h = NpcModule.service().embodyNear(server.overworld(), s, r, p.blockPosition().offset(3, 0, 0));
					if (h != null) {
						return r.fullName() + " (sent for)";
					}
				}
			}
			return null;
		});
		settle(context, 6_000);
		long remarks = CrowdStats.recentBarks().stream().filter(b -> b.contains("crowd_armour")).count();
		log("armour: next to guard " + guard + ", armour remarks in the log " + remarks);
		for (String b : CrowdStats.recentBarks()) {
			if (b.contains("crowd_armour")) {
				log("armour remark: " + b);
			}
		}
		for (String slot : List.of("head", "chest", "legs", "feet")) {
			world.getServer().runCommand("item replace entity @a armor." + slot + " with air");
		}
	}

	/** An Outlaw in the street: folk run indoors and shut their doors. */
	private static void outlaw(ClientGameTestContext context, TestSingleplayerContext world, long town) {
		long in0 = CrowdStats.INDOORS.sum();
		long doors0 = CrowdStats.DOORS_SHUT.sum();
		world.getServer().runCommand("hearth standing set @a[limit=1] -100");
		int most = 0;
		for (int i = 0; i < 30; i++) {
			settle(context, 500);
			most = Math.max(most, world.getServer().computeOnServer(server -> watching(server, Reaction.SHUN)));
			if (i == 8) {
				crowdShot(context, world, "outlaw");
			}
		}
		log("outlaw: " + most + " shunning at once, " + (CrowdStats.INDOORS.sum() - in0) + " got indoors, " + (CrowdStats.DOORS_SHUT.sum() - doors0)
			+ " doors shut, town shuttered " + world.getServer().computeOnServer(server -> Crowds.shutters().closed(settlement(server, town), server.overworld())));
		world.getServer().runCommand("hearth standing set @a[limit=1] 0");
		settle(context, 3_000);
	}

	/** Shutters at 21:30 and at 07:00, and a photograph of some closed in a storm. */
	private static void shutters(ClientGameTestContext context, TestSingleplayerContext world, long town) {
		log("shutters by day: " + count(world, town));
		BlockPos c = world.getServer().computeOnServer(server -> settlement(server, town).center());
		teleport(world, c.getX() + 0.5, surface(world, c) + 18, c.getZ() + 0.5, 0, 60, true);
		settle(context, 3_000);
		// half an hour after the town's curfew bell, an hour after its dawn bell (the rhythm's hours for its culture)
		int[] hours = world.getServer().computeOnServer(server -> new int[]{dev.voidmc.hearth.api.HearthServices.calendar().curfew(settlement(server, town)),
			dev.voidmc.hearth.api.HearthServices.calendar().dawn(settlement(server, town))});
		world.getServer().runCommand("time set " + dev.voidmc.hearth.npc.routine.Schedule.dayTimeOf(hours[0] + 30));
		settle(context, 8_000);
		log(String.format(Locale.ROOT, "shutters at %02d:%02d (curfew %02d:%02d): %s", (hours[0] + 30) / 60 % 24, (hours[0] + 30) % 60, hours[0] / 60, hours[0] % 60,
			count(world, town)));
		world.getServer().runCommand("time set " + dev.voidmc.hearth.npc.routine.Schedule.dayTimeOf(hours[1] + 60));
		settle(context, 8_000);
		log(String.format(Locale.ROOT, "shutters at %02d:%02d (dawn bell %02d:%02d): %s", (hours[1] + 60) / 60, (hours[1] + 60) % 60, hours[1] / 60, hours[1] % 60,
			count(world, town)));
		// a storm by day closes them too: a photograph of a closed one
		world.getServer().runCommand("time set 4000");
		world.getServer().runCommand("weather thunder");
		settle(context, 6_000);
		double[] cam = world.getServer().computeOnServer(server -> shutterView(server, town));
		if (cam != null) {
			teleport(world, cam[0], cam[1], cam[2], (float) cam[3], (float) cam[4], true);
			settle(context, 4_000);
			shot(context, "shutters_storm");
		}
		log("shutters in a storm: " + count(world, town));
		world.getServer().runCommand("weather clear");
		settle(context, 6_000);
		if (cam != null) {
			shot(context, "shutters_open");
		}
		log("shutters after the storm: " + count(world, town));
	}

	private static String count(TestSingleplayerContext world, long town) {
		return world.getServer().computeOnServer(server -> {
			int[] n = Crowds.shutters().count(server.overworld(), settlement(server, town));
			return String.format(Locale.ROOT, "%d of %d closed (%.0f %%), %d plots scanned", n[1], n[0], n[0] == 0 ? 0.0 : 100.0 * n[1] / n[0],
				Crowds.shutters().scannedPlots());
		});
	}

	// ---------------------------------------------------------------- helpers

	/** Folk running the crowd behaviour for a reaction now. */
	private static int watching(MinecraftServer server, Reaction r) {
		int n = 0;
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() != null && f.brain().behaviour() instanceof CrowdBehaviour b && b.reaction() == r) {
				n++;
			}
		}
		return n;
	}

	private static Map<UUID, Double> distances(MinecraftServer server, long town, double within) {
		ServerPlayer p = player(server);
		Map<UUID, Double> out = new HashMap<>();
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() != null && f.brain().settlement.id() == town && !f.isSleeping() && f.distanceTo(p) <= within) {
				out.put(f.brain().resident.id, (double) f.distanceTo(p));
			}
		}
		return out;
	}

	private static List<Folk> nearest(MinecraftServer server, long town, BlockPos at, double within) {
		List<Folk> out = new ArrayList<>();
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() != null && f.brain().settlement.id() == town && f.blockPosition().closerThan(at, within)) {
				out.add(f);
			}
		}
		out.sort((a, b) -> Double.compare(a.blockPosition().distSqr(at), b.blockPosition().distSqr(at)));
		return out;
	}

	/** Where the most awake folk stand together, outdoors. */
	private static @Nullable BlockPos busiest(MinecraftServer server, long town) {
		ServerLevel level = server.overworld();
		BlockPos best = null;
		int bestN = 0;
		List<Folk> all = new ArrayList<>();
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() != null && f.brain().settlement.id() == town && !f.isSleeping()) {
				all.add(f);
			}
		}
		for (Folk f : all) {
			if (!level.canSeeSky(f.blockPosition().above())) {
				continue;
			}
			int n = 0;
			for (Folk g : all) {
				if (g.distanceToSqr(f) <= 8 * 8) {
					n++;
				}
			}
			if (n > bestN) {
				bestN = n;
				best = f.blockPosition();
			}
		}
		log("busiest street: " + bestN + " folk within 8 of " + (best == null ? "-" : best.toShortString()) + " (" + all.size() + " awake)");
		return best;
	}

	/** Two more outdoor spots with folk about, away from the first. */
	private static List<BlockPos> busySpots(MinecraftServer server, long town, BlockPos first) {
		ServerLevel level = server.overworld();
		List<Folk> all = new ArrayList<>();
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() != null && f.brain().settlement.id() == town && !f.isSleeping() && level.canSeeSky(f.blockPosition().above())) {
				all.add(f);
			}
		}
		List<BlockPos> out = new ArrayList<>();
		List<BlockPos> taken = new ArrayList<>(List.of(first));
		for (int k = 0; k < 2; k++) {
			BlockPos best = null;
			int bestN = 1;
			for (Folk f : all) {
				boolean far = true;
				for (BlockPos t : taken) {
					far &= !f.blockPosition().closerThan(t, 20);
				}
				if (!far) {
					continue;
				}
				int n = 0;
				for (Folk g : all) {
					n += g.distanceToSqr(f) <= 36 ? 1 : 0;
				}
				if (n > bestN) {
					bestN = n;
					best = f.blockPosition();
				}
			}
			if (best != null) {
				out.add(best);
				taken.add(best);
			}
		}
		return out;
	}

	/** A spot seven blocks out from a known shutter, in the open, looking at it. */
	private static double @Nullable [] shutterView(MinecraftServer server, long town) {
		ServerLevel level = server.overworld();
		for (long at : Crowds.shutters().positions(town)) {
			BlockPos q = BlockPos.of(at);
			BlockState st = level.getBlockState(q);
			if (!(st.getBlock() instanceof TrapDoorBlock)) {
				continue;
			}
			Direction out = st.getValue(TrapDoorBlock.FACING);
			BlockPos cam = q.relative(out, 7).above();
			boolean clear = true;
			for (int i = 1; i <= 7 && clear; i++) {
				BlockPos c = q.relative(out, i);
				clear = level.getBlockState(c).isAir() && level.getBlockState(c.above()).isAir();
			}
			if (clear && level.canSeeSky(cam)) {
				double dx = q.getX() - cam.getX();
				double dz = q.getZ() - cam.getZ();
				float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				return new double[]{cam.getX() + 0.5, cam.getY() - 1.0, cam.getZ() + 0.5, yaw, 8};
			}
		}
		return null;
	}

	private static void stand(TestSingleplayerContext world, BlockPos spot) {
		teleport(world, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 10, false);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of("hearth_crowds_" + name).withSize(1600, 900));
	}

	/** A shot over the player's shoulder, turned toward the folk round them. */
	private static void crowdShot(ClientGameTestContext context, TestSingleplayerContext world, String name) {
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			double sx = 0;
			double sz = 0;
			int n = 0;
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.distanceTo(p) <= 14 && f.distanceTo(p) > 1) {
					sx += f.getX() - p.getX();
					sz += f.getZ() - p.getZ();
					n++;
				}
			}
			if (n > 0) {
				float yaw = (float) Math.toDegrees(Math.atan2(-sx, sz));
				p.teleportTo(server.overworld(), p.getX(), p.getY(), p.getZ(), java.util.Set.of(), yaw, 25, true);
			}
		});
		context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
		settle(context, 600);
		shot(context, name);
		context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
	}

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
		for (int pass = 0; pass < 2 && best == null; pass++) {
			for (int r = 128; r <= 1600 && (best == null || bestRelief > 3 || r < 512); r += 64) {
				for (int k = 0; k < 16; k++) {
					double a = k * Math.PI / 8 + r * 0.01;
					BlockPos at = new BlockPos(spawn.getX() + (int) (Math.cos(a) * r), 0, spawn.getZ() + (int) (Math.sin(a) * r));
					int g = w.terrain().groundY(at.getX(), at.getZ());
					if (g <= w.terrain().seaLevel() + 2) {
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
						continue;
					}
					if (w.whyNotFound(at, SimWorld.FOUND_SPACING, true) != null) {
						continue;
					}
					bestRelief = hi - lo;
					best = at;
					bestCulture = c;
				}
			}
		}
		if (best == null) {
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
		log(String.format(Locale.ROOT, "founded %s at %s, grew %d days: %d souls, %s, plots %s", s.name(), s.center().toShortString(), DAYS, s.population(),
			s.tier().getSerializedName(), s.plotCounts()));
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

	private static void teleport(TestSingleplayerContext world, double x, double y, double z, float yaw, float pitch, boolean fly) {
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.getAbilities().flying = fly;
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
		System.out.println("[hearth crowds] " + line);
	}
}
