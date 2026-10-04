package dev.voidmc.hearth.social.service;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * A player's life in town ({@code hearth:social/player}, persistent, kept through death): the inn bed they have taken
 * for the night (with the respawn point it replaced, given back at checkout), the mount they have hired, and when they
 * last bathed (the standing module's cleanliness can read {@link #lastBath}; docs/requests/p3-social.md).
 */
public record PlayerLife(Optional<Bed> bed, Optional<Hire> hire, long lastBath) {
	public static final PlayerLife EMPTY = new PlayerLife(Optional.empty(), Optional.empty(), Long.MIN_VALUE);

	public static final Codec<PlayerLife> CODEC = RecordCodecBuilder.create(i -> i.group(
		Bed.CODEC.optionalFieldOf("bed").forGetter(PlayerLife::bed),
		Hire.CODEC.optionalFieldOf("hire").forGetter(PlayerLife::hire),
		Codec.LONG.optionalFieldOf("last_bath", Long.MIN_VALUE).forGetter(PlayerLife::lastBath)
	).apply(i, PlayerLife::new));

	public static final AttachmentType<PlayerLife> ATTACHMENT = AttachmentRegistry.create(Hearth.id("social/player"), b -> b
		.persistent(CODEC)
		.initializer(() -> EMPTY)
		.copyOnDeath());

	/** Forces registration during init. */
	public static void init() {
	}

	public static PlayerLife of(Player player) {
		PlayerLife p = player.getAttached(ATTACHMENT);
		return p == null ? EMPTY : p;
	}

	public static void set(ServerPlayer player, PlayerLife life) {
		player.setAttached(ATTACHMENT, life);
	}

	public PlayerLife withBed(@Nullable Bed bed) {
		return new PlayerLife(Optional.ofNullable(bed), this.hire, this.lastBath);
	}

	public PlayerLife withHire(@Nullable Hire hire) {
		return new PlayerLife(this.bed, Optional.ofNullable(hire), this.lastBath);
	}

	public PlayerLife withBath(long gameTime) {
		return new PlayerLife(this.bed, this.hire, gameTime);
	}

	/**
	 * A bed at an inn for the night.
	 *
	 * @param settlementId the inn's town
	 * @param pos          the bed's head
	 * @param checkout     town tick the bed is given up
	 * @param previous     the respawn point it replaced (encoded with {@code ServerPlayer.RespawnConfig.CODEC}), if any
	 */
	public record Bed(long settlementId, BlockPos pos, long checkout, Optional<net.minecraft.server.level.ServerPlayer.RespawnConfig> previous) {
		public static final Codec<Bed> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("settlement").forGetter(Bed::settlementId),
			BlockPos.CODEC.fieldOf("pos").forGetter(Bed::pos),
			Codec.LONG.fieldOf("checkout").forGetter(Bed::checkout),
			net.minecraft.server.level.ServerPlayer.RespawnConfig.CODEC.optionalFieldOf("previous").forGetter(Bed::previous)
		).apply(i, Bed::new));
	}

	/**
	 * A hired mount.
	 *
	 * @param horse        the mount's entity id
	 * @param settlementId where it was hired
	 * @param deposit      bits left with the stable, returned at any stable less overdue days
	 * @param day          the sim day it was hired
	 * @param paidDays     days paid for
	 */
	public record Hire(UUID horse, long settlementId, long deposit, long day, int paidDays) {
		public static final Codec<Hire> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.STRING_CODEC.fieldOf("horse").forGetter(Hire::horse),
			Codec.LONG.fieldOf("settlement").forGetter(Hire::settlementId),
			Codec.LONG.fieldOf("deposit").forGetter(Hire::deposit),
			Codec.LONG.fieldOf("day").forGetter(Hire::day),
			Codec.INT.optionalFieldOf("paid_days", 1).forGetter(Hire::paidDays)
		).apply(i, Hire::new));
	}
}
