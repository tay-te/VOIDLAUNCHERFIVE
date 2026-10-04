package dev.voidmc.hearth.quest.dev;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.quest.QuestEvents;
import dev.voidmc.hearth.quest.gen.QuestGenerator;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.model.QuestType;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.model.SettlementQuests.Happening;
import dev.voidmc.hearth.quest.service.LivingQuests;
import dev.voidmc.hearth.quest.service.QuestItems;
import dev.voidmc.hearth.quest.service.QuestManager;
import dev.voidmc.hearth.quest.service.QuestSpawns;
import dev.voidmc.hearth.quest.service.QuestStore;
import dev.voidmc.hearth.quest.service.QuestTracker;
import dev.voidmc.hearth.quest.template.QuestTemplate;
import dev.voidmc.hearth.quest.template.QuestTemplates;
import dev.voidmc.hearth.quest.world.QuestWorlds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The living world's part of the quest self-test ({@code -Dhearth.dev.quest.selftest=true}): in the sandbox town, as the
 * fake player, a standing contract delivered into the stores; a real fire put out by the bucket brigade; a collapse with
 * props brought and miners dug out; a bandit camp raised and beaten; a market thief beaten into surrender and the stolen
 * purse brought back; a donation paid into a declining town's treasury; a new quarry prospected in real rock; a caravan
 * train on the lead and a robbery reported to the law; the wanted refused work but offered a way to clear their name.
 */
final class LivingSelfTest {
	private final ServerLevel level;
	private final SimpleSettlement town;
	private final FakePlayer player;
	private final BiConsumer<Boolean, String> check;
	private final List<LawService.Crime> reported = new ArrayList<>();
	private final java.util.Set<Long> forcedHere = new java.util.HashSet<>();

	LivingSelfTest(ServerLevel level, SimpleSettlement town, BiConsumer<Boolean, String> check) {
		this.level = level;
		this.town = town;
		this.player = FakePlayer.get(level);
		this.check = check;
	}

	private void check(boolean ok, String what) {
		this.check.accept(ok, what);
	}

	void run() {
		BlockPos center = this.town.center();
		this.player.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
		long start = System.nanoTime();
		contract();
		fire();
		collapse();
		banditCamp();
		thief();
		declining();
		prospect();
		boolean ownLaw = HearthServices.law() == null;
		if (ownLaw) {
			HearthServices.setLaw(new RecordingLaw());
		}
		try {
			caravan(ownLaw);
			if (ownLaw) {
				wanted();
			} else {
				Hearth.LOG.info("[quest selftest] the law module is running: the wanted checks need it to want the fake player, skipped");
			}
		} finally {
			if (ownLaw) {
				HearthServices.setLaw(null);
			}
		}
		Hearth.LOG.info("[quest selftest] living-world checks took {} ms", (System.nanoTime() - start) / 1_000_000L);
		this.player.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
	}

	// ------------------------------------------------------------------ contracts

	private void contract() {
		SettlementQuests board = QuestManager.board(this.level, this.town, true);
		QuestInstance offer = board.offers.stream().filter(q -> q.type == QuestType.CONTRACT).findFirst().orElse(null);
		check(offer != null, "a workshop short of inputs posts a standing contract: "
			+ board.needs.stream().filter(SettlementQuests.StalledNeed::contract).map(n -> n.source() + ":" + n.goods().getSerializedName()).toList());
		if (offer == null || !QuestManager.accept(this.player, this.town, offer.id)) {
			return;
		}
		Objective o = QuestStore.get(this.player).get(offer.id).objectives.getFirst();
		Goods goods = Goods.valueOf(o.data.getStringOr(Objective.Keys.GOODS, "food").toUpperCase(java.util.Locale.ROOT));
		int before = this.town.stockpile().get(goods);
		give(Identifier.parse(o.target), o.count);
		QuestManager.handIn(this.player, this.town, null);
		check(QuestStore.get(this.player).get(offer.id) == null, "the contract is delivered and paid");
		check(this.town.stockpile().get(goods) > before, "the " + goods.getSerializedName() + " reached the workshop's stores: "
			+ before + " → " + this.town.stockpile().get(goods));
	}

	// ------------------------------------------------------------------ disasters

	private void fire() {
		PlotView plot = this.town.plots().iterator().next();
		BlockPos o = plot.spec().origin();
		QuestInstance offer = LivingQuests.happen(this.level, this.town, Happening.FIRE, plot.id(),
			new BlockPos(o.getX() + 4, o.getY(), o.getZ() + 4), -1, 12, "", "", true);
		check(offer != null && offer.objectives.getFirst().kind == ObjectiveKind.EXTINGUISH, "a fire calls the bucket brigade");
		if (offer == null) {
			return;
		}
		List<BlockPos> fires = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			int x = o.getX() + 2 + i * 2;
			int z = o.getZ() + 3;
			int y = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			this.level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.NETHERRACK.defaultBlockState());
			this.level.setBlockAndUpdate(new BlockPos(x, y + 1, z), Blocks.FIRE.defaultBlockState());
			fires.add(new BlockPos(x, y + 1, z));
		}
		check(LivingQuests.heed(this.player, offer.id), "answering the call takes the brigade quest");
		this.player.snapTo(o.getX() + 4.5, o.getY(), o.getZ() + 4.5);
		QuestTracker.checkNow(this.player);
		QuestInstance q = QuestStore.get(this.player).get(offer.id);
		check(q != null && q.objectives.getFirst().data.getIntOr(Objective.Keys.FIRES, 0) >= 3, "the brigade sees the fires burning: "
			+ (q == null ? "?" : q.objectives.getFirst().data.getIntOr(Objective.Keys.FIRES, 0)));
		for (BlockPos f : fires) {
			this.level.setBlockAndUpdate(f, Blocks.AIR.defaultBlockState());
			this.level.setBlockAndUpdate(f.below(), Blocks.DIRT.defaultBlockState());
		}
		int rep = this.town.reputation(this.player.getUUID());
		QuestTracker.checkNow(this.player);
		check(QuestStore.get(this.player).get(offer.id) == null && QuestStore.get(this.player).completed(offer.template) > 0,
			"with the fires out, the brigade quest completes on the spot");
		check(HearthServices.standing() != null || this.town.reputation(this.player.getUUID()) > rep, "fighting the fire raised standing");
		Happening h = QuestStore.board(this.town).happening(SettlementQuests.happeningOf(offer));
		check(h != null && h.resolved(), "the fire is resolved on the town's board");
	}

	private void collapse() {
		PlotView plot = this.town.plots().iterator().next();
		BlockPos at = QuestGenerator.plotPlace(this.town, plot);
		String victims = this.town.residents().stream().filter(r -> r.age != dev.voidmc.hearth.api.AgeStage.CHILD).limit(2)
			.map(dev.voidmc.hearth.api.Resident::fullName).reduce((a, b) -> a + " and " + b).orElse("");
		QuestInstance offer = LivingQuests.happen(this.level, this.town, Happening.COLLAPSE, plot.id(), at, -1, 2, "ore", victims, true);
		check(offer != null && offer.objectives.size() == 2 && offer.objectives.get(1).kind == ObjectiveKind.RESCUE, "a collapse asks for props and a rescue");
		if (offer == null || !QuestManager.accept(this.player, this.town, offer.id)) {
			return;
		}
		Objective props = QuestStore.get(this.player).get(offer.id).objectives.getFirst();
		give(Identifier.parse(props.target), props.count);
		QuestManager.handIn(this.player, this.town, null);
		QuestInstance q = QuestStore.get(this.player).get(offer.id);
		check(q != null && q.objectives.getFirst().done(), "the props are brought");
		Objective rescue = q.objectives.get(1);
		this.player.snapTo(rescue.pos.getX() + 0.5, rescue.pos.getY(), rescue.pos.getZ() + 0.5);
		QuestTracker.checkNow(this.player);
		check(QuestStore.get(this.player).get(offer.id).objectives.get(1).data.getIntOr(Objective.Keys.HELD, 0) == 0, "no digging with empty hands");
		this.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
		int checks = rescue.data.getIntOr(Objective.Keys.HOLD, 40) * 20 / QuestManager.config().trackerInterval() + 2;
		for (int i = 0; i < checks; i++) {
			QuestTracker.checkNow(this.player);
		}
		q = QuestStore.get(this.player).get(offer.id);
		check(q != null && q.status == QuestStatus.READY, "digging " + checks + " checks with a pickaxe frees " + victims);
		QuestManager.handIn(this.player, this.town, null);
		check(QuestStore.get(this.player).get(offer.id) == null, "the rescue is handed in");
		this.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
	}

	private void banditCamp() {
		QuestInstance q = give("law/bandit_camp", null);
		if (q == null) {
			return;
		}
		Objective o = QuestStore.get(this.player).get(q.id).objectives.getFirst();
		standAt(o.pos);
		QuestTracker.checkNow(this.player);
		Objective sprung = QuestStore.get(this.player).get(q.id).objectives.getFirst();
		List<UUID> band = QuestSpawns.members(sprung);
		check(band.size() == sprung.count, "the bandits are raised at their camp: " + band.size() + "/" + sprung.count);
		for (UUID id : band) {
			if (this.level.getEntity(id) instanceof LivingEntity bandit) {
				bandit.hurtServer(this.level, this.level.damageSources().playerAttack(this.player), 1000);
			}
		}
		QuestInstance after = QuestStore.get(this.player).get(q.id);
		check(after != null && after.status == QuestStatus.READY, "beating the band readies the quest");
		QuestManager.handIn(this.player, this.town, null);
		unstand(o.pos);
	}

	private void thief() {
		QuestInstance q = give("law/thief_taking", null);
		if (q == null) {
			return;
		}
		// the test runs by day: the thief comes out now rather than at nightfall
		QuestStore.edit(this.player, pq -> pq.get(q.id).objectives.getFirst().data.remove(Objective.Keys.AT_NIGHT));
		Objective o = QuestStore.get(this.player).get(q.id).objectives.getFirst();
		standAt(o.pos);
		QuestTracker.checkNow(this.player);
		o = QuestStore.get(this.player).get(q.id).objectives.getFirst();
		Entity thief = o.entity == null ? null : this.level.getEntity(o.entity);
		check(thief instanceof LivingEntity, "the market thief shows themself");
		if (thief instanceof LivingEntity living) {
			living.hurtServer(this.level, this.level.damageSources().playerAttack(this.player), 1000);
			check(living.isAlive() && living.entityTags().contains(QuestSpawns.YIELDED), "a killing blow makes the thief yield instead");
		}
		QuestInstance after = QuestStore.get(this.player).get(q.id);
		check(after != null && after.objectives.getFirst().data.getBooleanOr(Objective.Keys.ALIVE, false), "taken alive");
		check(QuestItems.count(this.player, q.id, QuestItems.ROLE_GRANT) == 1, "the stolen purse is recovered");
		QuestTracker.checkNow(this.player);
		check(thief == null || thief.isRemoved(), "the watch takes the thief away");
		QuestManager.handIn(this.player, this.town, null);
		check(QuestStore.get(this.player).get(q.id) == null && QuestItems.count(this.player, q.id, QuestItems.ROLE_GRANT) == 0,
			"the purse goes back and the quest completes");
		unstand(o.pos);
	}

	private void declining() {
		QuestTemplate t = QuestTemplates.get(Hearth.id("disaster/declining_town"));
		SettlementQuests board = QuestStore.board(this.town);
		board.decliningSince = QuestWorlds.day(this.level);
		Happening h = board.addHappening(Happening.DECLINING, QuestWorlds.day(this.level), -1, this.town.center(), -1, 1, "", "the dev harness");
		QuestInstance q = give("disaster/declining_town", h);
		if (t == null || q == null) {
			return;
		}
		Objective pay = QuestStore.get(this.player).get(q.id).objectives.getFirst();
		EconomyService economy = HearthServices.economy();
		if (economy != null) {
			economy.pay(this.player, pay.count);
		} else {
			give(Identifier.withDefaultNamespace("emerald"), pay.count / QuestManager.config().bitsPerEmerald() + 1);
		}
		long treasury = this.town.treasury();
		QuestManager.handIn(this.player, this.town, null);
		check(QuestStore.get(this.player).get(q.id) == null, "the donation completes the quest");
		check(this.town.treasury() >= treasury + pay.count, "the donation reached the treasury: +" + (this.town.treasury() - treasury));
	}

	private void prospect() {
		SettlementQuests board = QuestStore.board(this.town);
		Happening h = board.addHappening(Happening.DEPLETED, QuestWorlds.day(this.level), -1, this.town.center(), -1, 300, "stone", "stone");
		QuestInstance q = give("disaster/new_site", h);
		if (q == null) {
			return;
		}
		List<ResourceSite.Kind> prospected = new ArrayList<>();
		QuestEvents.SITE_PROSPECTED.register((lvl, s, kind, pos, p) -> prospected.add(kind));
		// stand in rock 80 blocks out, in a small cave carved for the test
		BlockPos c = this.town.center();
		int x = c.getX() + 80;
		int z = c.getZ();
		force(new BlockPos(x, 0, z), true);
		int y = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 16;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					this.level.setBlockAndUpdate(new BlockPos(x + dx, y + dy, z + dz), Blocks.STONE.defaultBlockState());
				}
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy <= 1; dy++) {
					this.level.setBlockAndUpdate(new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState());
				}
			}
		}
		this.player.snapTo(x + 0.5, y, z + 0.5);
		QuestTracker.checkNow(this.player);
		QuestInstance after = QuestStore.get(this.player).get(q.id);
		check(after != null && after.status == QuestStatus.READY, "standing in a rock face 80 blocks out finds the new quarry");
		this.player.snapTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
		QuestManager.handIn(this.player, this.town, null);
		check(prospected.contains(ResourceSite.Kind.STONE), "handing in tells the sim where the new stone is");
		force(new BlockPos(x, 0, z), false);
	}

	// ------------------------------------------------------------------ caravans and the law

	private void caravan(boolean recording) {
		Settlement other = QuestWorlds.all(this.level).stream().filter(s -> s.id() != this.town.id()).findFirst().orElse(null);
		if (other == null) {
			check(false, "a second sandbox settlement for the caravan");
			return;
		}
		QuestInstance offer = LivingQuests.happen(this.level, this.town, Happening.CARAVAN_DEPARTED, -1, this.town.center(), other.id(), 96, "wool",
			"wool and leather", false);
		check(offer != null && offer.type == QuestType.CARAVAN, "a departing caravan wants an escort");
		if (offer == null || !QuestManager.accept(this.player, this.town, offer.id)) {
			return;
		}
		QuestInstance q = QuestStore.get(this.player).get(offer.id);
		Objective escort = q.objectives.stream().filter(o -> o.kind == ObjectiveKind.ESCORT).findFirst().orElseThrow();
		List<UUID> train = QuestSpawns.members(escort);
		check(train.size() == 3, "a train of three pack animals is on the lead: " + train.size());
		Entity follower = train.size() > 1 ? this.level.getEntity(train.get(2)) : null;
		check(follower instanceof net.minecraft.world.entity.animal.equine.AbstractChestedHorse h && h.hasChest(), "carrying cargo chests");
		long escrow = q.escrow;
		if (follower instanceof LivingEntity animal) {
			animal.hurtServer(this.level, this.level.damageSources().playerAttack(this.player), 1000);
		}
		if (recording) {
			check(this.reported.stream().anyMatch(c -> c.offence() == LawService.Offence.ROBBERY), "killing a caravan's animal is reported as robbery");
		}
		QuestInstance after = QuestStore.get(this.player).get(offer.id);
		check(after != null && after.escrow < escrow, "a lost follower costs its share of the reward: " + escrow + " → " + (after == null ? -1 : after.escrow));
		QuestManager.abandon(this.player, offer.id);
		check(train.stream().map(this.level::getEntity).allMatch(e -> e == null || e.isRemoved()),
			"abandoning sends the loaded train home (no cargo left on the road)");
	}

	private void wanted() {
		SettlementQuests board = QuestManager.board(this.level, this.town, true);
		QuestInstance honest = board.offers.stream().filter(q -> {
			QuestTemplate t = QuestTemplates.get(q.template);
			return t != null && t.when().wanted() == QuestTemplate.Wanted.REFUSE
				&& QuestManager.lock(this.player, this.town, q, QuestStore.get(this.player)).isEmpty();
		}).findFirst().orElse(null);
		RecordingLaw.WANTED.add(this.player.getUUID());
		try {
			String lock = honest == null ? "" : QuestManager.lock(this.player, this.town, honest, QuestStore.get(this.player));
			check(honest != null && !lock.isEmpty() && !lock.equals(QuestManager.HIDDEN),
				"the wanted are refused honest work: " + (honest == null ? "no offer" : QuestManager.lock(this.player, this.town, honest, QuestStore.get(this.player))));
			QuestInstance clear = give("law/clear_name_patrol", null);
			check(clear != null, "but offered a way to clear their name");
			if (clear != null) {
				QuestManager.abandon(this.player, clear.id);
			}
		} finally {
			RecordingLaw.WANTED.remove(this.player.getUUID());
		}
		QuestInstance cleared = give("law/clear_name_labour", null);
		check(cleared == null, "nobody unwanted is offered to clear a name");
	}

	// ------------------------------------------------------------------ helpers

	private @Nullable QuestInstance give(String template, @Nullable Happening happening) {
		QuestTemplate tmpl = QuestTemplates.get(Hearth.id(template));
		if (tmpl == null) {
			check(false, "template " + template + " is loaded");
			return null;
		}
		SettlementQuests board = QuestStore.board(this.town);
		QuestInstance q = QuestGenerator.bind(tmpl, QuestManager.generationContext(this.level, this.town, board), null, happening);
		if (q == null) {
			check(false, template + " binds in the sandbox");
			return null;
		}
		board.offers.add(q);
		String lock = QuestManager.lock(this.player, this.town, q, QuestStore.get(this.player));
		if (!lock.isEmpty()) {
			board.offers.remove(q);
			return null;
		}
		boolean ok = QuestManager.accept(this.player, this.town, q.id);
		check(ok, "accept " + template + ": " + q.text.title());
		return ok ? q : null;
	}

	private void give(Identifier item, int count) {
		ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(item));
		int max = stack.getMaxStackSize();
		while (count > 0) {
			int n = Math.min(max, count);
			this.player.getInventory().add(new ItemStack(stack.getItem(), n));
			count -= n;
		}
	}

	private void standAt(BlockPos pos) {
		force(pos, true);
		int y = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		this.player.snapTo(pos.getX() + 0.5, y, pos.getZ() + 0.5);
	}

	/**
	 * Holds the 3×3 chunks round a place (a fake player loads none of its own), and lets go of them again — only those
	 * this test forced, never the ones the harness holds round the town.
	 */
	private void force(BlockPos pos, boolean forced) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int cx = (pos.getX() >> 4) + dx;
				int cz = (pos.getZ() >> 4) + dz;
				long key = net.minecraft.world.level.ChunkPos.pack(cx, cz);
				if (forced && !this.level.getForceLoadedChunks().contains(key)) {
					this.level.setChunkForced(cx, cz, true);
					this.forcedHere.add(key);
				} else if (!forced && this.forcedHere.remove(key)) {
					this.level.setChunkForced(cx, cz, false);
				}
			}
		}
	}

	private void unstand(BlockPos pos) {
		force(pos, false);
		BlockPos c = this.town.center();
		this.player.snapTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
	}

	/** A law service for the test, when the law module is not running: records reports, wants whom it is told to. */
	private final class RecordingLaw implements LawService {
		static final java.util.Set<UUID> WANTED = new java.util.HashSet<>();

		@Override
		public @Nullable Owner owner(ServerLevel level, BlockPos pos) {
			return null;
		}

		@Override
		public @Nullable Owner owner(Entity entity) {
			return null;
		}

		@Override
		public boolean isStolen(ItemStack stack) {
			return false;
		}

		@Override
		public @Nullable Owner stolenFrom(ItemStack stack) {
			return null;
		}

		@Override
		public boolean wanted(UUID player, long settlementId) {
			return WANTED.contains(player);
		}

		@Override
		public long bounty(UUID player, long settlementId) {
			return WANTED.contains(player) ? 120 : 0;
		}

		@Override
		public boolean outlaw(UUID player, long settlementId) {
			return false;
		}

		@Override
		public void report(Crime crime) {
			LivingSelfTest.this.reported.add(crime);
		}
	}
}
