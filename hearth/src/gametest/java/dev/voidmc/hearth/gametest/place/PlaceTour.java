package dev.voidmc.hearth.gametest.place;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.economy.household.HouseBook;
import dev.voidmc.hearth.economy.household.Household;
import dev.voidmc.hearth.economy.household.HouseholdIndex;
import dev.voidmc.hearth.economy.household.Servants;
import dev.voidmc.hearth.gametest.Tours;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.companion.CompanionBehaviour;
import dev.voidmc.hearth.npc.companion.CompanionRules;
import dev.voidmc.hearth.npc.companion.CompanionService;
import dev.voidmc.hearth.npc.companion.Companions;
import dev.voidmc.hearth.npc.companion.PlaceConfig;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.person.NpcData;
import dev.voidmc.hearth.npc.person.Trait;
import dev.voidmc.hearth.quest.commission.CommissionDialogue;
import dev.voidmc.hearth.quest.commission.Rank;
import dev.voidmc.hearth.quest.commission.Retinue;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.burgher.BurgherDialogue;
import dev.voidmc.hearth.social.burgher.Burghers;
import dev.voidmc.hearth.social.office.Office;
import dev.voidmc.hearth.social.office.Officials;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The player's place tour (docs/LIVING_WORLD_2.md §8.8; {@code -Dhearth.tour=place}, or {@code HEARTH_LW_TOUR=place} or
 * {@code all}): in a historic town of the dev seed, a companion hired, given a sword and fighting beside the player; a
 * servant greeting the owner at the door of their house; the burgher's oath before the reeve; the dubbing at the throne
 * (where the town has a lord), and a commission asked for; with the companions' tick cost and {@code /hearth sim stats}.
 * Screenshots are {@code hearth_place_*}; every step logs {@code [hearth place]}. The tour's fixtures (coin given, standing
 * raised, a pledge, a service made up to sworn man, the oath allowed on any day) are logged as such.
 */
public class PlaceTour implements FabricClientGameTest {
	private record Town(long id, BlockPos center) {
	}

	static boolean wanted() {
		String env = System.getenv("HEARTH_LW_TOUR");
		if (env != null && (env.contains("place") || env.contains("all"))) {
			return true;
		}
		String only = System.getProperty("hearth.tour");
		return only != null && !only.isBlank() && Tours.wanted("place");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!wanted()) {
			return;
		}
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(8);
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed(System.getProperty("hearth.place.seed", "20261003"));
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("gamerule spawn_monsters false");
			world.getServer().runCommand("weather clear");
			timeTo(world, 2000);
			settle(context, 3_000);
			Town town = historic(context, world);
			if (town == null) {
				log("no town came live near spawn");
				return;
			}
			settle(context, 6_000);
			// a town just adopted fills its offices at its next day (the reeve, the lord)
			world.getServer().runCommand("time add 24000");
			settle(context, 12_000);
			log("town: " + world.getServer().computeOnServer(server -> {
				SimSettlement s = town(server, town);
				Resident lord = Officials.holder(s, s.data(SocialModule.LEDGER), Office.LORD);
				Resident reeve = Officials.holder(s, s.data(SocialModule.LEDGER), Office.REEVE);
				return s.name() + " (" + s.culture().getSerializedName() + ", " + s.tier().getSerializedName() + "), " + s.population() + " souls; lord "
					+ (lord == null ? "none" : lord.fullName()) + ", reeve " + (reeve == null ? "none" : reeve.fullName());
			}));
			world.getServer().runOnServer(server -> {
				// fixtures: coin for the fees, the oath on any day
				EconomyService eco = HearthServices.economy();
				eco.pay(player(server), 3000);
				PlaceConfig c = Companions.config();
				Companions.setConfig(new PlaceConfig(c.companions(), c.household(), c.commissions(), new PlaceConfig.Burghers(c.burghers().minStanding(),
					c.burghers().heldDays(), c.burghers().feeCrowns(), false, c.burghers().dues(), c.burghers().councilDays(), c.burghers().choices()), c.wages()));
				log("fixture: 3 000 bits given; the oath allowed on any day");
			});
			leg("companion", () -> companion(context, world, town));
			leg("servant", () -> servant(context, world, town));
			leg("oath", () -> oath(context, world, town));
			leg("dubbing", () -> dubbing(context, world, town));
			log(String.format(Locale.ROOT, "companions: %.2f µs a tick each over %d ticks (budget ~5); %d online",
				CompanionBehaviour.averageMicros(), CompanionBehaviour.TICKS.sum(), world.getServer().computeOnServer(server -> CompanionService.online())));
			for (String line : world.getServer().computeOnServer(server -> LivingWorldStats.lines())) {
				log("stats: " + line);
			}
			log("server " + world.getServer().computeOnServer(server -> String.format(Locale.ROOT, "%.2f ms a tick",
				dev.voidmc.hearth.sim.LoadCaps.mspt(server))));
		}
	}

	private static void leg(String name, Runnable leg) {
		log("---- " + name);
		try {
			leg.run();
		} catch (RuntimeException e) {
			log(name + " failed: " + e);
			e.printStackTrace();
		}
	}

	// ---------------------------------------------------------------- the legs

	/** A companion hired, given a sword, fighting a husk beside the player. */
	private static void companion(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		String hired = world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			ServerPlayer p = player(server);
			List<Resident> hands = CompanionRules.candidates(s, 3);
			Resident r = hands.isEmpty() ? null : hands.getFirst();
			if (r == null) {
				for (Resident x : s.residents()) {
					if (x.age == AgeStage.ADULT && x.job == dev.voidmc.hearth.api.JobType.GUARD) {
						r = x;
						break;
					}
				}
				if (r == null) {
					return "nobody to hire";
				}
				NpcData.of(r).setTraits(EnumSet.of(Trait.BRAVE));
				log("fixture: " + r.fullName() + " made brave (no willing hand in town)");
			}
			var bounds = s.bounds();
			BlockPos edge = ground(server, new BlockPos(bounds.maxX() + 20, 0, (bounds.minZ() + bounds.maxZ()) / 2));
			BlockPos at = openGround(server, edge);
			if (at == null) {
				at = openGround(server, s.center());
			}
			if (at == null) {
				at = edge;
			}
			p.setGameMode(GameType.SURVIVAL);
			p.getAbilities().invulnerable = true;
			p.onUpdateAbilities();
			p.teleportTo(server.overworld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), 0, 10, true);
			CompanionService.HireResult res = CompanionService.hire(p, s, r);
			Folk f = NpcModule.embodiment().entityOf(r.id);
			if (f != null) {
				f.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
				f.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			}
			return r.fullName() + " the " + r.job.getSerializedName() + ": " + res + ", embodied " + (f != null);
		});
		log("companion: hired " + hired);
		settle(context, 4_000);
		// a husk (it does not burn by day) comes at the player; the companion meets it
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			ServerLevel level = server.overworld();
			Mob husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
			if (husk != null) {
				BlockPos at = ground(server, p.blockPosition().offset(4, 0, 0));
				husk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 90, 0);
				for (Folk f : NpcModule.embodiment().entities()) {
					if (f.brain() != null && Companions.hired(f.brain().resident)) {
						husk.setTarget(f);
					}
				}
				level.addFreshEntity(husk);
			}
			// look along the fight
			p.teleportTo(level, p.getX(), p.getY(), p.getZ(), java.util.Set.of(), -90, 15, true);
		});
		settle(context, 1_200);
		double[] fight = world.getServer().computeOnServer(server -> {
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.brain() != null && Companions.hired(f.brain().resident)) {
					return viewOf(server, f.position(), 5);
				}
			}
			return null;
		});
		if (fight != null) {
			teleport(world, fight[0], fight[1], fight[2], (float) fight[3], (float) fight[4]);
		}
		settle(context, 700);
		context.takeScreenshot("hearth_place_companion");
		log("companion: " + world.getServer().computeOnServer(server -> {
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.brain() != null && Companions.hired(f.brain().resident)) {
					return f.folkName() + " at " + f.blockPosition().toShortString() + ", target " + (f.getTarget() == null ? "none" : f.getTarget().getType())
						+ ", player " + player(server).blockPosition().toShortString();
				}
			}
			return "no companion folk";
		}));
		settle(context, 4_000);
		world.getServer().runOnServer(server -> server.overworld().getEntities(EntityTypes.HUSK, h -> true).forEach(h -> h.discard()));
		// following, measured on its own: the player walks off in steps and the companion keeps up
		CompanionBehaviour.NANOS.reset();
		CompanionBehaviour.TICKS.reset();
		for (int i = 1; i <= 5; i++) {
			int step = i;
			world.getServer().runOnServer(server -> {
				ServerPlayer p = player(server);
				BlockPos at = ground(server, p.blockPosition().offset(step % 2 == 0 ? 7 : -3, 0, 6));
				p.teleportTo(server.overworld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), p.getYRot(), 10, true);
			});
			settle(context, 2_000);
		}
		log(String.format(Locale.ROOT, "companion: following %.2f µs a tick over %d ticks", CompanionBehaviour.averageMicros(), CompanionBehaviour.TICKS.sum()));
		// and at the player's side, the player standing (most of a companion's day)
		settle(context, 3_000);
		CompanionBehaviour.NANOS.reset();
		CompanionBehaviour.TICKS.reset();
		settle(context, 10_000);
		log(String.format(Locale.ROOT, "companion: at the side %.2f µs a tick over %d ticks", CompanionBehaviour.averageMicros(), CompanionBehaviour.TICKS.sum()));
		world.getServer().runOnServer(server -> {
			player(server).setGameMode(GameType.CREATIVE);
			server.overworld().getEntities(EntityTypes.HUSK, h -> true).forEach(h -> h.discard());
		});
	}

	/** A servant greeting the owner home at the door of their house. */
	private static void servant(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		double[] view = world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			ServerPlayer p = player(server);
			PlotView house = null;
			BlockPos door = null;
			for (PlotView pv : s.plots()) {
				if (pv.state() == PlotState.BUILT && pv.spec().archetype().getPath().startsWith("house/") && pv.owner() == null) {
					List<Poi> doors = Servants.pois(s, pv, PoiType.DOOR);
					if (!doors.isEmpty() && pv.spec().origin().distSqr(s.center()) < 120 * 120) {
						house = pv;
						door = doors.getFirst().pos();
						break;
					}
				}
			}
			if (house == null) {
				log("servant: no house with a door near the heart");
				return null;
			}
			s.setPlotOwner(house.id(), p.getUUID());
			Resident hand = null;
			for (Resident r : s.residents()) {
				if (r.age == AgeStage.ADULT && !Companions.hired(r) && r.home != house.id()) {
					hand = r;
					if (r.station == Station.POOR) {
						break;
					}
				}
			}
			if (hand == null) {
				return null;
			}
			if (hand.station != Station.POOR) {
				log("fixture: " + hand.fullName() + " counted poor for the tour");
				hand.station = Station.POOR;
			}
			HouseBook book = s.data(Household.HOUSES);
			Servants.hire(s, book, hand, house, SimServer.world(server.overworld()).day(), Companions.config());
			HouseholdIndex.set(p, HouseholdIndex.of(p).with(new HouseholdIndex.Entry(s.id(), s.dimension(), house.id())));
			s.markDirty();
			NpcModule.service().embodyNear(server.overworld(), s, hand, door);
			log("servant: " + hand.fullName() + " keeps " + house.spec().archetype().getPath() + " #" + house.id() + " (door " + door.toShortString()
				+ ", fixture: deed given to the player)");
			SLACK.set(3);
			double[] v = viewOf(server, net.minecraft.world.phys.Vec3.atBottomCenterOf(door), 6);
			SLACK.set(1);
			if (v == null) {
				log("servant: no clear spot before the door");
			}
			return v;
		});
		if (view == null) {
			return;
		}
		int before = Household.GREETINGS.get();
		teleport(world, view[0], view[1], view[2], (float) view[3], (float) view[4]);
		for (int i = 0; i < 50 && Household.GREETINGS.get() == before; i++) {
			settle(context, 500);
		}
		settle(context, 1_500);
		context.takeScreenshot("hearth_place_servant");
		log("servant: greeted " + (Household.GREETINGS.get() - before) + " time(s); " + world.getServer().computeOnServer(server -> {
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.brain() != null && dev.voidmc.hearth.economy.household.ServantState.serving(f.brain().resident)) {
					return f.folkName() + " at " + f.blockPosition().toShortString() + " (" + (int) Math.sqrt(f.distanceToSqr(player(server)))
						+ " blocks from the player): " + f.brain().describe();
				}
			}
			return "no servant folk embodied";
		}));
	}

	/** The burgher's oath before the reeve. */
	private static void oath(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		double[] view = world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			ServerPlayer p = player(server);
			Resident reeve = Officials.holder(s, s.data(SocialModule.LEDGER), Office.REEVE);
			if (reeve == null) {
				log("oath: the town has no reeve");
				return null;
			}
			long today = SimServer.world(server.overworld()).day();
			s.data(Burghers.KEY).owners.put(p.getUUID(), today - 31);
			StandingService standing = HearthServices.standing();
			standing.deed(new StandingService.Deed(p.getUUID(), s.id(), "quest:completed", 60, null, p.blockPosition(), true));
			log("fixture: property held since day " + (today - 31) + "; standing raised to " + standing.standing(s.id(), p.getUUID()));
			int seat = Officials.seat(s, Office.REEVE);
			PlotView hall = seat < 0 ? null : s.plot(seat);
			BlockPos at = hall == null ? s.center() : hall.spec().origin().offset(hall.spec().sizeX() / 2, 0, hall.spec().sizeZ() / 2);
			BlockPos stand = openGround(server, at);
			if (stand == null) {
				stand = openGround(server, s.center());
			}
			if (stand == null) {
				var bounds = s.bounds();
				stand = openGround(server, ground(server, new BlockPos(bounds.minX() - 20, 0, (bounds.minZ() + bounds.maxZ()) / 2)));
			}
			if (stand == null) {
				stand = ground(server, at);
			}
			log("oath: the ceremony at " + stand.toShortString() + " (" + (int) Math.sqrt(stand.distSqr(at)) + " blocks from the hall)");
			p.teleportTo(server.overworld(), stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, java.util.Set.of(), 180, 5, true);
			var folk = NpcModule.service().embodyNear(server.overworld(), s, reeve, stand.offset(0, 0, -2));
			BurgherDialogue d = new BurgherDialogue();
			boolean ok = d.select(p, s, reeve, "burgher:oath");
			Component said = d.reply(p, s, reeve, "burgher:oath");
			log("oath: before " + reeve.fullName() + " at " + (hall == null ? "the heart" : hall.spec().archetype().getPath()) + ": " + ok + " — "
				+ (said == null ? "" : said.getString()) + "; title now " + standing.title(s.id(), p.getUUID()).getSerializedName() + ", sworn "
				+ Burghers.sworn(s, p.getUUID()));
			if (folk == null) {
				return null;
			}
			folk.lookAt(p);
			double[] face = faceFrom(server, folk.entity().blockPosition(), 4);
			return face != null ? face : viewOf(server, folk.entity().position(), 4);
		});
		if (view != null) {
			teleport(world, view[0], view[1], view[2], (float) view[3], (float) view[4]);
		}
		settle(context, 2_500);
		context.takeScreenshot("hearth_place_oath");
	}

	/** The dubbing at the throne, and a commission asked for. */
	private static void dubbing(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		double[] view = world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			ServerPlayer p = player(server);
			Resident lord = Officials.holder(s, s.data(SocialModule.LEDGER), Office.LORD);
			if (lord == null) {
				log("dubbing: the town has no lord (a commission's seat is a town with one)");
				return null;
			}
			StandingService standing = HearthServices.standing();
			standing.deed(new StandingService.Deed(p.getUUID(), s.id(), "quest:completed", 60, null, p.blockPosition(), true));
			var list = lord.extra("social").getListOrEmpty("pledged").copy();
			list.add(StringTag.valueOf(p.getUUID().toString()));
			lord.extra("social").put("pledged", list);
			Retinue.Service sv = new Retinue.Service(Rank.SWORN, 6, Optional.of(lord.id), 0, true, Optional.empty(), true, false, Optional.empty());
			Retinue.set(p, Retinue.of(p).with(s.id(), sv));
			log("fixture: pledged, a sworn man with six commissions and a raid repelled; standing " + standing.standing(s.id(), p.getUUID()));
			BlockPos throne = null;
			for (Poi poi : s.pois(PoiType.THRONE)) {
				throne = poi.pos();
			}
			BlockPos at = throne != null ? throne : s.center();
			BlockPos stand = ground(server, at.offset(0, 0, 4));
			p.teleportTo(server.overworld(), stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, java.util.Set.of(), 180, 5, true);
			var folk = NpcModule.service().embodyNear(server.overworld(), s, lord, throne != null ? throne : stand.offset(0, 0, -3));
			CommissionDialogue d = new CommissionDialogue();
			boolean risen = d.select(p, s, lord, "commission:rise");
			Component said = d.reply(p, s, lord, "commission:rise");
			Retinue.Service after = Retinue.of(p).service(s.id());
			log("dubbing: at " + (throne == null ? "the heart (no throne)" : "the throne " + throne.toShortString()) + ": " + risen + " — "
				+ (said == null ? "" : said.getString()) + "; rank now " + (after == null ? "none" : after.rank().getSerializedName()) + ", house given "
				+ (after != null && after.house()));
			boolean asked = d.select(p, s, lord, "commission:ask");
			Component offer = d.reply(p, s, lord, "commission:ask");
			log("commission: asked " + asked + " — " + (offer == null ? "" : offer.getString()));
			if (folk == null) {
				return null;
			}
			return viewOf(server, folk.entity().position(), 4);
		});
		if (view != null) {
			teleport(world, view[0], view[1], view[2], (float) view[3], (float) view[4]);
		}
		settle(context, 2_500);
		context.takeScreenshot("hearth_place_dubbing");
	}

	// ---------------------------------------------------------------- the town and helpers

	private static @Nullable Town historic(ClientGameTestContext context, TestSingleplayerContext world) {
		List<dev.voidmc.hearth.api.HearthWorld.SiteInfo> sites = List.of();
		for (int i = 0; i < 40; i++) {
			sites = world.getServer().computeOnServer(server -> {
				SimWorld w = SimServer.world(server.overworld());
				return w == null ? List.<dev.voidmc.hearth.api.HearthWorld.SiteInfo>of()
					: w.knownSites(server.overworld().getRespawnData().pos(), Integer.getInteger("hearth.place.reach", 2500));
			});
			if (sites.size() >= 6) {
				break;
			}
			settle(context, 1_000);
		}
		dev.voidmc.hearth.api.HearthWorld.SiteInfo best = null;
		for (dev.voidmc.hearth.api.HearthWorld.SiteInfo i : sites) {
			if (best == null || i.tier().ordinal() > best.tier().ordinal()) {
				best = i;
			}
		}
		if (best == null) {
			return null;
		}
		log("visiting " + best.name() + " (" + best.culture().getSerializedName() + ", " + best.tier().getSerializedName() + ") at "
			+ best.center().toShortString());
		BlockPos c = best.center();
		teleport(world, c.getX() + 0.5, c.getY() + 30, c.getZ() + 0.5, 0, 60);
		long id = best.settlementId();
		for (int i = 0; i < 120; i++) {
			settle(context, 1_000);
			boolean live = world.getServer().computeOnServer(server -> SimServer.world(server.overworld()).settlement(id) != null);
			if (live) {
				return new Town(id, c);
			}
		}
		return null;
	}

	private static SimSettlement town(MinecraftServer server, Town t) {
		return SimServer.world(server.overworld()).settlement(t.id);
	}

	/**
	 * Where the player stands to look at {@code target} from about {@code dist} blocks, on standable ground near the
	 * target's own height with a clear line to it: {x, y, z, yaw, pitch}, or null.
	 */
	/** How near the target a block the line of sight hits may be (a door's frame); 1 by default. */
	private static final ThreadLocal<Integer> SLACK = ThreadLocal.withInitial(() -> 1);

	/** A spot {@code dist} blocks from {@code who} on open ground at the same height, facing them: {x, y, z, yaw, pitch}, or null. */
	private static double @Nullable [] faceFrom(MinecraftServer server, BlockPos who, int dist) {
		ServerLevel level = server.overworld();
		for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			for (int dy : new int[]{0, 1, -1}) {
				BlockPos c = who.relative(d, dist).above(dy);
				if (level.isEmptyBlock(c) && level.isEmptyBlock(c.above()) && !level.isEmptyBlock(c.below()) && level.canSeeSky(c.above())) {
					float yaw = d.getOpposite().toYRot();
					return new double[]{c.getX() + 0.5, c.getY(), c.getZ() + 0.5, yaw, 10};
				}
			}
		}
		return null;
	}

	private static double @Nullable [] viewOf(MinecraftServer server, net.minecraft.world.phys.Vec3 target, int dist) {
		for (int d : new int[]{dist, dist - 1, dist + 1, dist + 2, dist - 2}) {
			double[] v = d >= 2 ? viewAt(server, target, d, true) : null;
			if (v != null) {
				return v;
			}
		}
		for (int d : new int[]{dist, dist - 1, dist + 1, dist + 2, dist - 2, 2}) {
			double[] v = d >= 2 ? viewAt(server, target, d, false) : null;
			if (v != null) {
				return v;
			}
		}
		return null;
	}

	/** Open ground near {@code near}: standable, under the open sky all round (no roof, no leaves), or null. */
	private static @Nullable BlockPos openGround(MinecraftServer server, BlockPos near) {
		ServerLevel level = server.overworld();
		for (int r = 6; r <= 48; r += 3) {
			for (int k = 0; k < 16; k++) {
				double a = k * Math.PI / 8;
				BlockPos probe = ground(server, near.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)));
				BlockPos p = dev.voidmc.hearth.npc.entity.FolkSpots.standableNear(level, probe, 1);
				if (p == null || Math.abs(p.getY() - near.getY()) > 3 || level.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.LEAVES)) {
					continue;
				}
				boolean open = true;
				for (int dx = -2; dx <= 2 && open; dx++) {
					for (int dz = -2; dz <= 2 && open; dz++) {
						open = level.canSeeSky(p.offset(dx, 1, dz));
					}
				}
				if (open) {
					return p;
				}
			}
		}
		return null;
	}

	private static double @Nullable [] viewAt(MinecraftServer server, net.minecraft.world.phys.Vec3 target, int dist, boolean open) {
		ServerLevel level = server.overworld();
		BlockPos t = BlockPos.containing(target);
		for (int k = 0; k < 16; k++) {
			double a = k * Math.PI / 8;
			BlockPos probe = t.offset((int) Math.round(Math.cos(a) * dist), 0, (int) Math.round(Math.sin(a) * dist));
			BlockPos stand = dev.voidmc.hearth.npc.entity.FolkSpots.standableNear(level, probe, 1);
			if (stand == null || Math.abs(stand.getY() - t.getY()) > 2 || open && !level.canSeeSky(stand.above(2))) {
				continue;
			}
			net.minecraft.world.phys.Vec3 eye = new net.minecraft.world.phys.Vec3(stand.getX() + 0.5, stand.getY() + 1.62, stand.getZ() + 0.5);
			net.minecraft.world.phys.Vec3 aim = target.add(0, 1.2, 0);
			var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, aim, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
			if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS && hit.getBlockPos().distSqr(t) > SLACK.get()) {
				continue;
			}
			double dx = aim.x - eye.x;
			double dz = aim.z - eye.z;
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
			float pitch = (float) -(Math.atan2(aim.y - eye.y, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
			return new double[]{stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, yaw, pitch};
		}
		return null;
	}

	private static BlockPos ground(MinecraftServer server, BlockPos at) {
		ServerLevel level = server.overworld();
		level.getChunk(at.getX() >> 4, at.getZ() >> 4);
		return new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
	}

	private static void timeTo(TestSingleplayerContext world, int dayTicks) {
		long now = world.getServer().computeOnServer(server -> server.overworld().getOverworldClockTime());
		long delta = Math.floorMod(dayTicks - now, 24000L);
		if (delta > 0) {
			world.getServer().runCommand("time add " + delta);
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void teleport(TestSingleplayerContext world, double x, double y, double z, float yaw, float pitch) {
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
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
		System.out.println("[hearth place] " + line);
	}
}
