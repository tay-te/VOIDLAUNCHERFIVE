package dev.voidmc.hearth.quest.template;

import dev.voidmc.hearth.api.QuestType;
import dev.voidmc.hearth.quest.QuestText;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Checks a parsed template for things that would stop it binding or read wrong in game: objectives that cannot work,
 * unknown items and creatures, placeholders nobody fills. Registry lookups go through {@link Lookup} so the same checks
 * run in unit tests.
 */
public final class TemplateValidator {
	/** Variables an {@link Amount} may name. */
	public static final Set<String> AMOUNT_VARIABLES = Set.of("missing", "pressure", "population", "amount");
	/** Where an objective's place can be bound (besides {@code poi:}, {@code site:} and {@code plot:} forms). */
	public static final Set<String> PLACES = Set.of("", "ring", "center", "event", "route");
	public static final String GOODS_ITEM = "$goods_item";
	public static final String GIFT = "$gift";
	public static final String MONSTER = "monster";
	private static final int MAX_OBJECTIVES = 6;

	private TemplateValidator() {
	}

	/** What the validator may ask of the game's registries. */
	public interface Lookup {
		boolean item(Identifier id);

		boolean entity(Identifier id);

		boolean modLoaded(String modId);

		/** Accepts everything: for parsing checks only. */
		Lookup ANY = new Lookup() {
			@Override
			public boolean item(Identifier id) {
				return true;
			}

			@Override
			public boolean entity(Identifier id) {
				return true;
			}

			@Override
			public boolean modLoaded(String modId) {
				return true;
			}
		};
	}

	/** Whether the template's required mods are all present (a template for Expanse's goods sits out without it). */
	public static boolean enabled(QuestTemplate t, Lookup lookup) {
		for (String mod : t.when().requiresMods()) {
			if (!lookup.modLoaded(mod)) {
				return false;
			}
		}
		return true;
	}

	/** Every problem found, empty if the template is good. */
	public static List<String> validate(QuestTemplate t, Lookup lookup) {
		List<String> problems = new ArrayList<>();
		if (t.objectives().isEmpty()) {
			problems.add("objectives: at least one is needed");
		}
		if (t.objectives().size() > MAX_OBJECTIVES) {
			problems.add("objectives: at most " + MAX_OBJECTIVES);
		}
		boolean stalled = t.when().trigger() == QuestType.Trigger.STALLED || t.when().trigger() == QuestType.Trigger.SHORTAGE;
		boolean event = t.when().trigger() == QuestType.Trigger.EVENT;
		boolean hasGoodsItem = false;
		for (String c : t.when().requires()) {
			String problem = dev.voidmc.hearth.quest.gen.TownState.problem(c);
			if (problem != null) {
				problems.add("when.requires: " + problem);
			}
		}
		for (String e : t.when().events()) {
			if (!dev.voidmc.hearth.quest.model.SettlementQuests.Happening.KINDS.contains(e)) {
				problems.add("when.events: unknown event '" + e + "' (one of "
					+ String.join(", ", dev.voidmc.hearth.quest.model.SettlementQuests.Happening.KINDS.stream().sorted().toList()) + ")");
			}
		}
		if (event && t.when().events().isEmpty()) {
			problems.add("when.events: an event template names the happenings it answers");
		}
		if (!event && !t.when().events().isEmpty()) {
			problems.add("when.events: only read for trigger \"event\"");
		}
		for (int i = 0; i < t.objectives().size(); i++) {
			ObjectiveSpec o = t.objectives().get(i);
			String p = "objectives[" + i + "]";
			if (o.count().isVariable() && !AMOUNT_VARIABLES.contains(o.count().variable())) {
				problems.add(p + ".count: unknown variable $" + o.count().variable() + " (one of " + AMOUNT_VARIABLES + ")");
			}
			if (o.count().isVariable() && "missing".equals(o.count().variable()) && !stalled) {
				problems.add(p + ".count: $missing is only bound for stalled-construction and shortage templates");
			}
			if (o.count().isVariable() && "amount".equals(o.count().variable()) && !event) {
				problems.add(p + ".count: $amount is only bound for event templates");
			}
			checkPlace(o, event, p, problems);
			if (!o.crime().isEmpty() && java.util.Arrays.stream(dev.voidmc.hearth.api.LawService.Offence.values())
				.noneMatch(off -> off.getSerializedName().equals(o.crime()))) {
				problems.add(p + ".crime: unknown offence '" + o.crime() + "'");
			}
			switch (o.kind()) {
				case DELIVER -> {
					if (o.items().isEmpty()) {
						problems.add(p + ": deliver needs item or items");
					}
					for (String item : o.items()) {
						if (item.equals(GOODS_ITEM)) {
							hasGoodsItem = true;
							if (!stalled) {
								problems.add(p + ".item: " + GOODS_ITEM + " is only bound for stalled-construction and shortage templates");
							}
						} else if (!item.equals(GIFT)) {
							checkId(item, true, lookup::item, t, p + ".item", problems);
						}
					}
				}
				case KILL -> {
					if (o.entities().isEmpty()) {
						problems.add(p + ": kill needs entity or entities");
					}
					for (String e : o.entities()) {
						if (!e.equals(MONSTER)) {
							checkId(e, true, lookup::entity, t, p + ".entity", problems);
						}
					}
				}
				case SLAY -> {
					if (o.entities().isEmpty()) {
						problems.add(p + ": slay needs entity or entities");
					}
					for (String e : o.entities()) {
						checkId(e, false, lookup::entity, t, p + ".entity", problems);
					}
					if (o.names().isEmpty()) {
						problems.add(p + ": slay needs name or names for the beast");
					}
					if (isRing(o.place()) && o.maxDistance() <= 0) {
						problems.add(p + ".distance: slay needs a distance band for the beast's lair");
					}
				}
				case REACH -> {
					if (isRing(o.place()) && o.maxDistance() <= 0) {
						problems.add(p + ".distance: reach needs a distance band for the place (or a place)");
					}
				}
				case EXPLORE -> {
					if (o.structures().isEmpty()) {
						problems.add(p + ": explore needs structure or structures");
					}
					for (String s : o.structures()) {
						if (s.startsWith("#")) {
							problems.add(p + ".structures: list structure ids, not tags: " + s);
						} else if (Identifier.tryParse(s) == null) {
							problems.add(p + ".structures: not a valid id: " + s);
						}
					}
				}
				case BIOME -> {
					if (o.biomes().isEmpty()) {
						problems.add(p + ": biome needs biome or biomes");
					}
					for (String s : o.biomes()) {
						if (Identifier.tryParse(s) == null) {
							problems.add(p + ".biomes: not a valid id: " + s);
						}
					}
				}
				case ESCORT -> {
					for (String e : o.entities()) {
						checkId(e, false, lookup::entity, t, p + ".entity", problems);
					}
				}
				case SCOUT -> {
					if (o.maxDistance() <= 0 || o.minDistance() > o.maxDistance()) {
						problems.add(p + ".distance: scout needs a distance band [min, max]");
					}
				}
				case AMBUSH -> {
					if (o.entities().isEmpty()) {
						problems.add(p + ": ambush needs entity or entities");
					}
					for (String e : o.entities()) {
						checkId(e, false, lookup::entity, t, p + ".entity", problems);
					}
					if (isRing(o.place()) && o.maxDistance() <= 0) {
						problems.add(p + ".distance: an ambush in the open needs a distance band (or a place)");
					}
				}
				case APPREHEND -> {
					if (o.entities().isEmpty()) {
						problems.add(p + ": apprehend needs entity or entities");
					}
					for (String e : o.entities()) {
						checkId(e, false, lookup::entity, t, p + ".entity", problems);
					}
					if (o.names().isEmpty()) {
						problems.add(p + ": apprehend needs name or names for the culprit");
					}
					if (isRing(o.place()) && o.maxDistance() <= 0) {
						problems.add(p + ".distance: apprehend in the open needs a distance band (or a place)");
					}
				}
				case RESCUE, EXTINGUISH -> {
					if (isRing(o.place()) && o.maxDistance() <= 0) {
						problems.add(p + ".place: " + o.kind().getSerializedName() + " needs a place (event, plot:..., site:...) or a distance band");
					}
				}
				case PROSPECT -> {
					if (o.resource().isEmpty() && !t.when().events().contains(dev.voidmc.hearth.quest.model.SettlementQuests.Happening.DEPLETED)) {
						problems.add(p + ".resource: prospect needs a resource (or a template answering the depleted event)");
					}
					if (!o.resource().isEmpty() && java.util.Arrays.stream(dev.voidmc.hearth.api.ResourceSite.Kind.values())
						.noneMatch(k -> k.getSerializedName().equals(o.resource()))) {
						problems.add(p + ".resource: unknown site kind '" + o.resource() + "'");
					}
					if (o.maxDistance() <= 0 || o.minDistance() > o.maxDistance()) {
						problems.add(p + ".distance: prospect needs a distance band [min, max]");
					}
				}
				case PAY -> {
					if (!o.count().isVariable() && o.count().max() <= 0) {
						problems.add(p + ".count: pay needs a sum in bits");
					}
				}
				case COURIER, TALK, HUNT -> {
				}
			}
			if (o.grant() != null) {
				checkId(o.grant().item(), false, lookup::item, t, p + ".grant.item", problems);
				checkText(o.grant().name(), p + ".grant.name", problems);
				checkText(o.grant().lore(), p + ".grant.lore", problems);
			}
			checkText(o.label(), p + ".label", problems);
		}
		if (stalled && !hasGoodsItem) {
			problems.add("objectives: a stalled-construction or shortage template needs a deliver objective with item " + GOODS_ITEM);
		}
		if (t.type() == QuestType.CONTRACT && t.when().trigger() != QuestType.Trigger.SHORTAGE) {
			problems.add("when.trigger: a contract answers a workshop's shortage (trigger \"shortage\")");
		}
		if (t.type() == QuestType.COURIER && t.objectives().stream().noneMatch(o -> o.kind() == ObjectiveKind.COURIER)) {
			problems.add("objectives: a courier quest needs a courier objective");
		}
		if (t.type() == QuestType.ESCORT && t.objectives().stream().noneMatch(o -> o.kind() == ObjectiveKind.ESCORT)) {
			problems.add("objectives: an escort quest needs an escort objective");
		}
		QuestTemplate.Text text = t.text();
		for (String s : text.title()) {
			checkText(s, "text.title", problems);
		}
		for (List<String> list : List.of(text.summary(), text.offer(), text.progress(), text.complete())) {
			for (String s : list) {
				checkText(s, "text", problems);
			}
		}
		for (int i = 0; i < t.reward().items().size(); i++) {
			checkId(t.reward().items().get(i).item(), false, lookup::item, t, "reward.items[" + i + "].item", problems);
		}
		if (t.reward().coinMin() > t.reward().coinMax()) {
			problems.add("reward.coin: min above max");
		}
		return problems;
	}

	private static boolean isRing(String place) {
		return place.isEmpty() || place.equals("ring");
	}

	private static void checkPlace(ObjectiveSpec o, boolean event, String p, List<String> problems) {
		String place = o.place();
		if (PLACES.contains(place)) {
			if (place.equals("event") && !event) {
				problems.add(p + ".place: event is only bound for event templates");
			}
			return;
		}
		int colon = place.indexOf(':');
		String head = colon < 0 ? place : place.substring(0, colon);
		String arg = colon < 0 ? "" : place.substring(colon + 1);
		switch (head) {
			case "poi" -> {
				if (java.util.Arrays.stream(dev.voidmc.hearth.api.PoiType.values()).noneMatch(t -> t.getSerializedName().equals(arg))) {
					problems.add(p + ".place: unknown point of interest '" + arg + "'");
				}
			}
			case "site" -> {
				if (java.util.Arrays.stream(dev.voidmc.hearth.api.ResourceSite.Kind.values()).noneMatch(k -> k.getSerializedName().equals(arg))) {
					problems.add(p + ".place: unknown site kind '" + arg + "'");
				}
			}
			case "plot" -> {
				if (arg.isEmpty()) {
					problems.add(p + ".place: plot: needs an archetype path prefix (plot:extract/mine)");
				}
			}
			default -> problems.add(p + ".place: unknown place '" + place + "' (ring, center, event, route, poi:<type>, site:<kind>, plot:<prefix>)");
		}
	}

	private static void checkText(String text, String path, List<String> problems) {
		for (String name : QuestText.placeholders(text)) {
			if (!QuestText.KNOWN.contains(name)) {
				problems.add(path + ": unknown placeholder {" + name + "} (known: " + String.join(", ", QuestText.KNOWN) + ")");
			}
		}
	}

	private static void checkId(String raw, boolean tagAllowed, java.util.function.Predicate<Identifier> exists, QuestTemplate t, String path,
		List<String> problems) {
		boolean tag = raw.startsWith("#");
		if (tag && !tagAllowed) {
			problems.add(path + ": a tag is not allowed here: " + raw);
			return;
		}
		Identifier id = Identifier.tryParse(tag ? raw.substring(1) : raw);
		if (id == null) {
			problems.add(path + ": not a valid id: " + raw);
			return;
		}
		// Tags load alongside templates and may come from any pack, so only ids are checked; an id of a mod the
		// template requires is fine to name (the template is disabled when that mod is absent).
		if (!tag && !exists.test(id) && !t.when().requiresMods().contains(id.getNamespace())) {
			problems.add(path + ": unknown id " + raw);
		}
	}
}
