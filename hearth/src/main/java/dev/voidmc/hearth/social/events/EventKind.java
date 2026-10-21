package dev.voidmc.hearth.social.events;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * The town events you stumble into (docs/LIVING_WORLD_2.md §3.1). A major event is the day's one big scene in a town (a
 * wedding, a trial, a raid); minor ones (a christening, a runaway pig) come up to three a day. What starts an event is its
 * {@link Trigger}: a roll on the town's state ({@link Trigger#CHANCE}), or something that already happened (a match made,
 * a court day with a defendant, the bailiff's round, a band on the road, a roof finished, a birth, a verdict).
 */
public enum EventKind implements StringRepresentable {
	WEDDING(true, Trigger.FAMILY),
	FUNERAL(true, Trigger.FAMILY),
	TRIAL(true, Trigger.COURT),
	PILLORY(false, Trigger.VERDICT),
	EXECUTION(true, Trigger.VERDICT),
	FAIR(true, Trigger.FAIR_DAY),
	TOURNAMENT(true, Trigger.TOURNEY_DAY),
	FIRE(true, Trigger.CHANCE),
	TAX(true, Trigger.DOMAIN),
	RAID(true, Trigger.BAND),
	TOPPING_OUT(false, Trigger.BUILDING),
	CHRISTENING(false, Trigger.BIRTH),
	CART_OVERTURNED(false, Trigger.CHANCE),
	RUNAWAY_PIG(false, Trigger.CHANCE),
	TROUPE(false, Trigger.CHANCE),
	PREACHER(false, Trigger.CHANCE),
	HORSE_FAIR(false, Trigger.CHANCE),
	SLAUGHTER_DAY(false, Trigger.CHANCE),
	DECREE(false, Trigger.CHANCE),
	WELL_QUARREL(false, Trigger.CHANCE),
	LOST_CHILD(false, Trigger.CHANCE),
	/** A scene another module stages through {@link dev.voidmc.hearth.api.SceneService} (an oath, a dubbing). */
	SCENE(false, Trigger.STAGED);

	public static final Codec<EventKind> CODEC = StringRepresentable.fromEnum(EventKind::values);
	public static final EventKind[] VALUES = values();

	/** What brings an event about. */
	public enum Trigger {
		/** A daily roll on the town's state (its rule's chance by tier, its conditions). */
		CHANCE,
		/** A wedding or funeral in the social ledger (FamilyLife makes the match and marks the death). */
		FAMILY,
		/** The lord's court day, when there is someone to try. */
		COURT,
		/** The lord's bailiff's round of the domain, every 14 days. */
		DOMAIN,
		/** A band on a road near enough and stronger than the town's defence. */
		BAND,
		/** A large building's roof finished. */
		BUILDING,
		/** A child born. */
		BIRTH,
		/** A trial's sentence. */
		VERDICT,
		/** The town's fair, once a festival year. */
		FAIR_DAY,
		/** The lord's tournament, once a festival year. */
		TOURNEY_DAY,
		/** Staged by another module (SceneService), never scheduled by the book. */
		STAGED
	}

	public final boolean major;
	public final Trigger trigger;
	private final String id;

	EventKind(boolean major, Trigger trigger) {
		this.major = major;
		this.trigger = trigger;
		this.id = name().toLowerCase(Locale.ROOT);
	}

	public String id() {
		return this.id;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}

	/** The event's name in words ("a wedding"), for notices and the arrival line. */
	public String translationKey() {
		return "hearth.events.kind." + this.id;
	}

	public static @Nullable EventKind byId(String id) {
		for (EventKind k : VALUES) {
			if (k.id.equals(id)) {
				return k;
			}
		}
		return null;
	}
}
