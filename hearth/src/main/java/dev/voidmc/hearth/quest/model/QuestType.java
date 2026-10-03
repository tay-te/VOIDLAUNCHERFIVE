package dev.voidmc.hearth.quest.model;

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
	PERSONAL("personal", Trigger.NONE, true);

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
		PRESSURE("pressure");

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
