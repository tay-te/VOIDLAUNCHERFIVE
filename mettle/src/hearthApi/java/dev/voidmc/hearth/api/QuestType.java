package dev.voidmc.hearth.api;

import net.minecraft.util.StringRepresentable;

/**
 * What a quest is about, which decides where it can come from (a stalled build, monsters at night, another town) and
 * how it is shown. The objectives themselves say what the player must do.
 */
public enum QuestType implements StringRepresentable {
	/** Goods a stalled construction is short of. Delivering them puts them in the stockpile so the build goes on. */
	SUPPLY("supply", Trigger.STALLED, false),
	/** Monsters harrying the town at night, or a named beast nearby. */
	BOUNTY("bounty", Trigger.PRESSURE, false),
	/** A parcel or a letter to carry to another settlement. */
	COURIER("courier", Trigger.NONE, false),
	/** A pack animal of the caravan to lead to another settlement. */
	ESCORT("escort", Trigger.NONE, true),
	/** Find and chart a landmark, a structure or a realm. */
	EXPLORATION("exploration", Trigger.NONE, false),
	/** Pick a site for a new hamlet. */
	SCOUTING("scouting", Trigger.NONE, true),
	/** Bring local or rare goods: mammoth wool, venison, crab, amethyst. */
	GATHERING("gathering", Trigger.NONE, false),
	/** A resident's own request: a gift, a lost keepsake, a word to a friend. */
	PERSONAL("personal", Trigger.NONE, true),
	// ---- phase 3, the living world (docs/LIVING_WORLD.md §4–§7)
	/** A standing contract from a workshop short of its inputs: ore for the smelter, wax for the chandler, logs for the saw. */
	CONTRACT("contract", Trigger.SHORTAGE, false),
	/** The roads between towns: ride with a caravan, run down the bandits who robbed one, bring back its cargo. */
	CARAVAN("caravan", Trigger.EVENT, false),
	/** The law's work: outlaws wanted dead or alive, thief-taking, clearing your own name. */
	LAW("law", Trigger.NONE, false),
	/** When something goes wrong: a fire, a mine collapse, wolves at the pasture, a spent quarry, a town in decline. */
	DISASTER("disaster", Trigger.EVENT, false),
	/** Work for the town's officials: short lines from the reeve, the guard captain, the priest, the master builder, the lord. */
	OFFICIAL("official", Trigger.NONE, false),
	/** Shady work from a fence (robbing a caravan): a crime, reported to the law. */
	CRIME("crime", Trigger.NONE, true);

	public static final StringRepresentable.EnumCodec<QuestType> CODEC = StringRepresentable.fromEnum(QuestType::values);

	private final String name;
	private final Trigger defaultTrigger;
	private final boolean exclusive;

	QuestType(String name, Trigger defaultTrigger, boolean exclusive) {
		this.name = name;
		this.defaultTrigger = defaultTrigger;
		this.exclusive = exclusive;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	/** The need that makes a template of this type appear, unless the template says otherwise. */
	public Trigger defaultTrigger() {
		return this.defaultTrigger;
	}

	/** Whether an offer of this type goes to the first player who takes it (one caravan mule, one lost keepsake). */
	public boolean exclusive() {
		return this.exclusive;
	}

	public String translationKey() {
		return "hearth.quest.type." + this.name;
	}

	/** What a template waits for before it is offered. */
	public enum Trigger implements StringRepresentable {
		/** Always eligible (subject to tier, culture, reputation). */
		NONE("none"),
		/** Only while a construction is stalled for goods the template covers. */
		STALLED("stalled"),
		/** Only while monsters press on the town at night. */
		PRESSURE("pressure"),
		/** Only while a workshop of the town is short of its inputs (a standing contract). */
		SHORTAGE("shortage"),
		/** Only after something happened to the town: a fire, a collapse, a caravan robbed, a site spent ({@code when.events}). */
		EVENT("event");

		public static final StringRepresentable.EnumCodec<Trigger> CODEC = StringRepresentable.fromEnum(Trigger::values);

		private final String name;

		Trigger(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}
}
