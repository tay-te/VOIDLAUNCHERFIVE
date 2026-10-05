package dev.voidmc.mettle.character.skill.survival;

import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.SurvivalSkill;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

/**
 * The Hunter half of Survival: kill XP for animals (to whoever dealt at least half the damage), Skinner and Big Game's
 * extra meat and hide, Stalker's unseen kill, Quiet Approach's calm herds and Sixth Sense's heartbeat.
 */
public final class Hunting {
	/** Damage each player dealt to an animal, and the total from every source; weak so dead animals drop out. */
	private static final Map<LivingEntity, Ledger> LEDGERS = new WeakHashMap<>();
	/** An animal's health as the latest hit began (LivingEntity#hurtServer), for the blow that kills it. */
	private static final Map<LivingEntity, Float> HEALTH_BEFORE = new WeakHashMap<>();
	/** Animals whose drops were already dressed, so Field Dressing's second roll doesn't double Skinner or Big Game. */
	private static final Set<LivingEntity> DRESSED = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<UUID, Long> SENSED_AT = new HashMap<>();
	/** Heartbeats still to play: game time due, and pitch. */
	private static final Map<UUID, List<long[]>> BEATS = new HashMap<>();
	private static final Map<UUID, int[]> LAST_DRESSING = new HashMap<>();

	private static final class Ledger {
		final Map<UUID, Float> byPlayer = new HashMap<>();
		float total;
	}

	private Hunting() {
	}

	public static void init() {
		// A killing blow dies inside hurtServer, before AFTER_DAMAGE; afterDeath books that last hit itself.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseTaken, taken, blocked) -> {
			if (entity instanceof Animal && taken > 0 && entity.isAlive()) {
				book(entity, source, Math.min(taken, entity.getMaxHealth()));
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(Hunting::afterDeath);
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (context.getOptional(LootContextParams.THIS_ENTITY) instanceof Animal animal
				&& context.getOptional(LootContextParams.LAST_DAMAGE_PLAYER) instanceof ServerPlayer player
				&& !animal.isBaby() && !drops.isEmpty()) {
				dress(player, animal, drops);
			}
		});
	}

	/** A wild animal: one nobody owns (tamed wolves, horses and cats are not game). */
	public static boolean wild(LivingEntity entity) {
		return entity instanceof Animal && !(entity instanceof OwnableEntity o && o.getOwnerReference() != null);
	}

	// ---------------------------------------------------------------- kills

	private static void book(LivingEntity entity, DamageSource source, float amount) {
		Ledger ledger = LEDGERS.computeIfAbsent(entity, e -> new Ledger());
		ledger.total += amount;
		if (source.getEntity() instanceof ServerPlayer player) {
			ledger.byPlayer.merge(player.getUUID(), amount, Float::sum);
		}
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		Float before = HEALTH_BEFORE.remove(entity);
		if (entity instanceof Animal) {
			// The killing blow took whatever health was left.
			book(entity, source, before == null ? entity.getMaxHealth() : Math.min(before, entity.getMaxHealth()));
		}
		Ledger ledger = LEDGERS.remove(entity);
		if (ledger == null || !wild(entity) || entity.isBaby() || !(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}
		for (Map.Entry<UUID, Float> e : ledger.byPlayer.entrySet()) {
			if (!SurvivalRules.ownsKill(e.getValue(), ledger.total)) {
				continue;
			}
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				return;
			}
			SurvivalSkill.award(player, "animal", SurvivalRules.XP_ANIMAL);
			if (entity.is(SurvivalSkill.BIG_GAME_ANIMALS) && !player.isCreative()) {
				FeatCounters.add(player, SurvivalSkill.BIG_GAME_KILLS, 1);
			}
			return;
		}
	}

	/** Skinner (+1 meat, +1 hide) and Big Game (meat and hide doubled), once per animal, on its first loot roll. */
	private static void dress(ServerPlayer player, Animal animal, List<ItemStack> drops) {
		boolean skinner = Perks.has(player, SurvivalSkill.SKINNER);
		boolean bigGame = animal.is(SurvivalSkill.BIG_GAME_ANIMALS) && Perks.has(player, SurvivalSkill.BIG_GAME);
		if ((!skinner && !bigGame) || !DRESSED.add(animal)) {
			return;
		}
		int[] added = extraDrops(drops, skinner, bigGame);
		LAST_DRESSING.put(player.getUUID(), added);
		if (added[0] + added[1] > 0) {
			if (bigGame) {
				SurvivalSkill.chipThrottled(player, SurvivalSkill.BIG_GAME, 20, "big_game");
			} else {
				SurvivalSkill.chipThrottled(player, SurvivalSkill.SKINNER, 100, "skinner");
			}
		}
	}

	/**
	 * Adds the extra meat and hide to a kill's drops and returns how many of each were added. Big Game copies the first
	 * meat and first hide stack (doubling what the animal itself dropped, not other perks' extras); Skinner adds one of
	 * each.
	 */
	public static int[] extraDrops(List<ItemStack> drops, boolean skinner, boolean bigGame) {
		ItemStack meat = null;
		ItemStack hide = null;
		for (ItemStack d : drops) {
			if (meat == null && d.is(SurvivalSkill.GAME_MEAT)) {
				meat = d;
			} else if (hide == null && d.is(SurvivalSkill.HIDES)) {
				hide = d;
			}
		}
		List<ItemStack> extra = new ArrayList<>();
		int meatAdded = 0;
		int hideAdded = 0;
		if (bigGame) {
			if (meat != null) {
				extra.add(meat.copy());
				meatAdded += meat.getCount();
			}
			if (hide != null) {
				extra.add(hide.copy());
				hideAdded += hide.getCount();
			}
		}
		if (skinner) {
			if (meat != null) {
				extra.add(meat.copyWithCount(1));
				meatAdded++;
			}
			if (hide != null) {
				extra.add(hide.copyWithCount(1));
				hideAdded++;
			}
		}
		drops.addAll(extra);
		return new int[] {meatAdded, hideAdded};
	}

	/** Test hook: meat and hide the player's last dressed kill gained. */
	public static int @Nullable [] lastDressing(ServerPlayer player) {
		return LAST_DRESSING.get(player.getUUID());
	}

	// ---------------------------------------------------------------- Stalker (LivingEntity#hurtServer)

	public static float damage(LivingEntity target, DamageSource source, float damage) {
		if (target instanceof Animal) {
			HEALTH_BEFORE.put(target, target.getHealth() + target.getAbsorptionAmount());
		}
		if (damage <= 0 || !(source.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown() || !wild(target)
			|| !Perks.has(player, SurvivalSkill.STALKER) || noticed((Animal) target, player)) {
			return damage;
		}
		SurvivalSkill.chip(player, SurvivalSkill.STALKER, "stalker");
		// Enough to go through any armour or resistance an animal could have.
		return Math.max(damage, (target.getHealth() + target.getAbsorptionAmount()) * 10f + 20f);
	}

	/** Whether an animal knows the player is there: hurt lately, angry, or already after someone. */
	public static boolean noticed(Animal animal, Player player) {
		if (animal.getLastHurtByMob() != null && animal.tickCount - animal.getLastHurtByMobTimestamp() < 200) {
			return true;
		}
		if (animal.getTarget() != null || animal.isAggressive()) {
			return true;
		}
		return animal instanceof NeutralMob neutral && neutral.isAngryAt(player, (net.minecraft.server.level.ServerLevel) animal.level());
	}

	// ---------------------------------------------------------------- Quiet Approach (AvoidEntityGoal#canUse)

	/** True when the animal should not flee from {@code avoided}: a sneaking Quiet Approach player still 4+ blocks out. */
	public static boolean staysCalm(Mob mob, @Nullable LivingEntity avoided) {
		return mob instanceof Animal && avoided instanceof Player player && player.isShiftKeyDown()
			&& mob.distanceToSqr(player) > SurvivalRules.QUIET_APPROACH_RANGE * SurvivalRules.QUIET_APPROACH_RANGE
			&& Perks.has(player, SurvivalSkill.QUIET_APPROACH);
	}

	// ---------------------------------------------------------------- Sixth Sense (Mob#setTarget)

	public static void targeted(Mob mob, @Nullable LivingEntity before, @Nullable LivingEntity after) {
		if (!(after instanceof ServerPlayer player) || after == before || !(mob instanceof Enemy) || mob.level().isClientSide()
			|| mob.distanceToSqr(player) > SurvivalRules.SIXTH_SENSE_RANGE * SurvivalRules.SIXTH_SENSE_RANGE
			|| !Perks.has(player, SurvivalSkill.SIXTH_SENSE) || player.isCreative() || player.isSpectator()) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = SENSED_AT.get(player.getUUID());
		if (last != null && now - last < SurvivalRules.SIXTH_SENSE_COOLDOWN && now >= last) {
			return;
		}
		SENSED_AT.put(player.getUUID(), now);
		// Three beats, each closer to the last and a little higher: a heart that quickens.
		List<long[]> beats = new ArrayList<>();
		beats.add(new long[] {now, 90});
		beats.add(new long[] {now + 14, 100});
		beats.add(new long[] {now + 24, 112});
		BEATS.put(player.getUUID(), beats);
		SurvivalSkill.chipThrottled(player, SurvivalSkill.SIXTH_SENSE, 100, "sixth_sense");
	}

	/** Test hook: game time of the player's last Sixth Sense warning, or -1. */
	public static long lastSensed(ServerPlayer player) {
		return SENSED_AT.getOrDefault(player.getUUID(), -1L);
	}

	public static void tick(ServerPlayer player, int tick) {
		List<long[]> beats = BEATS.get(player.getUUID());
		if (beats == null) {
			return;
		}
		long now = player.level().getGameTime();
		for (Iterator<long[]> it = beats.iterator(); it.hasNext();) {
			long[] beat = it.next();
			if (beat[0] <= now) {
				it.remove();
				// Heard by this player only, from inside their own head.
				player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.WARDEN_HEARTBEAT),
					SoundSource.PLAYERS, player.getX(), player.getEyeY(), player.getZ(), 0.7f, beat[1] / 100f, player.getRandom().nextLong()));
			}
		}
		if (beats.isEmpty()) {
			BEATS.remove(player.getUUID());
		}
	}

	public static void forget(UUID player) {
		SENSED_AT.remove(player);
		BEATS.remove(player);
		LAST_DRESSING.remove(player);
	}

	public static void clear() {
		LEDGERS.clear();
		HEALTH_BEFORE.clear();
		DRESSED.clear();
		SENSED_AT.clear();
		BEATS.clear();
		LAST_DRESSING.clear();
	}
}
