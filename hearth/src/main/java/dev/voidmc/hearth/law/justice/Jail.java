package dev.voidmc.hearth.law.justice;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.LawConfig;
import dev.voidmc.hearth.law.crime.CrimeDesk;
import dev.voidmc.hearth.law.crime.LawStatusSender;
import dev.voidmc.hearth.law.own.Ownership;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Serving a sentence (docs/LIVING_WORLD.md §4): the cell in the guardhouse, or the pillory in the square for lesser
 * crimes. On a server the time is real and counts only while the player is online; walking out of the cell is a
 * jailbreak (the unserved part of the bounty is owed again, with the jailbreak's own fine), while the pillory simply
 * holds them fast. In single player a cell sentence is slept through: the clock jumps an hour per minute of sentence.
 */
public final class Jail {
	/** How far from the cell's point a prisoner may stand. */
	static final double CELL_RADIUS = 3.2;
	/** Game ticks per minute of sentence when it is slept through in single player (an hour). */
	static final int SLEEP_TICKS_PER_MINUTE = 1000;

	private Jail() {
	}

	/** Where a settlement keeps prisoners: its cell nearest the player, or null if it has none. */
	public static @Nullable BlockPos cell(Settlement s, BlockPos near) {
		return nearest(s.pois(PoiType.CELL), near);
	}

	public static @Nullable BlockPos pillory(Settlement s, BlockPos near) {
		return nearest(s.pois(PoiType.PILLORY), near);
	}

	private static @Nullable BlockPos nearest(List<Poi> pois, BlockPos near) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (Poi p : pois) {
			double d = p.pos().distSqr(near);
			if (d < bestD) {
				bestD = d;
				best = p.pos();
			}
		}
		return best;
	}

	/** Whether the player is serving a sentence. */
	public static boolean serving(ServerPlayer player) {
		return PlayerLaw.of(player).sentence().isPresent();
	}

	/**
	 * Puts the player in the cell (or the pillory) for {@code minutes}, or, in single player with sleep-through on, sleeps
	 * the sentence away. Returns false if there is nowhere to hold them.
	 */
	public static boolean sentence(ServerPlayer player, Settlement s, int minutes, boolean pillory, long owed) {
		ServerLevel level = player.level();
		BlockPos place = pillory ? pillory(s, player.blockPosition()) : cell(s, player.blockPosition());
		if (place == null) {
			return false;
		}
		BlockPos release = release(level, s, place);
		MinecraftServer server = level.getServer();
		LawConfig.Justice cfg = Law.config().justice();
		if (!pillory && cfg.sleepThroughJail() && server.isSingleplayer() && server.getPlayerList().getPlayerCount() <= 1) {
			int ticks = Math.min(3 * 24000, minutes * SLEEP_TICKS_PER_MINUTE);
			level.dimensionType().defaultClock().ifPresent(clock -> server.clockManager().addTicks(clock, ticks));
			player.teleportTo(release.getX() + 0.5, release.getY(), release.getZ() + 0.5);
			player.sendSystemMessage(Component.translatable("hearth.law.jail.slept", minutes, s.name()).withStyle(ChatFormatting.GRAY));
			player.getFoodData().setFoodLevel(Math.max(4, player.getFoodData().getFoodLevel() - minutes));
			return true;
		}
		int ticks = minutes * 1200;
		PlayerLaw.set(player, PlayerLaw.of(player).withSentence(new PlayerLaw.Sentence(s.id(), place, ticks, pillory, release, owed, ticks)));
		player.teleportTo(place.getX() + 0.5, place.getY(), place.getZ() + 0.5);
		player.sendSystemMessage(Component.translatable(pillory ? "hearth.law.pillory.start" : "hearth.law.jail.start", minutes, s.name())
			.withStyle(ChatFormatting.GOLD));
		level.playSound(null, place, pillory ? SoundEvents.WOODEN_TRAPDOOR_CLOSE : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.8F);
		LawStatusSender.refresh(player);
		return true;
	}

	/** Every second for each player: counts down sentences, holds the pilloried fast and notices those who walk out. */
	public static void tick(ServerPlayer player, int ticks) {
		PlayerLaw law = PlayerLaw.of(player);
		if (law.sentence().isEmpty()) {
			return;
		}
		PlayerLaw.Sentence s = law.sentence().get().served(ticks);
		Settlement settlement = Ownership.settlement(s.settlement(), player.level().getServer());
		if (settlement == null) {
			PlayerLaw.set(player, law.withSentence(null));
			LawStatusSender.refresh(player);
			return;
		}
		if (s.ticksLeft() <= 0) {
			release(player, settlement, s);
			return;
		}
		double dx = player.getX() - (s.place().getX() + 0.5);
		double dz = player.getZ() - (s.place().getZ() + 0.5);
		double dy = player.getY() - s.place().getY();
		boolean away = dx * dx + dz * dz > (s.pillory() ? 0.8 * 0.8 : CELL_RADIUS * CELL_RADIUS) || Math.abs(dy) > (s.pillory() ? 1.5 : 3.0);
		if (away) {
			if (s.pillory()) {
				player.teleportTo(s.place().getX() + 0.5, s.place().getY(), s.place().getZ() + 0.5);
			} else {
				escaped(player, settlement, s);
				return;
			}
		}
		if (s.pillory() && player.level().getGameTime() % 100 < ticks) {
			jeer(player);
		}
		PlayerLaw.set(player, law.withSentence(s));
		LawStatusSender.refresh(player);
	}

	/** Holds a pilloried player in place between the once-a-second checks. */
	public static void hold(ServerPlayer player) {
		PlayerLaw law = PlayerLaw.of(player);
		if (law.sentence().isPresent() && law.sentence().get().pillory()) {
			BlockPos p = law.sentence().get().place();
			if (player.position().distanceToSqr(p.getX() + 0.5, player.getY(), p.getZ() + 0.5) > 0.36) {
				player.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
			}
		}
	}

	/** After death or a dimension change, a prisoner is put back where they were held. */
	public static void returnToPlace(ServerPlayer player) {
		PlayerLaw law = PlayerLaw.of(player);
		law.sentence().ifPresent(s -> player.teleportTo(s.place().getX() + 0.5, s.place().getY(), s.place().getZ() + 0.5));
	}

	private static void release(ServerPlayer player, Settlement settlement, PlayerLaw.Sentence s) {
		PlayerLaw.set(player, PlayerLaw.of(player).withSentence(null));
		player.teleportTo(s.release().getX() + 0.5, s.release().getY(), s.release().getZ() + 0.5);
		player.sendSystemMessage(Component.translatable(s.pillory() ? "hearth.law.pillory.done" : "hearth.law.jail.done", settlement.name())
			.withStyle(ChatFormatting.GREEN));
		if (s.pillory()) {
			CrimeDesk.deed(settlement, player.getUUID(), "law:pillory", -8, null, s.place(), true);
		}
		LawStatusSender.refresh(player);
	}

	private static void escaped(ServerPlayer player, Settlement settlement, PlayerLaw.Sentence s) {
		PlayerLaw.set(player, PlayerLaw.of(player).withSentence(null));
		long day = Law.day(player.level());
		Warrant w = Law.state(settlement).warrantFor(player.getUUID(), day);
		long owed = s.unserved();
		if (owed > 0) {
			w.grow(0, owed, day);
		}
		settlement.markDirty();
		player.sendSystemMessage(Component.translatable("hearth.law.jail.escaped", settlement.name()).withStyle(ChatFormatting.RED));
		// breaking out is a crime of its own, if anyone sees it (the jailer's round discovers it otherwise)
		CrimeDesk.commit(player.level(), player, settlement, Offence.JAILBREAK, player.blockPosition(), null, 0, 0.4, null);
		LawStatusSender.refresh(player);
	}

	/** Folk round the pillory have their say. */
	private static void jeer(ServerPlayer player) {
		NpcService npc = HearthServices.npc();
		if (npc == null) {
			return;
		}
		List<FolkHandle> near = npc.folkNear(player.level(), player.position(), 12);
		if (near.isEmpty()) {
			return;
		}
		FolkHandle folk = near.get(player.getRandom().nextInt(near.size()));
		if (!folk.awake()) {
			return;
		}
		try {
			folk.lookAt(player);
			folk.say(Component.translatable("hearth.law.say.jeer." + player.getRandom().nextInt(4)), null);
		} catch (RuntimeException e) {
			Hearth.LOG.debug("law: jeer failed", e);
		}
	}

	/** Where a prisoner walks out: the building's door nearest the cell, or the town's heart. */
	static BlockPos release(ServerLevel level, Settlement s, BlockPos place) {
		BlockPos door = null;
		double best = 24 * 24;
		for (Poi p : s.pois(PoiType.DOOR)) {
			double d = p.pos().distSqr(place);
			if (d < best) {
				best = d;
				door = p.pos();
			}
		}
		if (door != null) {
			return door;
		}
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.center());
	}
}
