package dev.voidmc.hearth.social.service;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.SocialTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.storage.LevelData;
import org.jspecify.annotations.Nullable;

/**
 * The inn: a bed for the night (it becomes your respawn point until checkout the next morning, then your own is given
 * back), a room with a chest of your own by the day (locked to everyone else while it is yours, and for a few days of
 * grace after the rent runs out), and a hot meal.
 */
public final class Inn {
	private Inn() {
	}

	public enum Result {
		OK, FULL, NO_INN, CANNOT_PAY, ALREADY
	}

	/** Takes a bed for the night. */
	public static Result rentBed(ServerPlayer player, HearthWorld world, Settlement s, Resident keeper) {
		SocialConfig.Inn cfg = SocialModule.config().inn();
		PlotView inn = ServiceSites.inn(s, keeper);
		if (inn == null) {
			return Result.NO_INN;
		}
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		long now = SocialTime.now(world);
		ledger.beds.removeIf(r -> r.until() <= now);
		PlayerLife life = PlayerLife.of(player);
		if (life.bed().isPresent() && life.bed().get().settlementId() == s.id() && life.bed().get().checkout() > now) {
			return Result.ALREADY;
		}
		BlockPos bed = freeBed(world.level(), s, inn, ledger, player.getUUID());
		if (bed == null) {
			return Result.FULL;
		}
		if (!Hooks.charge(player, cfg.bed(), keeper, s)) {
			return Result.CANNOT_PAY;
		}
		long checkout = checkout(now, cfg.checkout());
		ledger.beds.add(new SocialLedger.Rental(bed.asLong(), player.getUUID(), checkout));
		s.deposit(cfg.bed());
		s.markDirty();
		// keep the old respawn point to give back, unless it is an inn bed of ours already
		Optional<ServerPlayer.RespawnConfig> previous = Optional.ofNullable(player.getRespawnConfig());
		if (life.bed().isPresent()) {
			previous = life.bed().get().previous();
		}
		player.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(world.level().dimension(), bed, player.getYRot(), 0), false),
			false);
		PlayerLife.set(player, life.withBed(new PlayerLife.Bed(s.id(), bed, checkout, previous)));
		return Result.OK;
	}

	/** The town tick of the next checkout time after {@code now}. */
	static long checkout(long now, int checkoutMinute) {
		long day = SocialTime.day(now);
		long t = SocialTime.at(day, checkoutMinute);
		// the checkout belongs to the morning after the night; a bed taken in the small hours is kept until that morning
		while (t <= now || t - now < 6 * 1000) {
			t += SocialTime.DAY_TICKS;
		}
		return t;
	}

	/** A bed in the inn that no guest has and no family of the inn sleeps in, nearest the player first. */
	static @Nullable BlockPos freeBed(ServerLevel level, Settlement s, PlotView inn, SocialLedger ledger, UUID player) {
		List<Poi> beds = new ArrayList<>(ServiceSites.pois(s, inn, PoiType.BED));
		int family = 0;
		for (Resident r : s.residents()) {
			if (r.home == inn.id()) {
				family++;
			}
		}
		// the inn's own family sleeps in its first beds
		beds.sort((a, b) -> Long.compare(a.pos().asLong(), b.pos().asLong()));
		for (int i = family; i < beds.size(); i++) {
			BlockPos p = beds.get(i).pos();
			boolean taken = false;
			for (SocialLedger.Rental r : ledger.beds) {
				if (r.pos() == p.asLong() && !r.player().equals(player)) {
					taken = true;
					break;
				}
			}
			if (!taken && (!level.isLoaded(p) || level.getBlockState(p).getBlock() instanceof BedBlock)) {
				return p;
			}
		}
		return null;
	}

	/** Takes (or extends by a day) a room with a chest. Returns the chest's position, or null with the reason given. */
	public static Result rentRoom(ServerPlayer player, HearthWorld world, Settlement s, Resident keeper, BlockPos[] out) {
		SocialConfig.Inn cfg = SocialModule.config().inn();
		PlotView inn = ServiceSites.inn(s, keeper);
		if (inn == null) {
			return Result.NO_INN;
		}
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		long today = world.day();
		for (int i = 0; i < ledger.rooms.size(); i++) {
			SocialLedger.Rental r = ledger.rooms.get(i);
			if (r.player().equals(player.getUUID())) {
				if (!Hooks.charge(player, cfg.room(), keeper, s)) {
					return Result.CANNOT_PAY;
				}
				ledger.rooms.set(i, new SocialLedger.Rental(r.pos(), r.player(), Math.max(r.until(), today - 1) + 1));
				s.deposit(cfg.room());
				s.markDirty();
				out[0] = BlockPos.of(r.pos());
				return Result.ALREADY;
			}
		}
		BlockPos chest = freeChest(world.level(), s, inn, ledger);
		if (chest == null) {
			return Result.FULL;
		}
		if (!Hooks.charge(player, cfg.room(), keeper, s)) {
			return Result.CANNOT_PAY;
		}
		ledger.rooms.add(new SocialLedger.Rental(chest.asLong(), player.getUUID(), today));
		s.deposit(cfg.room());
		s.markDirty();
		out[0] = chest;
		return Result.OK;
	}

	/** A guest chest: one near a bed and away from the counter, not let to anyone. */
	static @Nullable BlockPos freeChest(ServerLevel level, Settlement s, PlotView inn, SocialLedger ledger) {
		List<Poi> chests = ServiceSites.pois(s, inn, PoiType.STORAGE);
		List<Poi> beds = ServiceSites.pois(s, inn, PoiType.BED);
		List<Poi> counters = new ArrayList<>(ServiceSites.pois(s, inn, PoiType.COUNTER));
		counters.addAll(ServiceSites.pois(s, inn, PoiType.JOB_SITE));
		BlockPos store = null;
		if (!counters.isEmpty() && !chests.isEmpty()) {
			// the chest nearest the counter is the inn's own store
			double best = Double.MAX_VALUE;
			for (Poi c : chests) {
				double d = c.pos().distSqr(counters.get(0).pos());
				if (d < best) {
					best = d;
					store = c.pos();
				}
			}
		}
		for (Poi c : chests) {
			BlockPos p = c.pos();
			if (p.equals(store) || rentedTo(ledger, p) != null) {
				continue;
			}
			boolean byBed = beds.isEmpty();
			for (Poi b : beds) {
				if (b.pos().distSqr(p) <= 5 * 5) {
					byBed = true;
					break;
				}
			}
			if (byBed && (!level.isLoaded(p) || level.getBlockEntity(p) instanceof Container)) {
				return p;
			}
		}
		return null;
	}

	/** Who a chest is let to (including the days of grace), or null. */
	public static @Nullable UUID rentedTo(SocialLedger ledger, BlockPos pos) {
		long key = pos.asLong();
		for (SocialLedger.Rental r : ledger.rooms) {
			if (r.pos() == key) {
				return r.player();
			}
		}
		return null;
	}

	/** A hot meal at the inn. */
	public static boolean meal(ServerPlayer player, Settlement s, Resident keeper) {
		SocialConfig.Inn cfg = SocialModule.config().inn();
		if (!Hooks.charge(player, cfg.meal(), keeper, s)) {
			return false;
		}
		s.deposit(cfg.meal());
		s.markDirty();
		player.getFoodData().eat(20, 0.8F);
		if (cfg.mealRegenSeconds() > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, cfg.mealRegenSeconds() * 20, 0), null);
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
		return true;
	}

	/**
	 * Gives back respawn points of beds whose night is over (run every few seconds for online players with a bed).
	 */
	public static void checkouts(ServerPlayer player, long now) {
		PlayerLife life = PlayerLife.of(player);
		if (life.bed().isEmpty()) {
			return;
		}
		PlayerLife.Bed bed = life.bed().get();
		if (now < bed.checkout()) {
			return;
		}
		ServerPlayer.RespawnConfig current = player.getRespawnConfig();
		if (current != null && current.respawnData().pos().equals(bed.pos())) {
			player.setRespawnPosition(bed.previous().orElse(null), false);
		}
		PlayerLife.set(player, life.withBed(null));
		player.sendSystemMessage(Component.translatable("hearth.social.inn.checked_out").withStyle(ChatFormatting.GRAY));
	}

	/** Which way is the bed from the player, in words ("up the stairs to the north"). */
	public static Component whereIs(ServerPlayer player, BlockPos target) {
		int dy = target.getY() - player.blockPosition().getY();
		double dx = target.getX() + 0.5 - player.getX();
		double dz = target.getZ() + 0.5 - player.getZ();
		Direction dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		String level = dy >= 3 ? "up" : dy <= -3 ? "down" : "here";
		return Component.translatable("hearth.social.where." + level, Component.translatable("hearth.social.dir." + dir.getSerializedName()));
	}
}
