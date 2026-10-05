package dev.voidmc.hearth.quest.dev;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.quest.QuestRegistry;
import dev.voidmc.hearth.quest.gen.GenerationContext;
import dev.voidmc.hearth.quest.gen.QuestGenerator;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.PlayerQuests;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.model.QuestType;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.service.QuestItems;
import dev.voidmc.hearth.quest.service.QuestManager;
import dev.voidmc.hearth.quest.service.QuestStore;
import dev.voidmc.hearth.quest.service.QuestTracker;
import dev.voidmc.hearth.quest.service.Standings;
import dev.voidmc.hearth.quest.template.QuestTemplate;
import dev.voidmc.hearth.quest.template.QuestTemplates;
import dev.voidmc.hearth.quest.world.QuestSandbox;
import dev.voidmc.hearth.quest.world.QuestWorlds;
import dev.voidmc.hearth.quest.world.StructureLocator;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * An in-game self-test of the quest loop on a dedicated server, off unless asked for:
 *
 * <pre>
 *   -Dhearth.dev.quest.selftest=true   run it once the server is up (needs no client)
 *   -Dhearth.dev.quest.stop=true       then stop the server
 * </pre>
 *
 * It founds a sandbox town at spawn and, as a fake player, takes and finishes a supply quest (and checks the goods
 * reach the stockpile), a courier run to the neighbouring hamlet, a spider bounty with real kills, a lost keepsake,
 * an escort, and abandons one to see the coin go home; it queues landmark searches and checks what they found; it
 * times board refreshes; and it runs the living world's checks ({@link LivingSelfTest}: contracts, fire, collapse,
 * bandits, a thief, a donation, prospecting, a caravan, the wanted). Every check logs PASS or FAIL. Run with
 * {@code JAVA_TOOL_OPTIONS="-Dhearth.dev.quest.selftest=true -Dhearth.dev.quest.stop=true" ./gradlew runServer}.
 */
public final class QuestDevHarness {
	private static final int START = 60;
	private static final int LOCATE_WAIT = 20 * 40;
	private static @Nullable MinecraftServer server;
	private static int ticks;
	private static int failures;
	private static int passes;
	private static @Nullable SimpleSettlement town;
	private static long locateStart;
	private static @Nullable QuestInstance escortQuest;
	private static @Nullable Settlement escortDest;
	private static final List<Identifier> REQUESTED = List.of(Identifier.parse("expanse:stone_circle"), Identifier.parse("expanse:ruined_watchtower"),
		Identifier.parse("expanse:woodland_lodge"), Identifier.parse("expanse:nomad_camp"), Identifier.parse("expanse:lighthouse"),
		Identifier.parse("minecraft:pillager_outpost"), Identifier.parse("minecraft:desert_pyramid"), Identifier.parse("minecraft:trail_ruins"),
		Identifier.parse("expanse:wisteria_manor"), Identifier.parse("expanse:caravanserai"), Identifier.parse("expanse:wayside_shrine"),
		Identifier.parse("expanse:old_well"));

	private QuestDevHarness() {
	}

	public static void install() {
		if (!Boolean.getBoolean("hearth.dev.quest.selftest")) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (server == null) {
				return;
			}
			ticks++;
			try {
				if (ticks == 1) {
					// A dedicated server with no players keeps no chunks loaded; hold the town's ground for the test.
					forceSpawn(s, true);
				} else if (ticks == START) {
					loop(s);
				} else if (ticks == START + 2 && town != null) {
					new LivingSelfTest(s.overworld(), town, QuestDevHarness::check).run();
				} else if (ticks == START + 5) {
					escortStart(s);
				} else if (ticks == START + 80) {
					escortMove(s);
				} else if (ticks == START + 90) {
					escortCheck(s);
				} else if (ticks == START + LOCATE_WAIT) {
					landmarks(s);
					finish(s);
				}
			} catch (RuntimeException e) {
				Hearth.LOG.error("[quest selftest] crashed", e);
				failures++;
				finish(s);
			}
		});
	}

	private static void check(boolean ok, String what) {
		if (ok) {
			passes++;
			Hearth.LOG.info("[quest selftest] PASS {}", what);
		} else {
			failures++;
			Hearth.LOG.error("[quest selftest] FAIL {}", what);
		}
	}

	private static void loop(MinecraftServer s) {
		ServerLevel level = s.overworld();
		BlockPos spawn = level.getRespawnData().pos();
		BlockPos center = new BlockPos(spawn.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()), spawn.getZ());
		FakePlayer player = FakePlayer.get(level);
		player.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
		check(QuestTemplates.all().size() >= 30, "templates loaded: " + QuestTemplates.all().size());

		SimpleSettlement t = QuestSandbox.create(level, center, Direction.NORTH, Culture.TEMPERATE, 16);
		town = t;
		SettlementQuests board = QuestStore.board(t);
		long supply = board.offers.stream().filter(q -> q.type == QuestType.SUPPLY).count();
		check(supply == 2, "two supply offers for the stalled cottage (stone, wood): " + supply);
		check(board.offers.size() >= 4, "the board is filled: " + board.offers.size() + " offers " + board.offers.stream().map(q -> q.template.getPath()).toList());
		BlockPos front = center.relative(Direction.NORTH, 2);
		BlockPos found = null;
		for (int dy = -4; dy <= 4 && found == null; dy++) {
			BlockPos p = new BlockPos(front.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, front.getX(), front.getZ()) + dy, front.getZ());
			if (level.getBlockState(p).is(QuestRegistry.NOTICE_BOARD)) {
				found = p;
			}
		}
		check(found != null, "a notice board stands in the sandbox square" + (found == null ? "" : " at " + found.toShortString()));
		check(QuestWorlds.at(level, center) == t, "the settlement is found at its centre");

		// Supply: take the stone quest, bring the stone, see it reach the stockpile and the need go away.
		QuestInstance stone = board.offers.stream().filter(q -> q.type == QuestType.SUPPLY
			&& "stone".equals(q.objectives.getFirst().data.getStringOr(Objective.Keys.GOODS, ""))).findFirst().orElse(null);
		check(stone != null, "a supply offer for stone");
		if (stone != null) {
			long treasury = t.treasury();
			check(QuestManager.accept(player, t, stone.id), "accept the stone supply quest");
			PlayerQuests pq = QuestStore.get(player);
			QuestInstance mine = pq.get(stone.id);
			check(mine != null && mine.status == QuestStatus.ACTIVE, "the quest is active in the player's journal");
			check(mine != null && mine.escrow == stone.reward.coin() && t.treasury() == treasury - stone.reward.coin(),
				"the reward is held from the treasury: " + (mine == null ? -1 : mine.escrow));
			check(hasItem(player, QuestRegistry.JOURNAL.getDefaultInstance()),
				"the first quest comes with a journal");
			Objective o = mine.objectives.getFirst();
			give(player, Identifier.parse(o.target), o.count);
			int before = t.stockpile().get(Goods.STONE);
			int units = o.data.getIntOr(Objective.Keys.UNITS, 1);
			QuestManager.handIn(player, t, null);
			pq = QuestStore.get(player);
			check(pq.get(stone.id) == null && pq.completed(stone.template) == 1, "handing in completes the supply quest");
			check(t.stockpile().get(Goods.STONE) == before + o.count * units, "the stone reached the stockpile: +" + (t.stockpile().get(Goods.STONE) - before));
			check(board.needs.stream().noneMatch(n -> n.goods() == Goods.STONE && !n.contract()), "the stone need is met");
			if (dev.voidmc.hearth.api.HearthServices.economy() == null) {
				check(countItem(player, Items.EMERALD) > 0, "paid in emeralds without the economy module: " + countItem(player, Items.EMERALD));
			} else {
				check(dev.voidmc.hearth.api.HearthServices.economy().balance(player) > 0, "paid in coin through the economy module: "
					+ dev.voidmc.hearth.api.HearthServices.economy().balance(player));
			}
			check(Standings.standing(t, player.getUUID()) > 0, "standing rose: " + Standings.standing(t, player.getUUID()));
		}

		// Courier: a parcel to whichever town the quest named (the sandbox's hamlet, or a sim town nearer) and back.
		QuestInstance courier = give(level, t, player, "courier/parcel");
		Settlement neighbour = courier == null ? null : destination(s, player, courier);
		if (courier != null) {
			check(neighbour != null && neighbour.id() != t.id(), "the parcel is bound for another town: " + (neighbour == null ? "none" : neighbour.name()));
		}
		if (courier != null && neighbour != null) {
			check(QuestItems.count(player, courier.id, QuestItems.ROLE_PARCEL) == 1, "the parcel is in the player's pack");
			QuestManager.handIn(player, neighbour, null);
			QuestInstance q = QuestStore.get(player).get(courier.id);
			check(q != null && q.status == QuestStatus.READY && QuestItems.count(player, courier.id, QuestItems.ROLE_PARCEL) == 0,
				"handing the parcel over at " + neighbour.name() + " makes the quest ready");
			QuestManager.handIn(player, t, null);
			check(QuestStore.get(player).get(courier.id) == null, "returning to " + t.name() + " completes the courier quest");
		}

		// Abandon: the coin goes back to the treasury and standing drops.
		QuestInstance honey = give(level, t, player, "gathering/honey");
		if (honey != null) {
			long held = QuestStore.get(player).get(honey.id).escrow;
			long before = t.treasury();
			int rep = Standings.standing(t, player.getUUID());
			QuestManager.abandon(player, honey.id);
			check(t.treasury() == before + held, "abandoning returns " + held + " bits to the treasury");
			check(Standings.standing(t, player.getUUID()) < rep, "abandoning costs standing");
		}

		// Bounty: real spiders, really killed.
		QuestInstance spiders = give(level, t, player, "bounty/spiders");
		if (spiders != null) {
			int need = QuestStore.get(player).get(spiders.id).objectives.getFirst().count;
			for (int i = 0; i < need; i++) {
				Spider spider = EntityTypes.SPIDER.create(level, EntitySpawnReason.COMMAND);
				spider.snapTo(center.getX() + 6 + i, center.getY() + 1, center.getZ() + 6);
				level.addFreshEntity(spider);
				spider.hurtServer(level, level.damageSources().playerAttack(player), 1000);
			}
			QuestInstance q = QuestStore.get(player).get(spiders.id);
			check(q != null && q.status == QuestStatus.READY, "killing " + need + " spiders near town readies the bounty");
			QuestManager.handIn(player, t, null);
			check(QuestStore.get(player).get(spiders.id) == null, "the bounty is handed in");
		}

		// A lost keepsake: walk to where it was dropped, pick it up, bring it back.
		QuestInstance keepsake = give(level, t, player, "personal/lost_keepsake");
		if (keepsake != null) {
			Objective o = QuestStore.get(player).get(keepsake.id).objectives.getFirst();
			level.getChunk(o.pos.getX() >> 4, o.pos.getZ() >> 4);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, o.pos.getX(), o.pos.getZ());
			player.snapTo(o.pos.getX() + 0.5, y, o.pos.getZ() + 0.5);
			QuestTracker.checkNow(player);
			check(QuestItems.count(player, keepsake.id, QuestItems.ROLE_GRANT) == 1, "the keepsake is found at " + o.data.getStringOr(Objective.Keys.TARGET_NAME, "?"));
			player.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
			QuestManager.handIn(player, t, null);
			check(QuestStore.get(player).get(keepsake.id) == null && QuestItems.count(player, keepsake.id, QuestItems.ROLE_GRANT) == 0,
				"the keepsake is returned and the quest completes");
		}

		// Landmarks: queue searches round the town; their results are checked later.
		locateStart = System.nanoTime();
		StructureLocator.request(level, t, REQUESTED, QuestManager.config().exploreSearchRadius(), QuestWorlds.day(level), id -> {
		});

		// Performance: a forced refresh of a 16-folk town's board.
		GenerationContext ctx = QuestManager.generationContext(level, t, new SettlementQuests());
		long start = System.nanoTime();
		int runs = 2000;
		for (int i = 0; i < runs; i++) {
			SettlementQuests fresh = new SettlementQuests();
			fresh.addNeed(new SettlementQuests.StalledNeed(0, Goods.WOOD, 64, 1, "cottage"));
			QuestGenerator.refresh(new GenerationContext(t, fresh, ctx.day() + i, ctx.random(), ctx.others(), ctx.facts(), ctx.config(), ids2 -> {
			}), QuestTemplates.all(), true);
		}
		double micros = (System.nanoTime() - start) / 1000.0 / runs;
		check(micros < 2000, String.format(java.util.Locale.ROOT, "a full board refresh takes %.0f µs", micros));
	}

	/**
	 * Escort, over several ticks: a fake player loads no chunks, so the destination is forced and given time for its
	 * entities to load before the mule is moved there (in play the mule walks with the player through loaded land).
	 */
	private static void escortStart(MinecraftServer s) {
		ServerLevel level = s.overworld();
		SimpleSettlement t = town;
		if (t == null) {
			return;
		}
		FakePlayer player = FakePlayer.get(level);
		QuestInstance q = give(level, t, player, "escort/pack_mule");
		Settlement dest = q == null ? null : destination(s, player, q);
		if (q == null) {
			return;
		}
		check(dest != null && dest.id() != t.id(), "the escort is bound for another town: " + (dest == null ? "none" : dest.name()));
		if (dest == null) {
			return;
		}
		escortQuest = q;
		escortDest = dest;
		level.setChunkForced(dest.center().getX() >> 4, dest.center().getZ() >> 4, true);
	}

	/** The town a courier or escort quest was bound to, from its objective (not whichever other town comes first). */
	private static @Nullable Settlement destination(MinecraftServer s, FakePlayer player, QuestInstance q) {
		QuestInstance mine = QuestStore.get(player).get(q.id);
		if (mine == null) {
			return null;
		}
		return mine.objectives.stream().filter(o -> o.kind == ObjectiveKind.COURIER || o.kind == ObjectiveKind.ESCORT)
			.map(o -> QuestWorlds.byId(s, o.settlement)).filter(java.util.Objects::nonNull).findFirst().orElse(null);
	}

	private static void escortMove(MinecraftServer s) {
		ServerLevel level = s.overworld();
		FakePlayer player = FakePlayer.get(level);
		if (escortQuest == null || escortDest == null) {
			return;
		}
		Objective o = QuestStore.get(player).get(escortQuest.id).objectives.getFirst();
		Entity mule = o.entity == null ? null : level.getEntity(o.entity);
		if (mule == null && town != null) {
			List<net.minecraft.world.entity.animal.equine.Mule> near = level.getEntitiesOfClass(net.minecraft.world.entity.animal.equine.Mule.class,
				new net.minecraft.world.phys.AABB(town.center()).inflate(64));
			Hearth.LOG.info("[quest selftest] escort entity {} not found; data {}; mules near town: {}", o.entity, o.data, near);
		}
		check(mule != null && mule.getType() == EntityTypes.MULE, "a pack mule waits on the lead");
		if (mule != null) {
			BlockPos dest = escortDest.center();
			mule.teleportTo(dest.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dest.getX(), dest.getZ()), dest.getZ() + 0.5);
		}
	}

	private static void escortCheck(MinecraftServer s) {
		ServerLevel level = s.overworld();
		FakePlayer player = FakePlayer.get(level);
		SimpleSettlement t = town;
		if (escortQuest == null || escortDest == null || t == null) {
			return;
		}
		QuestTracker.checkNow(player);
		QuestInstance q = QuestStore.get(player).get(escortQuest.id);
		if (q == null || q.status != QuestStatus.READY) {
			Objective o = q == null ? null : q.objectives.getFirst();
			Entity mule = o == null || o.entity == null ? null : level.getEntity(o.entity);
			Hearth.LOG.info("[quest selftest] escort: quest {} status {}, objective {}, mule {}; history {}", escortQuest.id,
				q == null ? "gone" : q.status, o == null ? "-" : o.data, mule == null ? "missing" : mule.blockPosition().toShortString(),
				QuestStore.get(player).history.stream().limit(3).map(r -> r.title() + ":" + r.outcome()).toList());
		}
		check(q != null && q.status == QuestStatus.READY, "the mule's arrival at " + escortDest.name() + " readies the escort");
		QuestManager.handIn(player, t, null);
		check(QuestStore.get(player).get(escortQuest.id) == null, "the escort is handed in");
		level.setChunkForced(escortDest.center().getX() >> 4, escortDest.center().getZ() >> 4, false);
	}

	private static void landmarks(MinecraftServer s) {
		SimpleSettlement t = town;
		if (t == null) {
			return;
		}
		SettlementQuests board = QuestStore.board(t);
		int found = 0;
		for (SettlementQuests.LocatedTarget target : board.targets) {
			if (target.found()) {
				found++;
				double d = Math.sqrt(t.center().distSqr(new BlockPos(target.pos().getX(), t.center().getY(), target.pos().getZ())));
				Hearth.LOG.info("[quest selftest] located {} at {} ({} blocks), bounds {} to {}", target.structure(), target.pos().toShortString(), (int) d,
					target.min().toShortString(), target.max().toShortString());
			} else {
				Hearth.LOG.info("[quest selftest] {} not within {} blocks", target.structure(), QuestManager.config().exploreSearchRadius());
			}
		}
		long answered = REQUESTED.stream().filter(id -> board.target(id) != null).count();
		check(answered == REQUESTED.size(), "every landmark search finished within " + LOCATE_WAIT / 20 + " s: " + answered + "/" + REQUESTED.size());
		check(found > 0, found + " landmarks located round the sandbox town");
		QuestTemplate old = QuestTemplates.get(Hearth.id("exploration/old_places"));
		QuestTemplate ruins = QuestTemplates.get(Hearth.id("exploration/vanilla_ruins"));
		ServerLevel level = s.overworld();
		QuestInstance q = null;
		for (QuestTemplate tmpl : new QuestTemplate[] {old, ruins}) {
			if (tmpl != null && q == null) {
				q = QuestGenerator.bind(tmpl, QuestManager.generationContext(level, t, board), null);
			}
		}
		check(found == 0 || q != null, "an exploration quest binds to a located landmark: " + (q == null ? "none" : q.text.title()));
		Hearth.LOG.info("[quest selftest] landmark searches took about {} s of background time", (System.nanoTime() - locateStart) / 1_000_000_000L);
	}

	private static void forceSpawn(MinecraftServer s, boolean forced) {
		ServerLevel level = s.overworld();
		BlockPos spawn = level.getRespawnData().pos();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				level.setChunkForced((spawn.getX() >> 4) + dx, (spawn.getZ() >> 4) + dz, forced);
			}
		}
	}

	private static void finish(MinecraftServer s) {
		forceSpawn(s, false);
		Hearth.LOG.info("[quest selftest] done: {} passed, {} failed", passes, failures);
		if (Boolean.getBoolean("hearth.dev.quest.stop")) {
			s.halt(false);
		}
	}

	/** Binds a template here and has the player take it. */
	private static @Nullable QuestInstance give(ServerLevel level, SimpleSettlement t, FakePlayer player, String template) {
		QuestTemplate tmpl = QuestTemplates.get(Hearth.id(template));
		if (tmpl == null) {
			check(false, "template " + template + " is loaded");
			return null;
		}
		SettlementQuests board = QuestStore.board(t);
		QuestInstance q = QuestGenerator.bind(tmpl, QuestManager.generationContext(level, t, board), null);
		if (q == null) {
			check(false, template + " binds in the sandbox");
			return null;
		}
		board.offers.add(q);
		boolean ok = QuestManager.accept(player, t, q.id);
		check(ok, "accept " + template + ": " + q.text.title());
		return ok ? q : null;
	}

	private static void give(FakePlayer player, Identifier item, int count) {
		ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(item));
		int max = stack.getMaxStackSize();
		while (count > 0) {
			int n = Math.min(max, count);
			player.getInventory().add(new ItemStack(stack.getItem(), n));
			count -= n;
		}
	}

	private static boolean hasItem(FakePlayer player, ItemStack wanted) {
		return countItem(player, wanted.getItem()) > 0;
	}

	private static int countItem(FakePlayer player, net.minecraft.world.item.Item item) {
		int n = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}
}
