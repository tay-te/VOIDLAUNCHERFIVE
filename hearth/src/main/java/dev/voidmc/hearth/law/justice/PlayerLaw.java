package dev.voidmc.hearth.law.justice;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The law's record of one player ({@code hearth:law/player}, a player attachment kept through death): a sentence being
 * served, and their thief's skills, which grow with practice and ease the minigames.
 *
 * @param sentence     the cell or pillory they are confined to, if any
 * @param lockpicking  locks picked (the skill is derived from it)
 * @param pickpocketing pockets picked
 */
public record PlayerLaw(Optional<Sentence> sentence, int lockpicking, int pickpocketing) {
	public static final PlayerLaw EMPTY = new PlayerLaw(Optional.empty(), 0, 0);

	public static final Codec<PlayerLaw> CODEC = RecordCodecBuilder.create(i -> i.group(
		Sentence.CODEC.optionalFieldOf("sentence").forGetter(PlayerLaw::sentence),
		Codec.INT.optionalFieldOf("lockpicking", 0).forGetter(PlayerLaw::lockpicking),
		Codec.INT.optionalFieldOf("pickpocketing", 0).forGetter(PlayerLaw::pickpocketing)
	).apply(i, PlayerLaw::new));

	public static final AttachmentType<PlayerLaw> TYPE = AttachmentRegistry.create(Hearth.id("law/player"),
		b -> b.persistent(CODEC).copyOnDeath().initializer(() -> EMPTY));

	/** Loads the class (registers the attachment). */
	public static void init() {
	}

	public static PlayerLaw of(Player player) {
		return player.getAttachedOrElse(TYPE, EMPTY);
	}

	public static void set(Player player, PlayerLaw law) {
		player.setAttached(TYPE, law);
	}

	/** Skill from practice: 0–20, quickly at first, then slowly (a hundred locks make a master). */
	public static int skill(int practice) {
		return (int) Math.min(20, Math.floor(Math.sqrt(Math.max(0, practice)) * 2.0));
	}

	public int lockpickSkill() {
		return skill(this.lockpicking);
	}

	public int pickpocketSkill() {
		return skill(this.pickpocketing);
	}

	public PlayerLaw withSentence(@Nullable Sentence s) {
		return new PlayerLaw(Optional.ofNullable(s), this.lockpicking, this.pickpocketing);
	}

	public PlayerLaw addLockpicking() {
		return new PlayerLaw(this.sentence, this.lockpicking + 1, this.pickpocketing);
	}

	public PlayerLaw addPickpocketing() {
		return new PlayerLaw(this.sentence, this.lockpicking, this.pickpocketing + 1);
	}

	/**
	 * A sentence: confinement to a place for a time, counted only while the player is online (real time served).
	 *
	 * @param settlement the settlement that sentenced them
	 * @param place      the cell (or the pillory) they are held at
	 * @param ticksLeft  ticks still to serve
	 * @param pillory    the pillory (held fast, in public) rather than the cell
	 * @param release    where they are let out
	 * @param owed       the bounty the sentence settles (what is owed again, in part, if they break out)
	 * @param total      the sentence's full length in ticks
	 */
	public record Sentence(long settlement, BlockPos place, int ticksLeft, boolean pillory, BlockPos release, long owed, int total) {
		public static final Codec<Sentence> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("settlement").forGetter(Sentence::settlement),
			BlockPos.CODEC.fieldOf("place").forGetter(Sentence::place),
			Codec.INT.fieldOf("ticks_left").forGetter(Sentence::ticksLeft),
			Codec.BOOL.optionalFieldOf("pillory", false).forGetter(Sentence::pillory),
			BlockPos.CODEC.fieldOf("release").forGetter(Sentence::release),
			Codec.LONG.optionalFieldOf("owed", 0L).forGetter(Sentence::owed),
			Codec.INT.optionalFieldOf("total", 0).forGetter(Sentence::total)
		).apply(i, Sentence::new));

		public Sentence served(int ticks) {
			return new Sentence(this.settlement, this.place, Math.max(0, this.ticksLeft - ticks), this.pillory, this.release, this.owed, this.total);
		}

		/** What is still owed if they walk out now: the unserved part of the bounty. */
		public long unserved() {
			return this.total <= 0 ? this.owed : Math.round(this.owed * (double) this.ticksLeft / this.total);
		}
	}
}
