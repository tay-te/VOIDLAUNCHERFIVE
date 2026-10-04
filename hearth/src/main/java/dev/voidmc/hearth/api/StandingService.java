package dev.voidmc.hearth.api;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * Reputation in three layers (docs/LIVING_WORLD.md §3): each resident's personal opinion of a player, the player's
 * standing in each settlement, and regional fame that travels between towns as gossip. Implemented by the standing
 * module; {@link Settlement#reputation} and {@link Settlement#addReputation} are the settlement layer's raw numbers.
 * Server thread only. May be absent.
 */
public interface StandingService {
	/** The player's standing in a settlement, −100 (outlaw) to 100 (hero). */
	int standing(long settlementId, UUID player);

	Title title(long settlementId, UUID player);

	/**
	 * Something a player did. The service moves the settlement's standing, the opinions of the folk it touched (the
	 * victim, the witnesses, the giver of a quest), and queues the news as gossip to neighbouring towns (arriving days
	 * later, fading with distance). Every module reports deeds here rather than moving numbers itself.
	 */
	void deed(Deed deed);

	/** One resident's opinion of a player, −100 to 100, from their memories of each other (decaying with time). */
	int opinion(UUID resident, UUID player);

	/** How well known the player is around {@code near} (0 = nobody has heard of them), for greetings and prices. */
	int fame(UUID player, BlockPos near);

	/**
	 * How the player comes across right now, −1 (repellent) to 1 (charming): clean or filthy, local dress or foreign,
	 * armour on and weapon drawn (intimidating), bloodied from a fight. Speech checks and prices read it.
	 */
	float charisma(ServerPlayer player);

	/** How intimidating the player looks right now, 0 to 1 (armour, a drawn weapon, reputation for violence). */
	float menace(ServerPlayer player);

	/** The company (a sworn band of players sharing some standing) the player belongs to, or null. */
	@Nullable String company(UUID player);

	/**
	 * @param kind     a namespaced id of what happened ({@code quest:completed}, {@code law:theft}, {@code economy:donation},
	 *                 {@code repair:helped}), which the standing module maps to effects and to the words of the gossip
	 * @param weight   how much it matters, signed: +1 a small kindness, +25 saving the town, −30 a theft, −100 a murder
	 * @param resident the resident most affected (the victim, the quest giver), or null
	 * @param witnessed whether folk saw it (unwitnessed deeds touch only the people directly involved, and spread later
	 *                 if they are discovered)
	 */
	record Deed(UUID player, long settlementId, String kind, int weight, @Nullable UUID resident, BlockPos pos, boolean witnessed) {
	}

	enum Title implements StringRepresentable {
		OUTLAW("outlaw"),
		DISTRUSTED("distrusted"),
		STRANGER("stranger"),
		KNOWN("known"),
		FRIEND("friend"),
		/** Owns property in the town. */
		BURGHER("burgher"),
		HERO("hero");

		public static final StringRepresentable.EnumCodec<Title> CODEC = StringRepresentable.fromEnum(Title::values);

		private final String name;

		Title(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		public String translationKey() {
			return "hearth.title." + this.name;
		}
	}
}
