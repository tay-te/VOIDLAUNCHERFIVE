package dev.voidmc.hearth.quest.gen;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.quest.QuestText;
import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestReward;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.model.QuestType;
import dev.voidmc.hearth.quest.model.SettlementQuests;
import dev.voidmc.hearth.quest.model.SettlementQuests.LocatedTarget;
import dev.voidmc.hearth.quest.model.SettlementQuests.StalledNeed;
import dev.voidmc.hearth.quest.template.ObjectiveSpec;
import dev.voidmc.hearth.quest.template.QuestTemplate;
import dev.voidmc.hearth.quest.template.TemplateValidator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * Makes a settlement's offers from its needs and the quest templates. Cheap and event-driven: it runs when a board is
 * looked at, a resident is spoken to or a construction stalls, never per tick, and it reads only the settlement's
 * record, its quest data and what the game already knows (located landmarks, other settlements). Pure apart from the
 * {@link GenerationContext}, so it is unit tested without a world.
 *
 * <p>Order of business on a refresh: drop offers that expired or whose need went away; post supply quests for stalled
 * builds (the town's most pressing need); post a bounty if monsters press on the town at night; then fill the board
 * with a weighted mix of the other kinds, varied by type.
 */
public final class QuestGenerator {
	/** Coin multiplier by settlement tier: a city pays better than a camp. */
	private static final double[] TIER_PAY = {0.6, 0.8, 1.0, 1.3, 1.7};
	/** A negative search result is retried after this many days. */
	private static final int RELOCATE_DAYS = 30;

	private QuestGenerator() {
	}

	/**
	 * Refreshes the board if it is due (or {@code force}): expiry, needs, bounty, then fill. Returns the number of offers
	 * added.
	 */
	public static int refresh(GenerationContext ctx, Collection<QuestTemplate> templates, boolean force) {
		SettlementQuests data = ctx.data();
		long day = ctx.day();
		boolean first = data.refreshedDay == Long.MIN_VALUE;
		if (!force && !first && day - data.refreshedDay < ctx.config().refreshDays()) {
			return 0;
		}
		long elapsed = first ? 0 : Math.max(0, day - data.refreshedDay);
		for (long d = 0; d < Math.min(elapsed, 8); d++) {
			data.pressure /= 2;
		}
		data.offers.removeIf(q -> q.timing.expiresDay() < day);
		data.offers.removeIf(q -> q.type == QuestType.SUPPLY && q.takenBy.isEmpty() && !needStillOpen(data, q));
		data.cooldowns.values().removeIf(until -> until <= day);
		int added = postNeeds(ctx, templates);
		added += postBounty(ctx, templates);
		added += fill(ctx, templates);
		data.refreshedDay = day;
		return added;
	}

	/**
	 * Posts supply quests for stalled builds that have none yet, up to the configured number. Called on refresh and right
	 * away when a construction stalls, so a town short of stone asks for stone the same day.
	 */
	public static int postNeeds(GenerationContext ctx, Collection<QuestTemplate> templates) {
		SettlementQuests data = ctx.data();
		int supply = (int) data.offers.stream().filter(q -> q.type == QuestType.SUPPLY && q.takenBy.isEmpty()).count();
		int added = 0;
		for (StalledNeed need : List.copyOf(data.needs)) {
			if (supply >= ctx.config().maxSupplyOffers()) {
				break;
			}
			if (hasSupplyOffer(data, need)) {
				continue;
			}
			List<QuestTemplate> pool = eligible(ctx, templates, QuestType.Trigger.STALLED);
			pool.removeIf(t -> !t.when().goods().isEmpty() && !t.when().goods().contains(need.goods()));
			QuestInstance q = pickAndBind(ctx, pool, need);
			if (q != null) {
				data.offers.add(q);
				supply++;
				added++;
			}
		}
		return added;
	}

	private static int postBounty(GenerationContext ctx, Collection<QuestTemplate> templates) {
		SettlementQuests data = ctx.data();
		if (data.pressure < ctx.config().pressureThreshold()) {
			return 0;
		}
		for (QuestInstance q : data.offers) {
			if (q.type == QuestType.BOUNTY && q.takenBy.isEmpty()) {
				return 0;
			}
		}
		QuestInstance q = pickAndBind(ctx, eligible(ctx, templates, QuestType.Trigger.PRESSURE), null);
		if (q == null) {
			return 0;
		}
		data.offers.add(q);
		// The town has asked for help: the pressure is spent until monsters build it up again.
		data.pressure = 0;
		return 1;
	}

	private static int fill(GenerationContext ctx, Collection<QuestTemplate> templates) {
		SettlementQuests data = ctx.data();
		int slots = ctx.config().offersPerBoard() - (int) data.offers.stream().filter(q -> q.takenBy.isEmpty()).count();
		if (slots <= 0) {
			return 0;
		}
		List<QuestTemplate> pool = eligible(ctx, templates, QuestType.Trigger.NONE);
		pool.removeIf(t -> data.cooldowns.getOrDefault(t.id(), Long.MIN_VALUE) > ctx.day());
		pool.removeIf(t -> data.offers.stream().anyMatch(q -> q.template.equals(t.id())));
		int added = 0;
		int attempts = slots * 4;
		while (added < slots && attempts-- > 0 && !pool.isEmpty()) {
			QuestTemplate t = weighted(ctx, pool);
			pool.remove(t);
			QuestInstance q = bind(t, ctx, null);
			if (q != null) {
				data.offers.add(q);
				added++;
			}
		}
		return added;
	}

	/** Templates of a trigger this settlement can offer at all (tier and culture). */
	static List<QuestTemplate> eligible(GenerationContext ctx, Collection<QuestTemplate> templates, QuestType.Trigger trigger) {
		Settlement s = ctx.settlement();
		SettlementTier tier = s.tier();
		List<QuestTemplate> out = new ArrayList<>();
		for (QuestTemplate t : templates) {
			if (t.when().trigger() == trigger && t.when().allowsTier(tier) && t.weightFor(s.culture()) > 0) {
				out.add(t);
			}
		}
		return out;
	}

	private static @Nullable QuestInstance pickAndBind(GenerationContext ctx, List<QuestTemplate> pool, @Nullable StalledNeed need) {
		List<QuestTemplate> left = new ArrayList<>(pool);
		while (!left.isEmpty()) {
			QuestTemplate t = weighted(ctx, left);
			left.remove(t);
			QuestInstance q = bind(t, ctx, need);
			if (q != null) {
				return q;
			}
		}
		return null;
	}

	/** Weighted by template weight and culture, damped for types already on the board so it shows a mix. */
	private static QuestTemplate weighted(GenerationContext ctx, List<QuestTemplate> pool) {
		double total = 0;
		double[] w = new double[pool.size()];
		for (int i = 0; i < pool.size(); i++) {
			QuestTemplate t = pool.get(i);
			double wi = t.weightFor(ctx.settlement().culture());
			for (QuestInstance q : ctx.data().offers) {
				if (q.type == t.type()) {
					wi *= 0.35;
				}
			}
			w[i] = Math.max(1e-6, wi);
			total += w[i];
		}
		double r = ctx.random().nextDouble() * total;
		for (int i = 0; i < w.length; i++) {
			r -= w[i];
			if (r <= 0) {
				return pool.get(i);
			}
		}
		return pool.getLast();
	}

	private static boolean hasSupplyOffer(SettlementQuests data, StalledNeed need) {
		for (QuestInstance q : data.offers) {
			if (q.type != QuestType.SUPPLY || !q.takenBy.isEmpty()) {
				continue;
			}
			for (Objective o : q.objectives) {
				if (o.kind == ObjectiveKind.DELIVER && o.data.getIntOr(Objective.Keys.PLOT, -1) == need.plot()
					&& need.goods().getSerializedName().equals(o.data.getStringOr(Objective.Keys.GOODS, ""))) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean needStillOpen(SettlementQuests data, QuestInstance q) {
		for (Objective o : q.objectives) {
			if (o.kind != ObjectiveKind.DELIVER || !o.data.contains(Objective.Keys.GOODS)) {
				continue;
			}
			int plot = o.data.getIntOr(Objective.Keys.PLOT, -1);
			String goods = o.data.getStringOr(Objective.Keys.GOODS, "");
			for (StalledNeed n : data.needs) {
				if (n.plot() == plot && n.goods().getSerializedName().equals(goods)) {
					return true;
				}
			}
			return false;
		}
		return true;
	}

	// ------------------------------------------------------------------ binding

	/**
	 * Makes one quest from a template for this settlement, or null if it cannot be bound here and now (no other town to
	 * carry a parcel to, no landmark located yet, no resident to give a personal request).
	 */
	public static @Nullable QuestInstance bind(QuestTemplate t, GenerationContext ctx, @Nullable StalledNeed need) {
		Settlement s = ctx.settlement();
		RandomSource random = ctx.random();
		if (t.when().trigger() == QuestType.Trigger.STALLED && need == null) {
			return null;
		}
		Map<String, String> vars = new HashMap<>();
		Map<String, Integer> ints = new HashMap<>();
		vars.put("settlement", s.name());
		vars.put("culture", s.culture().getSerializedName());
		vars.put("tier", s.tier().getSerializedName());
		ints.put("population", s.population());
		ints.put("pressure", Math.max(1, ctx.data().pressure));

		Resident giverRes = chooseGiver(t, s, random);
		if (giverRes == null && (t.type() == QuestType.PERSONAL || needsGiver(t))) {
			return null;
		}
		QuestInstance.Giver giver = giverRes == null
			? new QuestInstance.Giver(Optional.empty(), council(s), "")
			: new QuestInstance.Giver(Optional.of(giverRes.id), giverRes.fullName(), giverRes.job.getSerializedName());
		vars.put("giver", giver.name());
		vars.put("giver_job", giverRes == null ? "council" : giverRes.job.getSerializedName().replace('_', ' '));
		vars.put("days", Integer.toString(t.deadlineDays()));
		if (need != null) {
			vars.put("place", need.place().isEmpty() ? "new building" : need.place());
			vars.put("goods", need.goods().getSerializedName());
		}

		List<Objective> objectives = new ArrayList<>();
		List<Map<String, String>> objectiveVars = new ArrayList<>();
		for (ObjectiveSpec spec : t.objectives()) {
			Map<String, String> ov = new HashMap<>();
			Objective o = bindObjective(spec, ctx, need, giverRes, ov, ints);
			if (o == null) {
				return null;
			}
			objectives.add(o);
			objectiveVars.add(ov);
			for (Map.Entry<String, String> e : ov.entrySet()) {
				vars.putIfAbsent(e.getKey(), e.getValue());
			}
		}
		// Labels and granted items' names are filled once every objective's values are known.
		for (int i = 0; i < objectives.size(); i++) {
			Objective o = objectives.get(i);
			Map<String, String> local = new HashMap<>(vars);
			local.putAll(objectiveVars.get(i));
			String label = t.objectives().get(i).label();
			o.label = QuestText.fill(label.isEmpty() ? defaultLabel(o, objectiveVars.get(i)) : label, local);
			if (o.data.contains(Objective.Keys.GRANT_NAME)) {
				o.data.putString(Objective.Keys.GRANT_NAME, QuestText.fill(o.data.getStringOr(Objective.Keys.GRANT_NAME, ""), local));
				o.data.putString(Objective.Keys.GRANT_LORE, QuestText.fill(o.data.getStringOr(Objective.Keys.GRANT_LORE, ""), local));
			}
		}

		QuestReward reward = reward(t, ctx, objectives);
		QuestTemplate.Text tt = t.text();
		int variant = random.nextInt(tt.title().size());
		QuestInstance.Text text = new QuestInstance.Text(
			QuestText.fill(pick(tt.title(), variant), vars),
			QuestText.fill(pick(tt.summary(), variant), vars),
			QuestText.fill(pick(tt.offer(), variant), vars),
			QuestText.fill(pick(tt.progress(), variant), vars),
			QuestText.fill(pick(tt.complete(), variant), vars));
		boolean exclusive = t.type().exclusive() || objectives.stream().anyMatch(o -> o.kind == ObjectiveKind.SLAY
			|| o.kind == ObjectiveKind.ESCORT || o.kind == ObjectiveKind.SCOUT);
		QuestInstance.Timing timing = new QuestInstance.Timing(ctx.day(), ctx.day() + ctx.config().offerLifetimeDays(), t.deadlineDays(), 0L, 0L);
		QuestInstance.Rules rules = new QuestInstance.Rules(t.sequential(), t.turnIn(), t.when().minReputation(), exclusive);
		ctx.data().issued++;
		if (t.cooldownDays() > 0) {
			ctx.data().cooldowns.put(t.id(), ctx.day() + t.cooldownDays());
		}
		return new QuestInstance(new UUID(random.nextLong(), random.nextLong()), t.id(), t.type(), QuestStatus.OFFERED, text, giver, s.id(),
			s.name(), s.center(), objectives, reward, 0L, timing, rules, List.of());
	}

	private static boolean needsGiver(QuestTemplate t) {
		for (ObjectiveSpec o : t.objectives()) {
			if (o.items().contains(TemplateValidator.GIFT)) {
				return true;
			}
		}
		return false;
	}

	/** A resident of the template's trades, else (unless council-only) any adult some of the time; null for the council. */
	static @Nullable Resident chooseGiver(QuestTemplate t, Settlement s, RandomSource random) {
		if (t.councilOnly()) {
			return null;
		}
		List<Resident> byJob = new ArrayList<>();
		List<Resident> adults = new ArrayList<>();
		for (Resident r : s.residents()) {
			if (r.age == AgeStage.CHILD && t.type() != QuestType.PERSONAL) {
				continue;
			}
			adults.add(r);
			if (t.giverJobs().contains(r.job)) {
				byJob.add(r);
			}
		}
		if (!byJob.isEmpty()) {
			return byJob.get(random.nextInt(byJob.size()));
		}
		if (adults.isEmpty()) {
			return null;
		}
		boolean personal = t.type() == QuestType.PERSONAL || needsGiver(t);
		return personal || random.nextInt(10) < 6 ? adults.get(random.nextInt(adults.size())) : null;
	}

	/** Who speaks for a settlement without a particular giver. */
	public static String council(Settlement s) {
		return switch (s.tier()) {
			case CAMP -> "the camp elders";
			case HAMLET -> "the hamlet elders";
			case VILLAGE -> "the village council";
			case TOWN -> "the town council";
			case CITY -> "the city council";
		};
	}

	private static @Nullable Objective bindObjective(ObjectiveSpec spec, GenerationContext ctx, @Nullable StalledNeed need, @Nullable Resident giver,
		Map<String, String> ov, Map<String, Integer> ints) {
		Settlement s = ctx.settlement();
		RandomSource random = ctx.random();
		GameFacts facts = ctx.facts();
		CompoundTag data = new CompoundTag();
		if (spec.grant() != null) {
			data.putString(Objective.Keys.GRANT_ITEM, spec.grant().item());
			data.putString(Objective.Keys.GRANT_NAME, spec.grant().name());
			data.putString(Objective.Keys.GRANT_LORE, spec.grant().lore());
			data.putBoolean(Objective.Keys.GRANT_REQUIRED, spec.grant().required());
		}
		BlockPos center = s.center();
		switch (spec.kind()) {
			case DELIVER -> {
				List<String> choices = new ArrayList<>(spec.items());
				String choice = choices.get(random.nextInt(choices.size()));
				int count;
				String target;
				String name;
				if (choice.equals(TemplateValidator.GOODS_ITEM)) {
					if (need == null) {
						return null;
					}
					GoodsItems.Choice ch = GoodsItems.pick(s.culture(), need.goods(), random, facts::itemExists);
					int items = Mth.positiveCeilDiv(need.missing(), ch.units());
					ints.put("missing", items);
					count = spec.count().resolve(random, ints);
					count = Math.min(count, supplyCap(ctx, ch.item()));
					target = ch.item().toString();
					name = facts.itemName(ch.item());
					data.putString(Objective.Keys.GOODS, need.goods().getSerializedName());
					data.putInt(Objective.Keys.UNITS, ch.units());
					data.putInt(Objective.Keys.PLOT, need.plot());
				} else if (choice.equals(TemplateValidator.GIFT)) {
					if (giver == null) {
						return null;
					}
					GiftPreferences.Gift gift = GiftPreferences.pick(giver, random, facts::itemExists);
					count = spec.count().isVariable() || spec.count().max() <= 1 ? gift.count() : spec.count().resolve(random, ints);
					target = gift.item().toString();
					name = facts.itemName(gift.item());
				} else {
					// Pick among the listed items that exist (an Expanse item drops out without Expanse).
					choices.removeIf(c -> c.startsWith("$") || (!c.startsWith("#") && !facts.itemExists(Identifier.parse(c))));
					if (choices.isEmpty()) {
						return null;
					}
					choice = choices.get(random.nextInt(choices.size()));
					count = spec.count().resolve(random, ints);
					target = choice;
					if (choice.startsWith("#")) {
						name = spec.targetName().isEmpty() ? GameFacts.humanize(Identifier.parse(choice.substring(1)).getPath()) : spec.targetName();
					} else {
						Identifier id = Identifier.parse(choice);
						name = facts.itemName(id);
						count = Math.min(count, facts.maxStackSize(id) * 9);
						// Goods the town builds with still go into its stores when brought.
						GoodsItems.Choice ch = GoodsItems.find(id);
						Goods goods = ch == null ? null : GoodsItems.goodsOf(ch.item());
						if (goods != null) {
							data.putString(Objective.Keys.GOODS, goods.getSerializedName());
							data.putInt(Objective.Keys.UNITS, ch.units());
						}
					}
				}
				ov.put("count", Integer.toString(count));
				ov.put("item", name);
				data.putString(Objective.Keys.TARGET_NAME, name);
				return new Objective(ObjectiveKind.DELIVER, "", target, count, 0, null, 0, -1L, null, data);
			}
			case KILL -> {
				String entity = spec.entities().get(random.nextInt(spec.entities().size()));
				int count = spec.count().resolve(random, ints);
				String mob = entity.equals(TemplateValidator.MONSTER) ? "monsters" : entity.startsWith("#")
					? GameFacts.humanize(entity.substring(entity.indexOf(':') + 1))
					: facts.entityName(Identifier.parse(entity));
				if (!spec.targetName().isEmpty()) {
					mob = spec.targetName();
				}
				int radius = spec.radius() > 0 ? spec.radius() : settlementRadius(s) + 32;
				if (spec.night()) {
					data.putBoolean(Objective.Keys.NIGHT, true);
				}
				data.putString(Objective.Keys.TARGET_NAME, mob);
				ov.put("count", Integer.toString(count));
				ov.put("mob", mob);
				return new Objective(ObjectiveKind.KILL, "", entity, count, 0, center, radius, s.id(), null, data);
			}
			case SLAY -> {
				String entity = spec.entities().get(random.nextInt(spec.entities().size()));
				if (!facts.entityExists(Identifier.parse(entity))) {
					return null;
				}
				String name = spec.names().get(random.nextInt(spec.names().size()));
				BlockPos lair = ring(center, spec.minDistance(), spec.maxDistance(), random);
				data.putString(Objective.Keys.NAME, name);
				data.putDouble(Objective.Keys.HEALTH, Math.max(1.0, spec.health()));
				data.putString(Objective.Keys.TARGET_NAME, name);
				ov.put("name", name);
				ov.put("mob", facts.entityName(Identifier.parse(entity)));
				putBearing(ov, center, lair);
				return new Objective(ObjectiveKind.SLAY, "", entity, 1, 0, lair, spec.radius() > 0 ? spec.radius() : 40, s.id(), null, data);
			}
			case REACH -> {
				BlockPos place = ring(center, spec.minDistance(), spec.maxDistance(), random);
				String name = !spec.names().isEmpty() ? spec.names().get(random.nextInt(spec.names().size()))
					: spec.targetName().isEmpty() ? "the place" : spec.targetName();
				data.putString(Objective.Keys.TARGET_NAME, name);
				ov.put("target", name);
				putBearing(ov, center, place);
				return new Objective(ObjectiveKind.REACH, "", "", 1, 0, place, spec.radius() > 0 ? spec.radius() : 5, s.id(), null, data);
			}
			case EXPLORE -> {
				LocatedTarget target = chooseTarget(spec, ctx);
				if (target == null) {
					return null;
				}
				String name = spec.targetName().isEmpty() ? facts.structureName(target.structure()) : spec.targetName();
				data.putString(Objective.Keys.TARGET_NAME, name);
				data.putLong(Objective.Keys.BOX_MIN, target.min().asLong());
				data.putLong(Objective.Keys.BOX_MAX, target.max().asLong());
				ov.put("target", name);
				putBearing(ov, center, target.pos());
				return new Objective(ObjectiveKind.EXPLORE, "", target.structure().toString(), 1, 0, target.pos(),
					spec.radius() > 0 ? spec.radius() : 8, -1L, null, data);
			}
			case BIOME -> {
				String name = spec.targetName().isEmpty() ? facts.biomeName(Identifier.parse(spec.biomes().getFirst())) : spec.targetName();
				data.putString(Objective.Keys.TARGET_NAME, name);
				ov.put("target", name);
				return new Objective(ObjectiveKind.BIOME, "", String.join(",", spec.biomes()), 1, 0, null, 0, -1L, null, data);
			}
			case COURIER, ESCORT, TALK -> {
				Settlement dest = chooseDestination(ctx);
				if (dest == null) {
					return null;
				}
				Resident recipient = null;
				if (spec.recipient() || spec.kind() == ObjectiveKind.TALK) {
					recipient = chooseRecipient(dest, random);
					if (recipient == null) {
						return null;
					}
					data.putString(Objective.Keys.RECIPIENT, recipient.fullName());
					ov.put("recipient", recipient.fullName());
				} else {
					ov.put("recipient", council(dest));
				}
				data.putString(Objective.Keys.TARGET_NAME, dest.name());
				ov.put("target", dest.name());
				putBearing(ov, center, dest.center());
				String target = "";
				if (spec.kind() == ObjectiveKind.ESCORT) {
					List<String> kinds = spec.entities().isEmpty() ? List.of("minecraft:mule") : spec.entities();
					target = kinds.get(random.nextInt(kinds.size()));
					if (!facts.entityExists(Identifier.parse(target))) {
						return null;
					}
					List<String> names = spec.names().isEmpty() ? List.of("Bramble", "Clover", "Old Tom", "Pepper", "Juniper") : spec.names();
					String name = names.get(random.nextInt(names.size()));
					data.putString(Objective.Keys.NAME, name);
					ov.put("name", name);
					ov.put("mob", facts.entityName(Identifier.parse(target)));
				}
				return new Objective(spec.kind(), "", target, 1, 0, dest.center(), 0, dest.id(), recipient == null ? null : recipient.id, data);
			}
			case SCOUT -> {
				int min = spec.minDistance();
				int max = spec.maxDistance();
				int clearance = spec.clearance() > 0 ? spec.clearance() : ctx.config().scoutClearance();
				data.putInt(Objective.Keys.MIN_DISTANCE, min);
				data.putInt(Objective.Keys.MAX_DISTANCE, max);
				data.putInt(Objective.Keys.CLEARANCE, clearance);
				String band = String.format(Locale.ROOT, "%,d to %,d blocks", min, max);
				data.putString(Objective.Keys.TARGET_NAME, band);
				ov.put("distance", band);
				ov.put("target", "a site for a hamlet");
				return new Objective(ObjectiveKind.SCOUT, "", "", 1, 0, center, 0, s.id(), null, data);
			}
		}
		return null;
	}

	private static String defaultLabel(Objective o, Map<String, String> ov) {
		return switch (o.kind) {
			case DELIVER -> "Bring {count} × {item}";
			case KILL -> o.data.getBooleanOr(Objective.Keys.NIGHT, false) ? "Kill {count} {mob} near {settlement} at night" : "Kill {count} {mob} near {settlement}";
			case SLAY -> "Slay {name}, {direction} of {settlement}";
			case REACH -> "Find {target}, {direction} of {settlement}";
			case EXPLORE -> "Find {target}, {direction} of {settlement}";
			case BIOME -> "Reach {target}";
			case COURIER -> o.entity != null ? "Deliver the parcel to {recipient} in {target}" : "Deliver the parcel to {target}";
			case ESCORT -> "Lead {name} the {mob} to {target}";
			case SCOUT -> "Find a site for a new hamlet {distance} from {settlement}";
			case TALK -> "Speak with {recipient} in {target}";
		};
	}

	private static int supplyCap(GenerationContext ctx, Identifier item) {
		int stack = ctx.facts().maxStackSize(item);
		return stack <= 1 ? Math.min(6, ctx.config().supplyMaxItems()) : ctx.config().supplyMaxItems();
	}

	/** About half the diagonal of the settlement's bounds: "near the settlement" for kill quests. */
	static int settlementRadius(Settlement s) {
		var b = s.bounds();
		int r = (int) (Math.sqrt((double) b.getXSpan() * b.getXSpan() + (double) b.getZSpan() * b.getZSpan()) / 2);
		return Math.max(48, Math.min(r, 256));
	}

	private static BlockPos ring(BlockPos center, int min, int max, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2;
		int dist = min >= max ? max : Mth.nextInt(random, min, max);
		return new BlockPos(center.getX() + (int) Math.round(Math.cos(angle) * dist), Objective.SURFACE_Y,
			center.getZ() + (int) Math.round(Math.sin(angle) * dist));
	}

	private static void putBearing(Map<String, String> ov, BlockPos from, BlockPos to) {
		ov.put("direction", QuestText.direction(from.getX(), from.getZ(), to.getX(), to.getZ()));
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		ov.putIfAbsent("distance", QuestText.distance(Math.sqrt(dx * dx + dz * dz)));
	}

	/**
	 * The nearest located target matching the spec that no offer here already points at. When none is known, asks the
	 * game to look (off the hot path) for the structures not yet searched, and the quest is not made this time.
	 */
	private static @Nullable LocatedTarget chooseTarget(ObjectiveSpec spec, GenerationContext ctx) {
		SettlementQuests data = ctx.data();
		BlockPos center = ctx.settlement().center();
		List<Identifier> wanted = new ArrayList<>();
		for (String s : spec.structures()) {
			Identifier id = Identifier.tryParse(s);
			if (id != null) {
				wanted.add(id);
			}
		}
		LocatedTarget best = null;
		double bestDist = Double.MAX_VALUE;
		List<Identifier> toSearch = new ArrayList<>();
		long maxSq = (long) ctx.config().exploreSearchRadius() * ctx.config().exploreSearchRadius();
		for (Identifier id : wanted) {
			LocatedTarget t = data.target(id);
			if (t == null || (!t.found() && ctx.day() - t.day() >= RELOCATE_DAYS)) {
				toSearch.add(id);
				continue;
			}
			if (!t.found() || alreadyOffered(data, t.structure())) {
				continue;
			}
			double d = horizontalDistSq(center, t.pos());
			if (d <= maxSq && d < bestDist) {
				bestDist = d;
				best = t;
			}
		}
		if (!toSearch.isEmpty()) {
			ctx.locateRequests().accept(toSearch);
		}
		return best;
	}

	private static boolean alreadyOffered(SettlementQuests data, Identifier structure) {
		for (QuestInstance q : data.offers) {
			for (Objective o : q.objectives) {
				if (o.kind == ObjectiveKind.EXPLORE && o.target.equals(structure.toString())) {
					return true;
				}
			}
		}
		return false;
	}

	/** Another settlement within courier range (not too close to be worth the walk). */
	private static @Nullable Settlement chooseDestination(GenerationContext ctx) {
		BlockPos center = ctx.settlement().center();
		long maxSq = (long) ctx.config().courierMaxDistance() * ctx.config().courierMaxDistance();
		List<Settlement> near = new ArrayList<>();
		for (Settlement o : ctx.others()) {
			if (o.id() == ctx.settlement().id() || !o.dimension().equals(ctx.settlement().dimension())) {
				continue;
			}
			double d = horizontalDistSq(center, o.center());
			if (d >= 96 * 96 && d <= maxSq) {
				near.add(o);
			}
		}
		return near.isEmpty() ? null : near.get(ctx.random().nextInt(near.size()));
	}

	private static @Nullable Resident chooseRecipient(Settlement dest, RandomSource random) {
		List<Resident> adults = new ArrayList<>();
		for (Resident r : dest.residents()) {
			if (r.age != AgeStage.CHILD) {
				adults.add(r);
			}
		}
		return adults.isEmpty() ? null : adults.get(random.nextInt(adults.size()));
	}

	private static double horizontalDistSq(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/**
	 * Coin (template base × tier × config, plus a premium over market price for delivered goods), capped by what the
	 * treasury can still promise; items; reputation.
	 */
	static QuestReward reward(QuestTemplate t, GenerationContext ctx, List<Objective> objectives) {
		Settlement s = ctx.settlement();
		RandomSource random = ctx.random();
		QuestTemplate.RewardSpec spec = t.reward();
		double tierPay = TIER_PAY[Math.min(s.tier().ordinal(), TIER_PAY.length - 1)];
		long coin = Math.round((spec.coinMin() >= spec.coinMax() ? spec.coinMax() : Mth.nextInt(random, spec.coinMin(), spec.coinMax()))
			* tierPay * ctx.config().coinMultiplier());
		double premium = spec.marketPremium() > 0 ? spec.marketPremium() : t.type() == QuestType.SUPPLY ? ctx.config().marketPremium() : 0;
		if (premium > 0) {
			for (Objective o : objectives) {
				if (o.kind != ObjectiveKind.DELIVER || o.target.startsWith("#")) {
					continue;
				}
				Identifier item = Identifier.parse(o.target);
				long price = ctx.facts().marketPrice(s, item, o.count);
				if (price < 0) {
					price = (long) GoodsItems.baseBits(item) * o.count;
				}
				coin += Math.round(price * premium * ctx.config().coinMultiplier());
			}
		}
		coin = Math.min(coin, budget(ctx));
		List<QuestReward.ItemReward> items = new ArrayList<>();
		for (QuestTemplate.ItemSpec is : spec.items()) {
			Identifier id = Identifier.tryParse(is.item());
			if (id != null && ctx.facts().itemExists(id)) {
				items.add(new QuestReward.ItemReward(id, is.count().resolve(random, Map.of())));
			}
		}
		int rep = spec.reputation() != 0 ? spec.reputation() : ctx.config().reputationComplete();
		return RewardText.withText(new QuestReward(Math.max(0, coin), items, rep, ""), ctx.facts());
	}

	/** What the board may still promise: a share of the treasury, less what untaken offers already promise. */
	public static long budget(GenerationContext ctx) {
		long promised = 0;
		for (QuestInstance q : ctx.data().offers) {
			if (q.takenBy.isEmpty()) {
				promised += q.reward.coin();
			}
		}
		return Math.max(0, (long) Math.floor(ctx.settlement().treasury() * ctx.config().treasuryShare()) - promised);
	}

	private static String pick(List<String> variants, int index) {
		return variants.isEmpty() ? "" : variants.get(index % variants.size());
	}

	/** The trades a template prefers, for the dev commands. */
	public static String jobs(QuestTemplate t) {
		return t.giverJobs().stream().map(JobType::getSerializedName).reduce((a, b) -> a + ", " + b).orElse("anyone");
	}
}
