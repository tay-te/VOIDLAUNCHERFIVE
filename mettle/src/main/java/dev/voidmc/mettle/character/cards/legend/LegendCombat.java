package dev.voidmc.mettle.character.cards.legend;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.cards.CardCooldowns;
import dev.voidmc.mettle.character.cards.CardHooks;
import dev.voidmc.mettle.character.cards.Mythics;
import dev.voidmc.mettle.character.skill.Combat;
import dev.voidmc.mettle.character.skill.blades.Swings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Misericorde, Red Harvest, Thunderhead, the Dread Maul, Hunter's Moon, the Pinning Star and the Iron Vow. Extra hits go
 * through {@code hurtServer}, so the card hooks and their ceilings (CardHooks.hurt, Ceilings) weigh them as any other.
 */
public final class LegendCombat {
	public static final String THUNDERHEAD = "thunderhead";
	public static final String DREAD_MAUL = "dread_maul";
	public static final String IRON_VOW = "iron_vow";
	/** Hunter's Moon's second arrow, which never looses a third. */
	public static final String SECOND_ARROW = "mettle.hunters_moon";
	/** A bolt Thunderhead called: it spares its caller and their allies. */
	public static final String BOLT = "mettle.thunderhead";
	/** A Thunderhead bolt over ground its caller may not build on lights no fire. */
	public static final String NO_FIRE = "mettle.thunderhead.no_fire";
	private static final Identifier PINNED = Mettle.id("legend/pinned");
	static final int RED_HARVEST_TICKS = 40;
	static final int FEAR_TICKS = 60;
	static final int PIN_TICKS = 40;
	static final int PIN_AGAIN_TICKS = 200;

	/** Red Harvest: the game time a holder's next swing sweeps until. */
	private static final Map<UUID, Long> SWEEP_UNTIL = new HashMap<>();
	/** Dread Maul: who flees, from where, until when. */
	private record Fear(Vec3 from, long until) {
	}

	private static final Map<Mob, Fear> FEARED = new WeakHashMap<>();
	private static final Map<LivingEntity, Long> ROOTED = new WeakHashMap<>();
	private static final Map<LivingEntity, Long> PIN_READY = new WeakHashMap<>();
	/** The Iron Vow: a player's health as a blow began, so a vow can give it back. */
	private static final Map<LivingEntity, Float> BEFORE = new WeakHashMap<>();
	/** The arrow hit being resolved (AbstractArrow#onHitEntity): its own damage, before the cards, and where it struck. */
	private static float arrowDamage = -1;
	private static @Nullable Vec3 arrowHit;
	private static boolean inner;

	private LegendCombat() {
	}

	private static boolean has(Player player, String id) {
		return Mythics.active(player, id);
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(LegendCombat::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(LegendCombat::afterDeath);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer && !inner) {
				BEFORE.put(entity, entity.getHealth());
			}
			return true;
		});
		// The vow is asked before the other death hooks (Pardon, Pact), so a blow it takes never spends theirs.
		Identifier first = Mettle.id("legend_first");
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(first, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(first, LegendCombat::allowDeath);
		ServerTickEvents.END_SERVER_TICK.register(LegendCombat::tick);
		ServerPlayerEvents.LEAVE.register(p -> SWEEP_UNTIL.remove(p.getUUID()));
	}

	// ---------------------------------------------------------------- hits

	private static void afterDamage(LivingEntity target, DamageSource source, float base, float taken, boolean blocked) {
		if (inner || !(source.getEntity() instanceof ServerPlayer player) || player == target || taken <= 0 || !target.isAlive()) {
			return;
		}
		ServerLevel level = (ServerLevel) target.level();
		Combat.Weapon weapon = Combat.weapon(source, player);
		// Misericorde: the mercy stroke, on a mob already below 15 % when the blade met it.
		if (weapon == Combat.Weapon.BLADE && target instanceof Mob && !LegendEffects.boss(target) && has(player, "misericorde")
			&& target.getHealth() + taken < target.getMaxHealth() * 0.15f) {
			finish(level, player, target);
			return;
		}
		// Thunderhead: a fully charged heavy hit under a thunderstorm's open sky.
		if (weapon == Combat.Weapon.HEAVY && Swings.strength(player) >= 0.9f && has(player, "thunderhead") && level.isThundering()
			&& level.canSeeSky(BlockPos.containing(target.getX(), target.getEyeY(), target.getZ())) && CardCooldowns.ready(player, THUNDERHEAD)) {
			CardCooldowns.start(player, THUNDERHEAD, 400);
			callLightning(level, player, target);
		}
		if (weapon == Combat.Weapon.RANGED && source.getDirectEntity() instanceof AbstractArrow arrow && !(arrow instanceof ThrownTrident)
			&& arrow.isCritArrow() && !arrow.entityTags().contains(SECOND_ARROW)) {
			// Hunter's Moon: a silent second arrow at the same target, for half the first one's damage.
			if (has(player, "hunters_moon") && underNightSky(player)) {
				loose(level, player, target, arrowDamage > 0 ? arrowDamage : base);
			}
			// The Pinning Star: struck in the lower half, a mob is rooted; the same one again only after 10 s.
			Vec3 hit = arrowHit != null ? arrowHit : arrow.position();
			if (target instanceof Mob && has(player, "pinning_star") && hit.y < target.getY() + target.getBbHeight() / 2
				&& level.getGameTime() >= PIN_READY.getOrDefault(target, 0L)) {
				pin(level, player, target);
			}
		}
	}

	private static void finish(ServerLevel level, ServerPlayer player, LivingEntity target) {
		inner = true;
		try {
			// The blade's own kill (credited, its loot and XP), past armour and the hurt frames.
			target.hurtServer(level, player.damageSources().source(DamageTypes.GENERIC_KILL, player), target.getMaxHealth() * 4 + 100);
		} finally {
			inner = false;
		}
	}

	private static void callLightning(ServerLevel level, ServerPlayer player, LivingEntity target) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt == null) {
			return;
		}
		bolt.snapTo(target.getX(), target.getY(), target.getZ());
		bolt.setCause(player);
		bolt.addTag(BOLT);
		if (!LegendEffects.mayBuild(player, level, target.blockPosition())) {
			bolt.addTag(NO_FIRE);
		}
		level.addFreshEntity(bolt);
		LegendEffects.chip(player, THUNDERHEAD, Component.translatable("mettle.legend.thunderhead"));
	}

	/** LightningBolt#tick's strike on each entity near: a Thunderhead bolt spares its caller and the caller's allies. */
	public static boolean spares(LightningBolt bolt, Entity entity) {
		if (!bolt.entityTags().contains(BOLT) || !(bolt.getCause() instanceof ServerPlayer caller)) {
			return false;
		}
		return entity == caller || entity instanceof Player p && CardHooks.helps(caller, p);
	}

	/** LightningBolt#spawnFire: never on ground the caller may not build on. */
	public static boolean fireless(LightningBolt bolt) {
		return bolt.entityTags().contains(NO_FIRE);
	}

	/** Night by the clock (dusk to dawn, when beds work), not a storm's gloom, and the sky open above. */
	private static boolean underNightSky(ServerPlayer player) {
		Level level = player.level();
		long time = Math.floorMod(level.getDefaultClockTime(), 24000L);
		return !level.dimensionType().hasFixedTime() && time >= 12542 && time < 23460 && level.canSeeSky(BlockPos.containing(player.getEyePosition()));
	}

	private static void loose(ServerLevel level, ServerPlayer player, LivingEntity target, float damage) {
		Arrow second = new Arrow(level, player, new ItemStack(Items.ARROW), null);
		second.addTag(SECOND_ARROW);
		second.setSilent(true);
		second.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
		Vec3 to = target.getBoundingBox().getCenter().subtract(second.position());
		// Speed 3 (a full draw); arrows deal speed × base damage, rounded up.
		second.setBaseDamage(damage / 2.0 / 3.0);
		second.shoot(to.x, to.y + to.horizontalDistance() * 0.02, to.z, 3.0f, 0f);
		level.addFreshEntity(second);
	}

	/** Around AbstractArrow#onHitEntity's hurt: what the arrow deals before the cards, and where it struck. */
	public static void arrowHits(AbstractArrow arrow, Entity target, float damage, Vec3 at) {
		arrowDamage = damage;
		arrowHit = at;
		// The second arrow follows the first within its hurt frames; its half must still land.
		if (arrow.entityTags().contains(SECOND_ARROW) && target instanceof LivingEntity living) {
			living.damageCooldownTime = 0;
		}
	}

	public static void arrowDone() {
		arrowDamage = -1;
		arrowHit = null;
	}

	private static void pin(ServerLevel level, ServerPlayer player, LivingEntity target) {
		long now = level.getGameTime();
		PIN_READY.put(target, now + PIN_AGAIN_TICKS);
		ROOTED.put(target, now + PIN_TICKS);
		for (Holder<Attribute> attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED, Attributes.JUMP_STRENGTH)) {
			AttributeInstance a = target.getAttribute(attribute);
			if (a != null && a.getModifier(PINNED) == null) {
				a.addTransientModifier(new AttributeModifier(PINNED, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
		target.needsSync = true;
		LegendEffects.chip(player, "pinning_star", Component.translatable("mettle.legend.pinning_star", target.getDisplayName()));
	}

	private static void unpin(LivingEntity target) {
		for (Holder<Attribute> attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED, Attributes.JUMP_STRENGTH)) {
			AttributeInstance a = target.getAttribute(attribute);
			if (a != null) {
				a.removeModifier(PINNED);
			}
		}
	}

	/** Whether the Pinning Star holds this entity now (tests). */
	public static boolean pinned(LivingEntity entity) {
		Long until = ROOTED.get(entity);
		return until != null && entity.level().getGameTime() <= until;
	}

	// ---------------------------------------------------------------- kills

	private static void afterDeath(LivingEntity killed, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || player == killed) {
			return;
		}
		Combat.Weapon weapon = Combat.weapon(source, player);
		long now = player.level().getGameTime();
		// Red Harvest: the swing is ready at once, and the next one within 2 s sweeps.
		if (weapon == Combat.Weapon.BLADE && has(player, "red_harvest")) {
			Swings.charge(player);
			SWEEP_UNTIL.put(player.getUUID(), now + RED_HARVEST_TICKS);
		}
		// The Dread Maul: hostiles within 6 blocks flee for 3 s.
		if (weapon == Combat.Weapon.HEAVY && has(player, "dread_maul") && CardCooldowns.ready(player, DREAD_MAUL)) {
			List<Mob> near = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(6),
				m -> m != killed && m.isAlive() && LegendEffects.hostile(m) && m.distanceToSqr(player) <= 36);
			if (!near.isEmpty()) {
				CardCooldowns.start(player, DREAD_MAUL, 300);
				for (Mob m : near) {
					FEARED.put(m, new Fear(player.position(), now + FEAR_TICKS));
					m.setTarget(null);
					flee(m, FEARED.get(m));
				}
				LegendEffects.chip(player, DREAD_MAUL, Component.translatable("mettle.legend.dread_maul", near.size()));
			}
		}
	}

	/** Player#isSweepAttack: a Red Harvest holder's next blade swing after a kill sweeps. Each kill readies one sweep. */
	public static boolean sweeps(Player player, boolean vanilla) {
		if (!(player instanceof ServerPlayer sp)) {
			return vanilla;
		}
		Long until = SWEEP_UNTIL.remove(sp.getUUID());
		if (vanilla || until == null || sp.level().getGameTime() > until) {
			return vanilla;
		}
		return Combat.melee(sp.getMainHandItem()) == Combat.Weapon.BLADE && has(sp, "red_harvest");
	}

	/** Whether a Red Harvest sweep waits for this player's next swing (tests). */
	public static boolean sweepReady(ServerPlayer player) {
		Long until = SWEEP_UNTIL.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until;
	}

	/** Mob#setTarget: a mob fleeing the Dread Maul takes no target. */
	public static boolean afraid(Mob mob) {
		Fear f = FEARED.get(mob);
		return f != null && mob.level().getGameTime() <= f.until();
	}

	private static void flee(Mob mob, Fear fear) {
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		if (mob instanceof Creeper creeper) {
			creeper.setSwellDir(-1);
		}
		if (mob instanceof PathfinderMob pm) {
			Vec3 away = DefaultRandomPos.getPosAway(pm, 12, 7, fear.from());
			if (away == null) {
				// No spot found at random: straight away from the killer, as far as a path goes.
				Vec3 dir = pm.position().subtract(fear.from()).multiply(1, 0, 1);
				away = pm.position().add(dir.lengthSqr() < 1e-4 ? new Vec3(8, 0, 0) : dir.normalize().scale(8));
			}
			pm.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
		}
	}

	// ---------------------------------------------------------------- the Iron Vow

	/**
	 * The blow before Player#hurtServer scaled it by difficulty (the ally's {@code amount} is already scaled; the holder's
	 * hurtServer scales it again, so it is turned back first): easy halves it plus one, hard adds half.
	 */
	static float unscaled(ServerLevel level, DamageSource source, float amount) {
		if (!source.scalesWithDifficulty()) {
			return amount;
		}
		return switch (level.getDifficulty()) {
			case EASY -> amount >= 2 ? (amount - 1) * 2 : amount;
			case HARD -> amount * 2 / 3;
			default -> amount;
		};
	}

	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (inner || !(entity instanceof ServerPlayer ally) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			|| source.getEntity() == null && source.getDirectEntity() == null) {
			return true;
		}
		ServerLevel level = (ServerLevel) ally.level();
		for (ServerPlayer holder : level.players()) {
			if (holder == ally || !holder.isAlive() || holder.isCreative() || holder.isSpectator() || holder.distanceToSqr(ally) > 64
				|| source.getEntity() == holder || !CardHooks.helps(holder, ally) || !has(holder, "iron_vow") || !CardCooldowns.ready(holder, IRON_VOW)) {
				continue;
			}
			CardCooldowns.start(holder, IRON_VOW, 24000);
			ally.setHealth(Math.max(1, BEFORE.getOrDefault(ally, 1f)));
			// The blow as it came, before the ally's armour: the holder's own armour and cards weigh it now.
			inner = true;
			int frames = holder.damageCooldownTime;
			try {
				holder.damageCooldownTime = 0;
				holder.hurtServer(level, source, unscaled(level, source, amount));
			} finally {
				inner = false;
				if (holder.isAlive()) {
					holder.damageCooldownTime = Math.max(frames, holder.damageCooldownTime);
				}
			}
			LegendEffects.chip(holder, IRON_VOW, Component.translatable("mettle.legend.iron_vow", ally.getDisplayName()));
			LegendEffects.chip(ally, IRON_VOW, Component.translatable("mettle.legend.iron_vow.saved", holder.getDisplayName()));
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- every tick

	private static void tick(MinecraftServer server) {
		if (!FEARED.isEmpty()) {
			List<Mob> done = new ArrayList<>();
			for (Map.Entry<Mob, Fear> e : FEARED.entrySet()) {
				Mob mob = e.getKey();
				if (!mob.isAlive() || mob.level().getGameTime() > e.getValue().until()) {
					done.add(mob);
					continue;
				}
				if (mob.getTarget() != null) {
					mob.setTarget(null);
				}
				if (mob.tickCount % 10 == 0) {
					flee(mob, e.getValue());
				} else {
					mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
				}
			}
			done.forEach(FEARED::remove);
		}
		if (!ROOTED.isEmpty()) {
			List<LivingEntity> done = new ArrayList<>();
			for (Map.Entry<LivingEntity, Long> e : ROOTED.entrySet()) {
				if (!e.getKey().isAlive() || e.getKey().level().getGameTime() > e.getValue()) {
					done.add(e.getKey());
				}
			}
			for (LivingEntity entity : done) {
				ROOTED.remove(entity);
				unpin(entity);
			}
		}
		if (server.getTickCount() % 200 == 0) {
			long now = server.overworld().getGameTime();
			PIN_READY.values().removeIf(at -> at < now);
			SWEEP_UNTIL.values().removeIf(at -> at < now);
		}
	}
}
