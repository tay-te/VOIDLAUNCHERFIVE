package dev.voidmc.hearth.gametest;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.routine.Schedule;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.terrain.CultureVote;
import dev.voidmc.hearth.sim.world.SimWorld;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.life.Dressings;
import dev.voidmc.hearth.social.life.Venues;
import dev.voidmc.hearth.social.rhythm.PlayBehaviour;
import dev.voidmc.hearth.social.rhythm.Rhythm;
import dev.voidmc.hearth.social.rhythm.RhythmCalendar;
import dev.voidmc.hearth.social.rhythm.ServiceBehaviour;
import dev.voidmc.hearth.social.rhythm.TavernNight;
import dev.voidmc.hearth.social.rhythm.WashBehaviour;
import dev.voidmc.hearth.social.rhythm.WatchLights;
import dev.voidmc.hearth.social.rhythm.WatchRounds;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * The daily rhythm in a held-embodied village (docs/LIVING_WORLD_2.md §2.8), checked and photographed: mass (pews filled,
 * the priest at the altar), wash day (washers at the water, laundry lines up), children's games at 15:00, a tavern night
 * (the musician, tables in use), the night watch (block light at the watchman's feet), chimney smoke at dusk; then the
 * town let go (no watch lights, no rhythm dressings left). {@code -Dhearth.tour=rhythm}; {@code -Dhearth.rhythm.days=N}.
 */
public class RhythmTour implements FabricClientGameTest {
	private static final int DAYS = Integer.getInteger("hearth.rhythm.days", 70);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Tours.wanted("rhythm")) {
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
			Long town = world.getServer().computeOnServer(RhythmTour::foundTown);
			if (town == null) {
				log("no site for a temperate town");
				return;
			}
			// a town with no night guard of its own: one of its men takes the night duty, before anyone is embodied (so his day is a watchman's)
			world.getServer().runOnServer(server -> {
				SimSettlement s = settlement(server, town);
				// (Schedule: a guard whose seed's third is 0 keeps the dark hours)
				for (dev.voidmc.hearth.api.Resident r : s.residents()) {
					long seed = r.id.getMostSignificantBits() ^ r.id.getLeastSignificantBits();
					if (r.job == JobType.GUARD && Math.floorMod(seed >> 12, 3) == 0) {
						return;
					}
				}
				for (dev.voidmc.hearth.api.Resident r : s.residents()) {
					long seed = r.id.getMostSignificantBits() ^ r.id.getLeastSignificantBits();
					if (r.age == AgeStage.ADULT && Math.floorMod(seed >> 12, 3) == 0 && (r.job == JobType.FARMER || r.job == JobType.NONE)) {
						r.job = JobType.GUARD;
						s.markDirty();
						log("appointed " + r.fullName() + " a guard");
						return;
					}
				}
			});
			world.getServer().runOnServer(server -> forceChunks(server, town, true));
			waitApplier(context, world);
			world.getServer().runOnServer(server -> forceChunks(server, town, false));
			world.getServer().runCommand("gamemode creative @a");
			BlockPos c = world.getServer().computeOnServer(server -> settlement(server, town).center());
			teleport(world, c.getX() + 0.5, surface(world, c) + 18, c.getZ() + 0.5, 0, 60);
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			settle(context, 15_000);
			log("town: " + world.getServer().computeOnServer(server -> {
				SimSettlement s = settlement(server, town);
				return s.name() + " " + s.population() + " souls, plots " + s.plotCounts() + ", embodied " + s.embodied();
			}));
			world.getServer().runCommand("gamerule advance_time true");

			// the rest day: mass at nine
			int serviceStart = world.getServer().computeOnServer(server -> dev.voidmc.hearth.social.rhythm.RhythmData.of(settlement(server, town).culture())
				.service().start());
			log("the service is at " + serviceStart / 60 + ":" + String.format("%02d", serviceStart % 60));
			timeTo(world, Schedule.dayTimeOf(serviceStart - 25));
			world.getServer().runOnServer(server -> Rhythm.holdDay(settlement(server, town), RhythmCalendar.Forced.REST));
			dev.voidmc.hearth.social.rhythm.RhythmConfig cfg0 = Rhythm.config();
			log("held: " + world.getServer().computeOnServer(server -> {
				SimSettlement s = settlement(server, town);
				long today = dev.voidmc.hearth.social.life.TownLife.today(s);
				return "today " + today + ", rest " + dev.voidmc.hearth.api.HearthServices.calendar().restDay(s, today) + ", calendar "
					+ dev.voidmc.hearth.api.HearthServices.calendar().getClass().getSimpleName();
			}));
			world.getServer().runOnServer(server -> {
				java.util.UUID leads = Rhythm.officiant(settlement(server, town));
				if (leads != null) {
					gather(server, town, java.util.Set.of(leads), 1);
				}
			});
			settle(context, 40_000); // the peal, the walk to the chapel
			timeTo(world, Schedule.dayTimeOf(serviceStart + 30)); // the sermon, between the chants
			world.getServer().runCommand("gamerule advance_time false"); // held at the sermon while the shot is taken (a slow tick runs the clock on)
			settle(context, 10_000);
			log("mass: " + world.getServer().computeOnServer(server -> mass(server, town)));
			shotAt(context, world, town, "hearth_rhythm_mass", server -> churchCamera(server, town));
			world.getServer().runCommand("gamerule advance_time true");
			log("rhythm: " + world.getServer().computeOnServer(server -> String.join(" | ", Rhythm.stats())));

			// wash day: at the water, laundry in the yards
			teleport(world, c.getX() + 0.5, surface(world, c) + 18, c.getZ() + 0.5, 0, 60);
			settle(context, 5_000);
			timeTo(world, Schedule.dayTimeOf(9 * 60 + 50));
			// every household sends someone to the water today, so the embodied part of town shows it
			Rhythm.setConfig(new dev.voidmc.hearth.social.rhythm.RhythmConfig(cfg0.rhythm(), cfg0.lights(), cfg0.service(), cfg0.tavern(),
				new dev.voidmc.hearth.social.rhythm.RhythmConfig.Wash(true, 1.0, 3, cfg0.wash().reach(), cfg0.wash().scanReads(), cfg0.wash().lines()), cfg0.smoke()));
			world.getServer().runOnServer(server -> Rhythm.holdDay(settlement(server, town), RhythmCalendar.Forced.WASH));
			world.getServer().runOnServer(server -> gather(server, town, Rhythm.washers(settlement(server, town),
				dev.voidmc.hearth.social.life.TownLife.today(settlement(server, town))), 5));
			settle(context, 30_000);
			log("wash day: " + world.getServer().computeOnServer(server -> wash(server, town)));
			shotAt(context, world, town, "hearth_rhythm_wash", server -> folkCamera(server, town, WashBehaviour.ID, 5));
			shotAt(context, world, town, "hearth_rhythm_laundry", server -> laundryCamera(server, town));

			Rhythm.setConfig(cfg0);
			// children's games at three
			timeTo(world, Schedule.dayTimeOf(15 * 60));
			for (int i = 0; i < 3 && world.getServer().computeOnServer(server -> dev.voidmc.hearth.api.HearthServices.calendar().marketDay(settlement(server, town),
				dev.voidmc.hearth.social.life.TownLife.today(settlement(server, town)))); i++) {
				world.getServer().runCommand("time add 24000"); // the children are at the market on market day: the next day
			}
			world.getServer().runOnServer(server -> Rhythm.holdDay(settlement(server, town), RhythmCalendar.Forced.PLAIN));
			settle(context, 25_000);
			log("children: " + world.getServer().computeOnServer(server -> behaviours(server, town, PlayBehaviour.ID)));
			shotAt(context, world, town, "hearth_rhythm_play", server -> folkCamera(server, town, PlayBehaviour.ID, 7));

			// supper smoke at dusk
			timeTo(world, Schedule.dayTimeOf(18 * 60 + 35));
			settle(context, 6_000);
			shotAt(context, world, town, "hearth_rhythm_smoke", server -> overview(server, town));

			// a tavern night
			timeTo(world, Schedule.dayTimeOf(19 * 60 + 10));
			world.getServer().runOnServer(server -> {
				SimSettlement s = settlement(server, town);
				// the drinkers of the fullest tavern, so one room fills
				List<java.util.Set<java.util.UUID>> by = Rhythm.drinkersByTavern(s, dev.voidmc.hearth.social.life.TownLife.today(s));
				gather(server, town, by.isEmpty() ? java.util.Set.of() : by.get(0), 10);
				java.util.Set<java.util.UUID> hands = new java.util.HashSet<>();
				for (dev.voidmc.hearth.api.Resident r : s.residents()) {
					if (r.job == JobType.INNKEEPER || r.job == JobType.ENTERTAINER) {
						hands.add(r.id);
					}
				}
				gather(server, town, hands, 4);
			});
			settle(context, 60_000);
			log("tavern night: " + world.getServer().computeOnServer(server -> tavern(server, town)));
			shotAt(context, world, town, "hearth_rhythm_tavern", server -> tavernCamera(server, town));

			// the night watch with torches
			timeTo(world, Schedule.dayTimeOf(22 * 60 + 20));
			world.getServer().runOnServer(server -> {
				SimSettlement s = settlement(server, town);
				java.util.Set<java.util.UUID> watch = new java.util.HashSet<>();
				for (dev.voidmc.hearth.api.Resident r : s.residents()) {
					if ((r.job == JobType.GUARD || r.job == JobType.JAILER) && dev.voidmc.hearth.api.HearthServices.npc().onDuty(s, r)) {
						watch.add(r.id);
					}
				}
				int guards = 0;
				for (dev.voidmc.hearth.api.Resident r : s.residents()) {
					if (r.job == JobType.GUARD || r.job == JobType.JAILER) {
						guards++;
					}
				}
				log("night guards on duty: " + watch.size() + " of " + guards + " guards and jailers");
				if (watch.isEmpty()) {
					// none tonight (the sim's job pass may have taken back the guard named at the start): one of the town's men
					// takes the night duty, embodied afresh so his day is a watchman's
					for (dev.voidmc.hearth.api.Resident r : s.residents()) {
						long seed = r.id.getMostSignificantBits() ^ r.id.getLeastSignificantBits();
						if (r.age == AgeStage.ADULT && Math.floorMod(seed >> 12, 3) == 0 && (r.job == JobType.FARMER || r.job == JobType.NONE)) {
							r.job = JobType.GUARD;
							Folk old = NpcModule.embodiment().entityOf(r.id);
							if (old != null) {
								NpcModule.embodiment().fold(server.overworld(), old, true);
							}
							watch.add(r.id);
							log("appointed " + r.fullName() + " to the night watch");
							break;
						}
					}
				}
				gather(server, town, watch, 3);
			});
			settle(context, 25_000);
			log("night watch: " + world.getServer().computeOnServer(server -> watch(server, town)));
			shotAt(context, world, town, "hearth_rhythm_watch", server -> folkCamera(server, town, WatchRounds.ID, 5));
			log("rhythm: " + world.getServer().computeOnServer(server -> String.join(" | ", Rhythm.stats())));
			log("living world: " + String.join(" | ", LivingWorldStats.lines()));

			// let the town go: nothing of the rhythm stays in the world
			teleport(world, c.getX() + 900.5, 200, c.getZ() + 0.5, 0, 30);
			settle(context, 20_000);
			log("let go: " + world.getServer().computeOnServer(server -> {
				SimSettlement s = settlement(server, town);
				long rhythmDressings = s.data(SocialModule.LEDGER).dressings.stream().filter(d -> d.event().startsWith(Dressings.RHYTHM)).count();
				return String.format(Locale.ROOT, "embodied %s, watch lights %d, rhythm dressing blocks %d", s.embodied(), WatchLights.standing(s), rhythmDressings);
			}));
		}
	}

	// ---------------------------------------------------------------- checks

	private static boolean doing(Folk f, Identifier behaviour) {
		NpcService.FolkBehaviour b = f.brain() == null ? null : f.brain().behaviour();
		return b != null && b.id().equals(behaviour);
	}

	private static String behaviours(MinecraftServer server, long town, Identifier id) {
		int n = 0;
		int kids = 0;
		StringBuilder what = new StringBuilder();
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() == null || f.brain().settlement.id() != town) {
				continue;
			}
			if (f.brain().resident.age == AgeStage.CHILD) {
				kids++;
				if (what.length() < 600) {
					what.append(f.folkName()).append(": ").append(f.brain().behaviour() == null ? f.brain().describe() : f.brain().behaviour().id().getPath())
						.append("; ");
				}
			}
			if (doing(f, id)) {
				n++;
			}
		}
		return n + " folk doing " + id.getPath() + ", " + kids + " children embodied: " + what;
	}

	private static String mass(MinecraftServer server, long town) {
		SimSettlement s = settlement(server, town);
		PlotView church = dev.voidmc.hearth.social.rhythm.Service.church(s).plot();
		int at = 0;
		int shown = 0;
		ServiceBehaviour probe = new ServiceBehaviour();
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() != null && f.brain().settlement.id() == town && doing(f, ServiceBehaviour.ID)) {
				at++;
			} else if (f.brain() != null && f.brain().settlement.id() == town && shown++ < 6) {
				log(String.format(Locale.ROOT, "  not at the service: %s (%s %s) %s, wants %s", f.folkName(), f.brain().resident.job.getSerializedName(),
					f.brain().resident.age.getSerializedName(), f.brain().behaviour() == null ? f.brain().describe() : f.brain().behaviour().id().getPath(),
					probe.wants(f.brain().handle())));
			}
		}
		if (church == null) {
			return "no church built; " + at + " folk at an open-air blessing";
		}
		List<Poi> seats = Venues.inPlot(s.pois(PoiType.SEAT), church);
		List<Poi> altars = Venues.inPlot(s.pois(PoiType.ALTAR), church);
		int filled = 0;
		for (Poi seat : seats) {
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.folkPose() == FolkPose.SIT && f.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(seat.pos())) < 1.0) {
					filled++;
					break;
				}
			}
		}
		int shownAt = 0;
		for (Folk f : NpcModule.embodiment().entities()) {
			if (doing(f, ServiceBehaviour.ID) && shownAt++ < 6) {
				double best = 99;
				for (Poi seat : seats) {
					best = Math.min(best, Math.sqrt(f.blockPosition().distSqr(seat.pos())));
				}
				log(String.format(Locale.ROOT, "  at the service: %s at %s, pose %s, %.1f from a pew, walking %s", f.folkName(), f.blockPosition().toShortString(),
					f.folkPose(), best, !f.getNavigation().isDone()));
			}
		}
		String priest = "no officiant embodied";
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() != null && f.brain().settlement.id() == town && doing(f, ServiceBehaviour.ID)
				&& f.brain().resident.id.equals(dev.voidmc.hearth.social.rhythm.Rhythm.leader(s))) {
				double d = altars.isEmpty() ? -1 : Math.sqrt(f.blockPosition().distSqr(altars.get(0).pos()));
				priest = String.format(Locale.ROOT, "%s %s %.1f from the altar (%s)", f.brain().resident.job.getSerializedName(), f.folkName(), d, doing(f, ServiceBehaviour.ID) ? "officiating" : f.brain().describe());
			}
		}
		java.util.UUID leads = Rhythm.leader(s);
		dev.voidmc.hearth.api.Resident lr = leads == null ? null : s.resident(leads);
		Folk lf = leads == null ? null : NpcModule.embodiment().entityOf(leads);
		String who = lr == null ? "none" : lr.fullName() + " (" + lr.job.getSerializedName() + " " + lr.age.getSerializedName() + "), "
			+ (lf == null ? "not embodied" : lf.brain() == null ? "no brain" : lf.brain().describe() + " at " + lf.blockPosition().toShortString());
		return String.format(Locale.ROOT, "%s at %s: %d of %d seats filled (%.0f %%), %d folk at the service, %s; officiant %s", church.spec().archetype().getPath(),
			Rhythm.serviceNow(s), filled, seats.size(), seats.isEmpty() ? 0 : 100.0 * filled / seats.size(), at, priest, who);
	}

	private static String wash(MinecraftServer server, long town) {
		SimSettlement s = settlement(server, town);
		long lines = s.data(SocialModule.LEDGER).dressings.stream().filter(d -> d.event().startsWith(Dressings.RHYTHM + "laundry")).count();
		int washing = 0;
		int kneeling = 0;
		int shown = 0;
		WashBehaviour probe = new WashBehaviour();
		java.util.Set<java.util.UUID> washers = Rhythm.washers(s, dev.voidmc.hearth.social.life.TownLife.today(s));
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() != null && f.brain().settlement.id() == town && f.brain().resident.age == AgeStage.ADULT && !doing(f, WashBehaviour.ID)
				&& shown++ < 8) {
				log(String.format(Locale.ROOT, "  adult %s (%s): washer %s, wants %s, %s", f.folkName(), f.brain().resident.job.getSerializedName(),
					washers.contains(f.brain().resident.id), probe.wants(f.brain().handle()),
					f.brain().behaviour() == null ? f.brain().describe() : f.brain().behaviour().id().getPath()));
			}
		}
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() != null && f.brain().settlement.id() == town && doing(f, WashBehaviour.ID)) {
				washing++;
				if (f.folkPose() == FolkPose.TEND || f.getNavigation().isDone()) {
					kneeling++;
				}
			}
		}
		return washing + " washers (" + kneeling + " at the water), laundry line blocks " + lines + "; " + String.join(" | ", Rhythm.stats());
	}

	private static String tavern(MinecraftServer server, long town) {
		SimSettlement s = settlement(server, town);
		int drinkers = 0;
		int hands = 0;
		int seated = 0;
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() == null || f.brain().settlement.id() != town) {
				continue;
			}
			if (doing(f, TavernNight.ID)) {
				drinkers++;
				double best = 99;
				for (Poi seat : s.pois(PoiType.SEAT)) {
					best = Math.min(best, Math.sqrt(f.blockPosition().distSqr(seat.pos())));
				}
				log(String.format(Locale.ROOT, "  drinker %s at %s, pose %s, %.1f from a seat, walking %s", f.folkName(), f.blockPosition().toShortString(),
					f.folkPose(), best, !f.getNavigation().isDone()));
				if (f.folkPose() == FolkPose.SIT) {
					seated++;
				}
			}
			if (doing(f, TavernNight.Hands.ID)) {
				hands++;
			}
		}
		int tablesInUse = 0;
		int busiest = 0;
		for (PlotView p : Venues.built(s, "commerce/inn", "commerce/tavern")) {
			int here = 0;
			for (Poi t : Venues.inPlot(s.pois(PoiType.TABLE), p)) {
				for (Folk f : NpcModule.embodiment().entities()) {
					if (f.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(t.pos())) < 2.5 * 2.5 && doing(f, TavernNight.ID)) {
						here++;
						break;
					}
				}
			}
			tablesInUse += here;
			busiest = Math.max(busiest, here);
		}
		int[] sb = Rhythm.TAVERN == null ? new int[3] : Rhythm.TAVERN.seatedAndBound();
		return String.format(Locale.ROOT, "%d drinkers (%d seated), %d tables in use (the busiest tavern %d), %d innkeeper/entertainer at work, %d taverns with a drinker playing; "
			+ "behaviour: %d sat, %d bound for a seat, of %d", drinkers, seated, tablesInUse, busiest, hands, Rhythm.musicians(s, dev.voidmc.hearth.social.life.TownLife.today(s)),
			sb[0], sb[1], sb[2]);
	}

	private static String watch(MinecraftServer server, long town) {
		ServerLevel level = server.overworld();
		StringBuilder out = new StringBuilder();
		int n = 0;
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() == null || f.brain().settlement.id() != town || !doing(f, WatchRounds.ID)) {
				continue;
			}
			n++;
			BlockPos feet = f.blockPosition();
			out.append(String.format(Locale.ROOT, " %s at %s: block light %d at the feet, sky %d;", f.folkName(), feet.toShortString(),
				level.getBrightness(LightLayer.BLOCK, feet), level.getBrightness(LightLayer.SKY, feet)));
		}
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() != null && f.brain().settlement.id() == town && (f.brain().resident.job == JobType.GUARD || f.brain().resident.job == JobType.JAILER)
				&& out.length() < 2000) {
				out.append(String.format(Locale.ROOT, " [%s %s: %s, on duty %s]", f.brain().resident.job.getSerializedName(), f.folkName(),
					f.brain().behaviour() == null ? f.brain().describe() : f.brain().behaviour().id().getPath(),
					dev.voidmc.hearth.api.HearthServices.npc().onDuty(f.brain().settlement, f.brain().resident)));
			}
		}
		return n + " watchmen walking, lights standing " + WatchLights.standing(settlement(server, town)) + ":" + out;
	}

	/**
	 * The held village: brings up to {@code most} of these residents in as folk near the square, as if they lived on it
	 * (the embodiment picks the nearest homes; a tour wants the ones the rhythm sends).
	 */
	private static void gather(MinecraftServer server, long town, java.util.Set<java.util.UUID> who, int most) {
		SimSettlement s = settlement(server, town);
		NpcService npc = dev.voidmc.hearth.api.HearthServices.npc();
		int n = 0;
		for (dev.voidmc.hearth.api.Resident r : s.residents()) {
			if (n >= most || !who.contains(r.id)) {
				continue;
			}
			if (npc.embodyNear(server.overworld(), s, r, Venues.square(s)) != null) {
				n++;
			}
		}
		log("brought in " + n + " of " + who.size());
	}

	// ---------------------------------------------------------------- cameras

	interface Camera {
		double @Nullable [] at(MinecraftServer server);
	}

	private static void shotAt(ClientGameTestContext context, TestSingleplayerContext world, long town, String name, Camera camera) {
		double[] cam = world.getServer().computeOnServer(camera::at);
		if (cam == null) {
			log(name + ": nothing to photograph");
			return;
		}
		double dx = cam[3] - cam[0];
		double dy = cam[4] - (cam[1] + 1.62);
		double dz = cam[5] - cam[2];
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		teleport(world, cam[0], cam[1], cam[2], yaw, Mth.clamp(pitch, -70, 70));
		settle(context, 3_500);
		context.takeScreenshot(name);
	}

	/** Inside the church: from near the door toward the altar. */
	private static double @Nullable [] churchCamera(MinecraftServer server, long town) {
		SimSettlement s = settlement(server, town);
		ServerLevel level = server.overworld();
		PlotView p = dev.voidmc.hearth.social.rhythm.Service.church(s).plot();
		if (p != null) {
			{
				List<Poi> altars = Venues.inPlot(s.pois(PoiType.ALTAR), p);
				BlockPos altar = altars.get(0).pos();
				BlockPos mid = Venues.middle(p.spec()).atY(altar.getY());
				double ax = altar.getX() + 0.5;
				double az = altar.getZ() + 0.5;
				double vx = mid.getX() - altar.getX();
				double vz = mid.getZ() - altar.getZ();
				double len = Math.max(1, Math.sqrt(vx * vx + vz * vz));
				for (double k = 2.2; k >= 0.6; k -= 0.2) {
					BlockPos q = BlockPos.containing(ax + vx / len * len * k, altar.getY() + 1, az + vz / len * len * k);
					if (level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir()) {
						return new double[] {q.getX() + 0.5, q.getY(), q.getZ() + 0.5, ax, altar.getY() + 0.6, az};
					}
				}
				return new double[] {mid.getX() + 0.5, altar.getY() + 1.5, mid.getZ() + 0.5, ax, altar.getY() + 0.6, az};
			}
		}
		return overview(server, town);
	}

	/** A few blocks from the first folk doing a behaviour, a little above, looking at them. */
	private static double @Nullable [] folkCamera(MinecraftServer server, long town, Identifier id, int back) {
		ServerLevel level = server.overworld();
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() == null || f.brain().settlement.id() != town || !doing(f, id)) {
				continue;
			}
			net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(f.getX(), f.getY() + 1.0, f.getZ());
			for (double lift : new double[] {1.5, 3.0, 5.0}) {
				for (int turn = 0; turn < 8; turn++) {
					double a = turn * Math.PI / 4;
					BlockPos q = BlockPos.containing(f.getX() + Math.cos(a) * back, f.getY() + lift, f.getZ() + Math.sin(a) * back);
					if (level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir() && sees(level, q, at)) {
						return new double[] {q.getX() + 0.5, q.getY(), q.getZ() + 0.5, f.getX(), f.getY() + 1.0, f.getZ()};
					}
				}
			}
		}
		return null;
	}

	private static double @Nullable [] laundryCamera(MinecraftServer server, long town) {
		SimSettlement s = settlement(server, town);
		ServerLevel level = server.overworld();
		for (var d : s.data(SocialModule.LEDGER).dressings) {
			if (!d.event().startsWith(Dressings.RHYTHM + "laundry") || d.block().equals("minecraft:air")) {
				continue;
			}
			BlockPos p = BlockPos.of(d.pos());
			for (int lift = 2; lift <= 6; lift += 2) {
				for (int turn = 0; turn < 8; turn++) {
					double a = turn * Math.PI / 4 + 0.3;
					BlockPos q = BlockPos.containing(p.getX() + Math.cos(a) * 7, p.getY() + lift, p.getZ() + Math.sin(a) * 7);
					net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(q.getX() + 0.5, q.getY() + 1.62, q.getZ() + 0.5);
					net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atCenterOf(p);
					// a clear view of the line: nothing solid between the eye and the linen
					if (level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir()
						&& level.clip(new net.minecraft.world.level.ClipContext(eye, at, net.minecraft.world.level.ClipContext.Block.VISUAL,
							net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty())).getBlockPos().distManhattan(p) <= 1) {
						return new double[] {q.getX() + 0.5, q.getY(), q.getZ() + 0.5, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5};
					}
				}
			}
		}
		return null;
	}

	private static double @Nullable [] tavernCamera(MinecraftServer server, long town) {
		SimSettlement s = settlement(server, town);
		ServerLevel level = server.overworld();
		// the busiest tavern first
		List<PlotView> taverns = new java.util.ArrayList<>(Venues.built(s, "commerce/inn", "commerce/tavern"));
		taverns.sort(java.util.Comparator.comparingInt(p -> {
			int n = 0;
			for (Folk f : NpcModule.embodiment().entities()) {
				if ((doing(f, TavernNight.ID) || doing(f, TavernNight.Hands.ID)) && Venues.inside(p.spec(), f.blockPosition())) {
					n--;
				}
			}
			return n;
		}));
		for (PlotView p : taverns) {
			List<Poi> tables = Venues.inPlot(s.pois(PoiType.TABLE), p);
			if (tables.isEmpty()) {
				continue;
			}
			BlockPos t = tables.get(0).pos();
			for (int r = 6; r >= 3; r--) {
				for (int turn = 0; turn < 8; turn++) {
					double a = turn * Math.PI / 4;
					BlockPos q = BlockPos.containing(t.getX() + Math.cos(a) * r, t.getY() + 1.2, t.getZ() + Math.sin(a) * r);
					if (level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir() && !level.canSeeSky(q)
						&& sees(level, q, net.minecraft.world.phys.Vec3.atCenterOf(t))) {
						return new double[] {q.getX() + 0.5, q.getY(), q.getZ() + 0.5, t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5};
					}
				}
			}
		}
		return null;
	}

	/** Whether an eye standing at {@code q} sees {@code at} with nothing solid between (the target's own block aside). */
	private static boolean sees(ServerLevel level, BlockPos q, net.minecraft.world.phys.Vec3 at) {
		net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(q.getX() + 0.5, q.getY() + 1.62, q.getZ() + 0.5);
		var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, at, net.minecraft.world.level.ClipContext.Block.VISUAL,
			net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getLocation().distanceTo(at) < 1.2;
	}

	private static double[] overview(MinecraftServer server, long town) {
		SimSettlement s = settlement(server, town);
		BlockPos c = s.center();
		ServerLevel level = server.overworld();
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
		return new double[] {c.getX() + 30.5, y + 22, c.getZ() + 30.5, c.getX(), y, c.getZ()};
	}

	// ---------------------------------------------------------------- the town (as FolkNight)

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
		if (add > 0) {
			world.getServer().runCommand("time add " + add);
		}
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

	private static void log(String s) {
		System.out.println("[hearth rhythm tour] " + s);
	}
}
