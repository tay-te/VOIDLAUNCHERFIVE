package dev.voidmc.hearth.api;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The law, as other modules use it (docs/LIVING_WORLD.md §4): who owns what, which goods are stolen, who is wanted, and a
 * way to report crimes the law module cannot see for itself (robbing a caravan on a quest, say). Implemented by the law
 * module, which also owns witnesses, guards, justice, locks and the stealth minigames. Server thread only. May be absent.
 */
public interface LawService {
	/** Who owns the block at {@code pos} (a container, a bed, a crop, a door), or null for the wild and the public. */
	@Nullable Owner owner(ServerLevel level, BlockPos pos);

	/** Who owns an animal (a settlement's pasture flock, a stable's horse), or null. */
	@Nullable Owner owner(Entity entity);

	boolean isStolen(ItemStack stack);

	/** Whom a stolen item was taken from, or null if it is not stolen (or the mark has faded). */
	@Nullable Owner stolenFrom(ItemStack stack);

	/** Whether the player is wanted in a settlement (a crime reported and not yet paid for). */
	boolean wanted(UUID player, long settlementId);

	/** The coin the player owes a settlement in fines and bounty (bits), 0 if none. */
	long bounty(UUID player, long settlementId);

	/** Whether the player is an outlaw in the settlement's region (attacked on sight). */
	boolean outlaw(UUID player, long settlementId);

	/** Reports a crime committed where the law module cannot see it; it then goes through witnesses and justice as usual. */
	void report(Crime crime);

	/** Every player wanted in a settlement, worst first, for notice boards and town criers. */
	default java.util.List<Wanted> wanted(long settlementId) {
		return java.util.List.of();
	}

	/** Clears the player's wanted status (and bounty) in a settlement, as by a pardon or work done in place of the fine. */
	default void pardon(UUID player, long settlementId, String reason) {
	}

	/**
	 * Marks goods as stolen from {@code owner} (loot of a robbed caravan, say), so that town's merchants refuse them and a
	 * fence takes them. {@code thief} may be null when nobody in particular took them.
	 */
	default void markStolen(ItemStack stack, Owner owner, @Nullable UUID thief) {
	}

	/**
	 * A wanted player, as a poster shows them.
	 *
	 * @param origin the settlement the crime was committed in when word came from elsewhere (0: here)
	 */
	record Wanted(UUID player, String name, long bounty, Offence worst, boolean outlaw, long origin) {
	}

	/**
	 * @param settlementId the settlement whose property it is (or whose law applies), or 0 for none
	 * @param resident     the resident who owns it (a household, a workplace's worker), or null for the settlement itself
	 * @param player       the player who owns it (a deeded house, a bought workshop), or null
	 */
	record Owner(long settlementId, @Nullable UUID resident, @Nullable UUID player, Kind kind) {
		public enum Kind {
			HOUSEHOLD, WORKPLACE, SETTLEMENT, PLAYER
		}
	}

	/**
	 * @param victim  the resident wronged, or null (the settlement, a caravan)
	 * @param value   the worth of what was taken or broken, in bits (sets the fine)
	 * @param witnessed whether the reporter saw it (otherwise it is a suspicion to be proven)
	 */
	record Crime(Offence offence, UUID offender, long settlementId, BlockPos pos, @Nullable UUID victim, long value, boolean witnessed,
		long gameTime) {
	}

	enum Offence implements StringRepresentable {
		THEFT("theft"),
		PICKPOCKETING("pickpocketing"),
		LOCKPICKING("lockpicking"),
		TRESPASS("trespass"),
		POACHING("poaching"),
		VANDALISM("vandalism"),
		ASSAULT("assault"),
		MURDER("murder"),
		ROBBERY("robbery"),
		JAILBREAK("jailbreak"),
		RESISTING_ARREST("resisting_arrest");

		public static final StringRepresentable.EnumCodec<Offence> CODEC = StringRepresentable.fromEnum(Offence::values);

		private final String name;

		Offence(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		public String translationKey() {
			return "hearth.offence." + this.name;
		}
	}
}
