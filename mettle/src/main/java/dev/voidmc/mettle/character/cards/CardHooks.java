package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.MettleEvents;
import dev.voidmc.mettle.api.MettleServices;
import dev.voidmc.mettle.character.PlacedBlocks;
import dev.voidmc.mettle.character.skill.Combat;
import dev.voidmc.mettle.character.skill.MiningSkill;
import dev.voidmc.mettle.character.skill.SurvivalSkill;
import dev.voidmc.mettle.character.skill.blades.BleedDamage;
import dev.voidmc.mettle.character.skill.blades.Swings;
import dev.voidmc.mettle.character.skill.heavy.Stagger;
import dev.voidmc.mettle.character.skill.heavy.Trophies;
import dev.voidmc.mettle.character.skill.heavy.Woodcraft;
import dev.voidmc.mettle.character.skill.survival.Foraging;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import dev.voidmc.mettle.mixin.blades.BladesLivingAccess;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * The behaviour traits and set bonuses of cards v2 (docs/CARDS.md §4.2, §6.3): one place where every hook asks how much
 * of a trait or which set tier a player has active, from the synced card cache. Hooks are reached from Mettle's own
 * mixins ({@code mixin.cards}), from the skills' existing hooks (draw speed, shots, shields, crafting) and from Fabric
 * events. Values are percent traits as fractions unless the trait is flat.
 */
public final class CardHooks {
	private CardHooks() {
	}

	// ---------------------------------------------------------------- reading

	private static Identifier t(String path) {
		return Mettle.id(path);
	}

	/** A percent trait as a fraction (9 % → 0.09), 0 when not active. Both sides. */
	public static double pct(Player player, String trait) {
		return CardStore.get(player).trait(t(trait)) / 100.0;
	}

	/** A flat trait's value (armour, hearts, seconds, kg), 0 when not active. */
	public static double flat(Player player, String trait) {
		return CardStore.get(player).trait(t(trait));
	}

	public static int set(Player player, String keyword) {
		Identifier k = t(keyword);
		// A Dragon Standard (Dragonfall) may lend this player a set tier they don't have.
		return Math.max(CardStore.get(player).tier(k), MythicEffects.borrowed(player).getOrDefault(k, 0));
	}

	private static boolean any(Player player) {
		CardsState s = CardStore.get(player);
		return !s.traits().isEmpty() || !s.tiers().isEmpty() || !MythicEffects.borrowed(player).isEmpty();
	}

	private static boolean roll(Player player, double chance) {
		return chance > 0 && player.getRandom().nextDouble() < chance;
	}

	// ---------------------------------------------------------------- per-player memory (server, never saved)

	private static final class Memory {
		long sprintStart = -1;
		boolean chargeSpent;
		int tempoTarget = -1;
		long tempoAt;
		int tempoStacks;
		long rageUntil;
		int rageStacks;
		long flourishUntil;
		double flourish;
		long lastHurt;
		long lastHeal;
		double healBudget;
		long bloodAt;
		double bloodBudget;
		Vec3 lastPos = Vec3.ZERO;
		long stillSince;
		Set<Integer> keenFirst = new HashSet<>();
		long kindledUntil;
		long underdarkReady;
		long slipArmedUntil;
		long ironskinReady;
		long bodyguardReady;
		int weakTarget = -1;
		long weakFirst;
		int weakHits;
		/** Seamfinder: the ore veins broken into lately (their block positions), the newest last. */
		Deque<Set<Long>> veins = new ArrayDeque<>();
	}

	private static final Map<UUID, Memory> MEMORY = new HashMap<>();
	/** Targets marked by Marking: the bonus and when it ends. */
	private static final Map<LivingEntity, double[]> MARKED = new WeakHashMap<>();
	/** Arrows loosed while the shooter stood still (Poised). */
	private static final Map<Entity, Boolean> POISED = new WeakHashMap<>();
	/** Card bleeds: damage left and pulses left. */
	private static final Map<LivingEntity, double[]> BLEEDS = new IdentityHashMap<>();
	private static final Map<LivingEntity, UUID> BLEED_BY = new IdentityHashMap<>();
	private static boolean inner;
	/** Overkill (Brutal 6): the hit that is about to kill, and the health it met. */
	private static final Map<LivingEntity, float[]> OVERKILL = new WeakHashMap<>();
	/** Weak Point (Keen 6): when each target may be exposed again. */
	private static final Map<LivingEntity, Long> WEAK_READY = new WeakHashMap<>();
	private static final Identifier PACT = Mettle.id("set/hex/6/pact");
	/**
	 * What a hit being worked out does besides its damage (cooldowns spent, marks, counters, an ally's share), run only
	 * when vanilla lets the hit land ({@link #landed}): a hit turned away by invulnerability frames changes nothing.
	 */
	private static final Map<LivingEntity, List<Runnable>> PENDING = new WeakHashMap<>();
	private static @org.jspecify.annotations.Nullable List<Runnable> later;
	/** The share of the hit being taken that cards left (for Ironskin, after armour, to stay under the ceiling). */
	private static final Map<LivingEntity, Double> TAKEN_FACTOR = new WeakHashMap<>();
	/** Magpie's extra roll is under way (its own drops are not rolled again). */
	private static boolean rolling;
	/** Hoard 4: one more of a natural drop one time in 25. */
	static final double HOARD = 1 / 25.0;

	private static Memory memory(Player player) {
		return MEMORY.computeIfAbsent(player.getUUID(), u -> new Memory());
	}

	private static final Identifier TEMPO = Mettle.id("trait_timed/tempo");
	private static final Identifier FLOURISH = Mettle.id("trait_timed/flourish");
	private static final Identifier LONG_WINDED = Mettle.id("trait_timed/long_winded");
	private static final Identifier PACESETTING = Mettle.id("trait_timed/pacesetting");
	private static final Identifier FERAL = Mettle.id("trait_timed/feral");

	private static final Set<EntityType<?>> FLYING = Set.of(EntityTypes.PHANTOM, EntityTypes.GHAST, EntityTypes.BLAZE, EntityTypes.BAT,
		EntityTypes.BEE, EntityTypes.VEX, EntityTypes.ALLAY, EntityTypes.PARROT, EntityTypes.ENDER_DRAGON, EntityTypes.WITHER, EntityTypes.BREEZE,
		EntityTypes.HAPPY_GHAST);

	static void init() {
		CardCooldowns.init();
		ServerTickEvents.END_SERVER_TICK.register(CardHooks::tick);
		ServerPlayerEvents.LEAVE.register(p -> {
			MEMORY.remove(p.getUUID());
			Cards.forgetChips(p.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(CardHooks::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(CardHooks::afterDeath);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
				broke(sl, sp, pos, state);
			}
		});
		LootTableEvents.MODIFY_DROPS.register(CardHooks::drops);
		MettleEvents.MODIFY_XP.register((player, skill, xp) -> xp * xpBonus(player, skill));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !(entity instanceof ServerPlayer p && pact(p)));
		net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer p) {
				AttributeInstance health = p.getAttribute(Attributes.MAX_HEALTH);
				if (health != null && health.getModifier(PACT) != null) {
					health.removeModifier(PACT);
				}
			}
		});
		MettleServices.addBandModifier((player, rules) -> {
			double later = flat(player, "athletics/burden_wise");
			return later <= 0 ? rules : new dev.voidmc.mettle.api.BandRules((float) (rules.burdened() + later / 100.0), rules.overburdened(),
				rules.pinned(), rules.penaltyScale(), rules.canBePinned());
		});
		MettleServices.addWeightModifier(CardHooks::weight);
	}

	// ---------------------------------------------------------------- digging (both sides, so breaking stays predicted)

	/** Player#getDestroySpeed: Deepborn, Rockbreaker, Netherborn, Surefooted, Woodcutter's, the Deep set. */
	public static float destroySpeed(Player player, BlockState state, float speed) {
		if (speed <= 0 || !any(player)) {
			return speed;
		}
		double m = 1;
		boolean below = player.getY() < 0;
		if (below) {
			m *= 1 + pct(player, "mining/deepborn");
			if (set(player, "deep") >= 2) {
				m *= 1.05;
			}
		}
		if (MiningSkill.isStoneLike(state)) {
			m *= 1 + pct(player, "mining/rockbreaker");
		}
		if (state.is(BlockTags.BASE_STONE_NETHER) || state.is(Blocks.ANCIENT_DEBRIS) || state.is(Blocks.SMOOTH_BASALT)) {
			m *= 1 + pct(player, "mining/netherborn");
		}
		if (Woodcraft.isLog(state) && player.getMainHandItem().is(ItemTags.AXES)) {
			m *= 1 + pct(player, "heavy_weapons/woodcutters");
		}
		// The dig ceiling (docs/CARDS.md §14): these and the cursed cards' break speed (already in the speed) at most ×2.2.
		AttributeInstance breakSpeed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
		m = Ceilings.dig(breakSpeed == null ? 1 : Ceilings.cardFactor(breakSpeed), m);
		m *= unsteady(player, pct(player, "mining/surefooted"));
		return (float) (speed * m);
	}

	/**
	 * Surefooted: vanilla digs at a fifth of the speed off the ground, and slower in water (submerged mining speed); each
	 * of those slownesses is X smaller. It only takes back vanilla's slowness, so it is not counted in the ceiling.
	 */
	static double unsteady(Player player, double steady) {
		if (steady <= 0) {
			return 1;
		}
		double m = 1;
		if (player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
			double wet = player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
			if (wet > 0 && wet < 1) {
				m *= (1 - (1 - wet) * (1 - steady)) / wet;
			}
		}
		if (!player.onGround()) {
			m *= (1 - 0.8 * (1 - steady)) / 0.2;
		}
		return m;
	}

	// ---------------------------------------------------------------- damage

	/**
	 * LivingEntity#hurtServer's incoming damage: traits of whoever hits, of whoever is hit, and sets around them, held to
	 * the melee and damage-taken ceilings (docs/CARDS.md §14). What the hit does besides its damage waits in
	 * {@link #PENDING} until vanilla lets it land ({@link #landed}).
	 */
	public static float hurt(LivingEntity target, DamageSource source, float damage) {
		if (damage <= 0 || inner) {
			return damage;
		}
		PENDING.remove(target);
		TAKEN_FACTOR.remove(target);
		List<Runnable> effects = new ArrayList<>();
		later = effects;
		double m = 1;
		try {
			Entity attackerEntity = source.getEntity();
			double[] mark = MARKED.get(target);
			double marked = mark != null && attackerEntity != null && target.level().getGameTime() <= mark[1] ? 1 + mark[0] : 1;
			ServerPlayer attacker = attackerEntity instanceof ServerPlayer p && p != target ? p : null;
			if (attacker != null) {
				double hit = (any(attacker) ? dealt(attacker, target, source, damage) : 1) * MythicEffects.dealt(attacker) * marked;
				if (source.getDirectEntity() == attacker) {
					// A melee hit: what cards put in attack damage counts too, and the Twin Edge's quarter more.
					AttributeInstance attack = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
					boolean twin = Combat.weapon(source, attacker) == Combat.Weapon.BLADE && Mythics.active(attacker, "twin_edge");
					hit = Ceilings.melee(attack == null ? 1 : Ceilings.cardFactor(attack), hit, twin);
				}
				m *= pvp(target, hit);
			} else {
				m *= marked;
			}
			if (attackerEntity instanceof OwnableEntity pet && attackerEntity instanceof LivingEntity && pet.getOwner() instanceof ServerPlayer owner
				&& owner.distanceToSqr(attackerEntity) < 256) {
				m *= 1 + pct(owner, "survival/beastfriend") + (set(owner, "wild") >= 4 ? 0.15 : 0);
			}
			if (target instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer owner && owner.distanceToSqr(target) < 256) {
				m *= Math.max(0.2, 1 - pct(owner, "survival/beastfriend") - (set(owner, "wild") >= 4 ? 0.25 : 0));
			}
			if (target instanceof ServerPlayer victim) {
				boolean pvpOff = attacker != null && "off".equals(CardConfig.get().pvp_perks);
				double t = pvpOff ? 1 : Ceilings.taken(taken(victim, source, damage) * MythicEffects.taken(victim));
				if (!pvpOff) {
					t = bodyguard(victim, source, damage * m, t);
				}
				t = attacker != null ? pvp(victim, t) : t;
				TAKEN_FACTOR.put(victim, t);
				m *= t;
			}
			if (attacker != null && set(attacker, "brutal") >= 6 && target instanceof Mob) {
				// Overkill: the blow as it lands and the health it meets.
				float blow = (float) (damage * m);
				later(() -> OVERKILL.put(target, new float[] {blow, target.getHealth()}));
			}
		} finally {
			later = null;
		}
		if (!effects.isEmpty()) {
			PENDING.put(target, effects);
		}
		return (float) (damage * m);
	}

	/** Runs (or keeps for {@link #landed}) what a hit does besides its damage. */
	private static void later(Runnable effect) {
		if (later != null) {
			later.add(effect);
		} else {
			effect.run();
		}
	}

	/** LivingEntity#hurtServer, just before actuallyHurt: the hit got past invulnerability and its frames, so it counts. */
	public static void landed(LivingEntity target) {
		List<Runnable> effects = PENDING.remove(target);
		if (effects != null) {
			effects.forEach(Runnable::run);
		}
	}

	/** LivingEntity#hurtServer returned: a hit that never landed leaves nothing behind. */
	public static void settled(LivingEntity target) {
		PENDING.remove(target);
		TAKEN_FACTOR.remove(target);
	}

	/** {@code pvp_perks} (cards.json, docs/CARDS.md §4.1): between players, cards' share of a hit is halved or off. */
	private static double pvp(Entity target, double factor) {
		if (!(target instanceof Player)) {
			return factor;
		}
		return switch (CardConfig.get().pvp_perks) {
			case "off" -> 1;
			case "reduced" -> 1 + (factor - 1) / 2;
			default -> factor;
		};
	}

	/**
	 * LivingEntity#getDamageAfterMagicAbsorb: Ironskin (Iron 6) takes 3 off the first hit every 8 s, after armour, never
	 * more than leaves the hit within the damage-taken ceiling with the rest of the cards.
	 */
	public static float afterArmour(LivingEntity entity, DamageSource source, float damage) {
		if (inner || damage <= 0 || !(entity instanceof ServerPlayer player) || set(player, "iron") < 6) {
			return damage;
		}
		boolean pvp = source.getEntity() instanceof Player other && other != player;
		String mode = CardConfig.get().pvp_perks;
		if (pvp && "off".equals(mode)) {
			return damage;
		}
		Memory mem = memory(player);
		long now = player.level().getGameTime();
		if (now < mem.ironskinReady) {
			return damage;
		}
		double before = TAKEN_FACTOR.getOrDefault(player, 1.0);
		double floor = before <= 0 ? damage : damage / before * Ceilings.TAKEN;
		float lighter = (float) Math.min(pvp && "reduced".equals(mode) ? 1.5 : 3.0, damage - floor);
		if (lighter <= 0) {
			return damage;
		}
		mem.ironskinReady = now + 160;
		return damage - lighter;
	}

	private static double dealt(ServerPlayer player, LivingEntity target, DamageSource source, float damage) {
		double m = 1;
		Combat.Weapon weapon = Combat.weapon(source, player);
		boolean crit = false;
		if (weapon == Combat.Weapon.BLADE) {
			crit = Swings.critical(player);
			if (crit && !Swings.swept(player, target)) {
				m *= 1 + pct(player, "blades/keen");
			}
			if (Swings.swept(player, target)) {
				m *= 1 + pct(player, "blades/sweeping");
			}
			if (target.getHealth() < target.getMaxHealth() * 0.3f) {
				m *= 1 + pct(player, "blades/finishing");
			}
			if (target instanceof Mob mob && mob.getTarget() != player) {
				m *= 1 + pct(player, "blades/ambushing");
			}
		} else if (weapon == Combat.Weapon.HEAVY) {
			crit = Swings.critical(player);
			double crush = pct(player, "heavy_weapons/crushing");
			if (crush > 0) {
				double armour = Math.min(20, target.getArmorValue());
				double before = 1 - armour / 25.0;
				double after = 1 - armour * (1 - crush) / 25.0;
				m *= after / before;
			}
			if (source.is(DamageTypes.MACE_SMASH)) {
				m *= 1 + pct(player, "heavy_weapons/thunderous");
			}
			Memory mem = memory(player);
			long now = player.level().getGameTime();
			double charge = pct(player, "heavy_weapons/charging");
			if (charge > 0 && mem.sprintStart >= 0 && now - mem.sprintStart >= 40 && !mem.chargeSpent) {
				m *= 1 + charge;
				later(() -> mem.chargeSpent = true);
			}
			if (now <= mem.rageUntil) {
				m *= 1 + mem.rageStacks * pct(player, "heavy_weapons/raging");
			}
		} else if (weapon == Combat.Weapon.RANGED && source.getDirectEntity() instanceof AbstractArrow arrow) {
			crit = arrow.isCritArrow();
			m *= 1 + pct(player, "marksmanship/heavy_tipped");
			if (player.distanceTo(target) > 20) {
				m *= 1 + pct(player, "marksmanship/farsighted");
			}
			if (FLYING.contains(target.getType())) {
				m *= 1 + pct(player, "marksmanship/winged");
			}
			if (target instanceof Animal) {
				m *= 1 + pct(player, "marksmanship/game_hunters");
			}
			if (arrow.getY() >= target.getEyeY() - 0.35) {
				m *= 1 + pct(player, "marksmanship/headhunting");
			}
			if (Boolean.TRUE.equals(POISED.get(arrow))) {
				m *= 1 + pct(player, "marksmanship/poised");
			}
		}
		long now = player.level().getGameTime();
		Memory mem6 = memory(player);
		if (weapon != null) {
			// Kindled (Ember 6), Slipstream (Swift 6), Undertow (Tide 6), Underdark's first hit from the dark (Deep 6).
			if (now <= mem6.kindledUntil) {
				m *= 1.15;
			}
			if (now <= mem6.slipArmedUntil) {
				m *= 1.3;
				later(() -> mem6.slipArmedUntil = 0);
			}
			if (set(player, "tide") >= 6 && (player.isInWater() || player.level().isRainingAt(player.blockPosition()))) {
				m *= 1.15;
			}
			if (set(player, "deep") >= 6 && target instanceof Mob && now >= mem6.underdarkReady && player.level().getMaxLocalRawBrightness(player.blockPosition()) <= 4) {
				m *= 1.5;
				later(() -> mem6.underdarkReady = now + 200);
			}
			if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.ThrownTrident && set(player, "tide") >= 4) {
				m *= 1.1;
			}
			// Weak Point (Keen 6): three landed hits on one target within 4 s expose it to everyone for 4 s. A swing counts
			// only at full strength on the foe aimed at, so spam clicks and sweeps never arm it (a spear's stab, which has
			// its own charge, and arrows count as they land).
			if (set(player, "keen") >= 6 && (!Swings.swinging(player) || Swings.strength(player) >= 0.9f && !Swings.swept(player, target))) {
				later(() -> weakPoint(mem6, target, now));
			}
			if (crit && set(player, "keen") >= 2) {
				m *= 1.08;
			}
			if (!crit && set(player, "keen") >= 4 && target.getHealth() >= target.getMaxHealth() && !mem6.keenFirst.contains(target.getId())) {
				m *= 1.5;
				later(() -> {
					if (mem6.keenFirst.size() >= 256) {
						mem6.keenFirst.clear();
					}
					mem6.keenFirst.add(target.getId());
				});
			}
		}
		return m;
	}

	private static void weakPoint(Memory mem, LivingEntity target, long now) {
		if (mem.weakTarget != target.getId() || now - mem.weakFirst > 80) {
			mem.weakTarget = target.getId();
			mem.weakFirst = now;
			mem.weakHits = 0;
		}
		if (++mem.weakHits >= 3 && now >= WEAK_READY.getOrDefault(target, 0L)) {
			MARKED.put(target, new double[] {Math.max(0.2, mark(target)), now + 80});
			WEAK_READY.put(target, now + 200);
			mem.weakHits = 0;
		}
	}

	private static double taken(ServerPlayer player, DamageSource source, float damage) {
		double m = 1;
		boolean mine = any(player);
		if (mine) {
			if (source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_STALACTITE)
				|| source.is(DamageTypes.STALAGMITE) || source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.IN_WALL)) {
				m *= 1 - pct(player, "mining/stonehide");
			}
			if (player.getY() < 0 && source.getEntity() instanceof Mob) {
				m *= 1 - pct(player, "mining/cavern_wise");
			}
			if (source.is(DamageTypeTags.IS_PROJECTILE)) {
				m *= 1 - pct(player, "defense/arrowcatcher");
			}
			if (source.is(DamageTypeTags.IS_FIRE)) {
				m *= 1 - pct(player, "defense/fireproof");
				if (set(player, "ember") >= 2) {
					m *= 0.85;
				}
			}
			if (source.is(DamageTypeTags.IS_EXPLOSION)) {
				m *= 1 - pct(player, "defense/blastproof");
			}
			if (source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.IS_DROWNING)) {
				m *= 1 - pct(player, "survival/weathered");
			}
			if (source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.CACTUS)) {
				m *= 1 - pct(player, "survival/thornwalker");
			}
			if (player.getY() < 0 && set(player, "deep") >= 2) {
				m *= 0.95;
			}
			// Parrying: holding a blade, not blocking, a melee hit is turned aside for half its damage.
			if (source.getEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity() && !player.isBlocking()
				&& Combat.melee(player.getMainHandItem()) == Combat.Weapon.BLADE && roll(player, pct(player, "blades/parrying"))) {
				m *= 0.5;
				later(() -> Cards.feedback(player, "blades/parrying"));
			}
		}
		long now = player.level().getGameTime();
		Memory mem6 = memory(player);
		if (source.is(DamageTypeTags.IS_FIRE) && set(player, "ember") >= 6 && CardCooldowns.ready(player, CardCooldowns.KINDLED)) {
			later(() -> {
				if (CardCooldowns.ready(player, CardCooldowns.KINDLED)) {
					mem6.kindledUntil = now + 160;
					CardCooldowns.start(player, CardCooldowns.KINDLED, 600);
					Cards.setFeedback(player, "ember");
				}
			});
		}
		// Allies around: the best Shielding of a blocking player within 4, the best Warden 4 within 6; never from someone
		// in a fight with this player (docs/CARDS.md §6.3).
		double shield = 0;
		boolean warden = false;
		for (ServerPlayer other : player.level().players()) {
			if (!helps(other, player) || source.getEntity() == other) {
				continue;
			}
			double d2 = other.distanceToSqr(player);
			if (d2 <= 16 && other.isBlocking()) {
				shield = Math.max(shield, pct(other, "defense/shielding"));
			}
			if (d2 <= 36 && set(other, "warden") >= 4) {
				warden = true;
			}
			// Defense Taunting: a blocking player near the attacker may draw it off.
			if (source.getEntity() instanceof Mob mob && other.isBlocking() && other.distanceToSqr(mob) <= 64 && roll(other, pct(other, "defense/taunting"))) {
				later(() -> mob.setTarget(other));
			}
		}
		m *= 1 - shield;
		if (warden) {
			m *= 0.92;
		}
		return m;
	}

	/**
	 * Bodyguard (Warden 6): an ally within 6 takes a third of a hit over 4 on this player instead (4 s cooldown), within
	 * the damage-taken ceiling. Returns the share this player is left taking.
	 */
	private static double bodyguard(ServerPlayer player, DamageSource source, double damage, double taken) {
		if (inner || damage * taken <= 4) {
			return taken;
		}
		long now = player.level().getGameTime();
		for (ServerPlayer other : player.level().players()) {
			if (!helps(other, player) || source.getEntity() == other || other.distanceToSqr(player) > 36 || set(other, "warden") < 6) {
				continue;
			}
			Memory om = memory(other);
			if (now < om.bodyguardReady) {
				continue;
			}
			double after = Ceilings.taken(taken * 2 / 3);
			if (after >= taken) {
				return taken;
			}
			float share = (float) (damage * (taken - after));
			later(() -> {
				om.bodyguardReady = now + 80;
				inner = true;
				try {
					other.hurtServer((ServerLevel) other.level(), other.damageSources().generic(), share);
				} finally {
					inner = false;
				}
				Cards.setFeedback(other, "warden");
			});
			return after;
		}
		return taken;
	}

	/**
	 * Whether {@code helper}'s ally bonuses reach {@code helped}: allies, and neither struck the other in the last 5 s
	 * ("no ally bonus ever applies to someone attacking you", docs/CARDS.md §6.3).
	 */
	public static boolean helps(Player helper, Player helped) {
		return helper != helped && allies(helper, helped) && !struck(helper, helped) && !struck(helped, helper);
	}

	/** {@code by} hit {@code who} within the last 100 ticks. */
	private static boolean struck(Player by, Player who) {
		return who.getLastHurtByMob() == by && who.tickCount - who.getLastHurtByMobTimestamp() < 100
			|| by.getLastHurtMob() == who && by.tickCount - by.getLastHurtMobTimestamp() < 100;
	}

	/** Allies (§6.3): players on the same scoreboard team, or every player when one has no team. */
	public static boolean allies(Player a, Player b) {
		return a.getTeam() == null || b.getTeam() == null || a.isAlliedTo(b);
	}

	/** After damage was dealt: lifesteal, marks, raging stacks, bleeds, dazes, cleaves, ignites, taunts, shield thorns. */
	private static void afterDamage(LivingEntity target, DamageSource source, float base, float taken, boolean blocked) {
		long now = target.level().getGameTime();
		if (target instanceof ServerPlayer victim) {
			Memory mem = memory(victim);
			mem.lastHurt = now;
			if (taken > 0 && source.getEntity() instanceof LivingEntity && pct(victim, "heavy_weapons/raging") > 0) {
				mem.rageStacks = now <= mem.rageUntil ? Math.min(3, mem.rageStacks + 1) : 1;
				mem.rageUntil = now + 100;
			}
			if (blocked && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker && attacker != victim) {
				double thorns = pct(victim, "defense/thorned");
				if (thorns > 0 && !inner) {
					inner = true;
					try {
						attacker.hurtServer((ServerLevel) victim.level(), victim.damageSources().thorns(victim), (float) (base * thorns));
					} finally {
						inner = false;
					}
				}
			}
		}
		if (!(source.getEntity() instanceof ServerPlayer player) || player == target || taken <= 0 || inner || !any(player)) {
			return;
		}
		Combat.Weapon weapon = Combat.weapon(source, player);
		ServerLevel level = (ServerLevel) target.level();
		if (weapon == Combat.Weapon.BLADE) {
			double steal = pct(player, "blades/thirsting");
			Memory mem = memory(player);
			if (steal > 0) {
				if (now - mem.lastHeal >= 20) {
					mem.lastHeal = now;
					mem.healBudget = 2;
				}
				float heal = (float) Math.min(mem.healBudget, taken * steal);
				if (heal > 0) {
					player.heal(heal);
					mem.healBudget -= heal;
				}
			}
			if (Swings.critical(player)) {
				double bleed = flat(player, "blades/bleeding");
				if (bleed > 0) {
					BLEEDS.put(target, new double[] {bleed, 3});
					BLEED_BY.put(target, player.getUUID());
				}
			}
			double tempo = pct(player, "blades/tempo");
			if (tempo > 0) {
				mem.tempoStacks = mem.tempoTarget == target.getId() && now - mem.tempoAt <= 30 ? Math.min(3, mem.tempoStacks + 1) : 1;
				mem.tempoTarget = target.getId();
				mem.tempoAt = now;
				timed(player, Attributes.ATTACK_SPEED, TEMPO, tempo * mem.tempoStacks);
			}
			if (target instanceof Mob mob && mob.getTarget() instanceof Player other && other != player && roll(player, pct(player, "blades/taunting"))) {
				mob.setTarget(player);
			}
		}
		if (weapon == Combat.Weapon.HEAVY) {
			if (roll(player, pct(player, "heavy_weapons/staggering"))) {
				Stagger.stagger(target, 10);
			}
			double push = pct(player, "heavy_weapons/heavy_handed");
			if (push > 0) {
				push(target, player, 0.4 * push);
			}
			double cleave = pct(player, "heavy_weapons/cleaving");
			if (cleave > 0 && player.getMainHandItem().is(ItemTags.AXES)) {
				LivingEntity next = null;
				double best = 6.25;
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2.5))) {
					if (e != target && e != player && e.isAlive() && !(e instanceof Player p && allies(player, p)) && e.distanceToSqr(target) < best) {
						best = e.distanceToSqr(target);
						next = e;
					}
				}
				if (next != null) {
					inner = true;
					try {
						next.hurtServer(level, player.damageSources().playerAttack(player), (float) (taken * cleave));
					} finally {
						inner = false;
					}
				}
			}
			double breaker = pct(player, "heavy_weapons/shieldbreaker");
			if (breaker > 0 && target instanceof Player victim) {
				ItemStack shield = victim.getUseItem().isEmpty() ? victim.getOffhandItem() : victim.getUseItem();
				if (!shield.isEmpty() && victim.getCooldowns().isOnCooldown(shield)) {
					victim.getCooldowns().addCooldown(shield, (int) (100 * (1 + breaker)));
				}
			}
		}
		if (weapon == Combat.Weapon.RANGED) {
			double mark = pct(player, "marksmanship/marking");
			if (mark > 0) {
				MARKED.put(target, new double[] {mark, now + 60});
			}
		}
		if (now <= memory(player).kindledUntil) {
			target.igniteForSeconds(3);
		}
		if (set(player, "tide") >= 6 && (player.isInWater() || level.isRainingAt(player.blockPosition()))) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 0), player);
		}
		if ((weapon == Combat.Weapon.BLADE || weapon == Combat.Weapon.HEAVY) && set(player, "ember") >= 4 && player.getRandom().nextInt(8) == 0) {
			target.igniteForSeconds(3);
		}
		if (Swings.critical(player) && set(player, "brutal") >= 4 && target.isAlive()) {
			push(target, player, 0.25);
			Stagger.stagger(target, 10);
		}
	}

	/** Kills: Flourishing, Bloodthirsty, Retrieving. */
	private static void afterDeath(LivingEntity killed, DamageSource source) {
		float[] over = OVERKILL.remove(killed);
		if (!(source.getEntity() instanceof ServerPlayer player) || player == killed || !any(player)) {
			return;
		}
		// Overkill (Brutal 6): what the killing blow had left spills onto the nearest enemy within 4 blocks (up to 10).
		if (over != null && !inner && killed.level() instanceof ServerLevel level) {
			float spill = Math.min(10f, over[0] - over[1]);
			if (spill > 0) {
				LivingEntity next = null;
				double best = 16;
				for (Mob m : level.getEntitiesOfClass(Mob.class, killed.getBoundingBox().inflate(4))) {
					if (m != killed && m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy && m.distanceToSqr(killed) < best) {
						best = m.distanceToSqr(killed);
						next = m;
					}
				}
				if (next != null) {
					inner = true;
					try {
						next.hurtServer(level, player.damageSources().playerAttack(player), spill);
					} finally {
						inner = false;
					}
				}
			}
		}
		Combat.Weapon weapon = Combat.weapon(source, player);
		long now = player.level().getGameTime();
		if (weapon == Combat.Weapon.BLADE) {
			double speed = pct(player, "blades/flourishing");
			if (speed > 0) {
				Memory mem = memory(player);
				mem.flourish = speed;
				mem.flourishUntil = now + 60;
				timed(player, Attributes.MOVEMENT_SPEED, FLOURISH, speed);
			}
		}
		if (weapon == Combat.Weapon.HEAVY) {
			// Bloodthirsty: at most its hearts in any second; a kill by a cleave or an overkill (inner) heals half.
			double hearts = flat(player, "heavy_weapons/bloodthirsty");
			if (hearts > 0) {
				Memory mem = memory(player);
				if (now - mem.bloodAt >= 20) {
					mem.bloodAt = now;
					mem.bloodBudget = hearts * 2;
				}
				float heal = (float) Math.min(mem.bloodBudget, hearts * 2 * (inner ? 0.5 : 1));
				if (heal > 0) {
					player.heal(heal);
					mem.bloodBudget -= heal;
				}
			}
		}
		if (weapon == Combat.Weapon.RANGED && source.getDirectEntity() instanceof AbstractArrow && roll(player, pct(player, "marksmanship/retrieving"))) {
			killed.spawnAtLocation((ServerLevel) killed.level(), new ItemStack(Items.ARROW));
		}
	}

	// ---------------------------------------------------------------- drops

	/**
	 * A stack an extra drop may copy: nothing of its own in it (no enchantments, name, contents or other data, so never a
	 * filled shulker box or bundle) and no container at all. Used by every "one more" effect in Mettle.
	 */
	public static boolean plain(ItemStack stack) {
		return !stack.isEmpty() && stack.getComponentsPatch().isEmpty() && !stack.has(DataComponents.CONTAINER)
			&& !stack.has(DataComponents.BUNDLE_CONTENTS) && !stack.has(DataComponents.CONTAINER_LOOT);
	}

	/** Loot rolled: chests (Magpie), broken or picked blocks, and mobs a player killed. */
	private static void drops(Holder<LootTable> table, LootContext context, List<ItemStack> drops) {
		if (drops.isEmpty() || rolling) {
			return;
		}
		BlockState block = context.getOptional(LootContextParams.BLOCK_STATE);
		Entity self = context.getOptional(LootContextParams.THIS_ENTITY);
		if (block == null && self instanceof ServerPlayer opener && magpieTable(table)) {
			// Magpie (Hoard 6): a loot chest a player opens first rolls its table once more, one time in four.
			if (set(opener, "hoard") >= 6 && opener.getRandom().nextInt(4) == 0) {
				rolling = true;
				try {
					table.value().getRandomItemsRaw(context, drops::add);
				} finally {
					rolling = false;
				}
			}
			return;
		}
		if (block != null) {
			// Broken blocks name the breaker as THIS_ENTITY; picked ones (berry bushes, cave vines) as INTERACTING_ENTITY.
			ServerPlayer player = self instanceof ServerPlayer p ? p
				: context.getOptional(LootContextParams.INTERACTING_ENTITY) instanceof ServerPlayer p2 ? p2 : null;
			Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
			if (player != null && origin != null && any(player)) {
				blockDrops(player, context.getLevel(), BlockPos.containing(origin), block, drops, self == player);
			}
		} else if (self instanceof LivingEntity killed && !(self instanceof Player)
			&& context.getOptional(LootContextParams.LAST_DAMAGE_PLAYER) instanceof ServerPlayer player && any(player)) {
			mobDrops(player, killed, drops);
		}
	}

	/** Loot chests Magpie rolls again: chests a player unpacks, never a trial vault's reward. */
	static boolean magpieTable(Holder<LootTable> table) {
		String path = table.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		return path.startsWith("chests/") && !path.startsWith("chests/trial_chambers/reward");
	}

	/**
	 * One more of {@code drop} with chance {@code single}, and each of its items once more with chance {@code each}
	 * (Wild 2's "10 % more"); together never more than the extra-drop ceiling (docs/CARDS.md §14).
	 */
	private static void extras(ServerPlayer player, ItemStack drop, double single, double each, List<ItemStack> out) {
		double k = Ceilings.dropScale(single + each);
		int n = roll(player, single * k) ? 1 : 0;
		for (int i = 0; i < drop.getCount() && each > 0; i++) {
			if (roll(player, each * k)) {
				n++;
			}
		}
		if (n > 0) {
			out.add(drop.copyWithCount(n));
		}
	}

	/**
	 * Natural drops only: an ore, an amethyst bud, a log, a wild plant or a ripe crop that no player placed. Extras copy a
	 * plain stack; Hoard 4 never copies a block's own item (no silk-touched ore, no flower, no shulker box).
	 */
	private static void blockDrops(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> drops, boolean broken) {
		boolean upper = state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
			&& state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
			== net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
		if (PlacedBlocks.isPlaced(level, pos) || upper && PlacedBlocks.isPlaced(level, pos.below())) {
			return;
		}
		Item own = state.getBlock().asItem();
		ItemStack natural = drops.stream().filter(d -> plain(d) && !d.is(own)).findFirst().orElse(null);
		ItemStack any = drops.stream().filter(CardHooks::plain).findFirst().orElse(null);
		double hoard = set(player, "hoard") >= 4 ? HOARD : 0;
		List<ItemStack> extra = new ArrayList<>();
		if (broken && MiningSkill.isOre(state)) {
			if (natural != null) {
				boolean metal = state.is(ConventionalBlockTags.IRON_ORES) || state.is(ConventionalBlockTags.GOLD_ORES) || state.is(ConventionalBlockTags.COPPER_ORES);
				boolean gem = state.is(ConventionalBlockTags.DIAMOND_ORES) || state.is(ConventionalBlockTags.EMERALD_ORES) || state.is(ConventionalBlockTags.LAPIS_ORES)
					|| state.is(ConventionalBlockTags.REDSTONE_ORES) || state.is(ConventionalBlockTags.QUARTZ_ORES);
				double chance = hoard;
				if (metal && natural.is(ConventionalItemTags.RAW_MATERIALS)) {
					chance += pct(player, "mining/greedy");
				}
				if (gem) {
					chance += pct(player, "mining/lucky");
				}
				if (pct(player, "mining/seamfinder") > 0 && firstOfVein(player, level, pos, state)) {
					chance += pct(player, "mining/seamfinder");
				}
				extras(player, natural, chance, 0, extra);
			}
		} else if (broken && (state.is(Blocks.AMETHYST_CLUSTER) || state.is(Blocks.LARGE_AMETHYST_BUD) || state.is(Blocks.MEDIUM_AMETHYST_BUD))) {
			extras(player, new ItemStack(Items.AMETHYST_SHARD), pct(player, "mining/geodic") + (natural != null ? hoard : 0), 0, extra);
		} else if (broken && state.is(BlockTags.LOGS)) {
			// Splitting: a log that grew (never one placed) drops one more of itself.
			if (any != null) {
				extras(player, any, pct(player, "heavy_weapons/splitting"), 0, extra);
			}
		} else if (Foraging.isWildPlant(state)) {
			if (any != null) {
				double wild = set(player, "wild") >= 2 ? 0.10 : 0;
				extras(player, any, pct(player, "survival/foraging") + (any == natural ? hoard : 0), wild, extra);
			}
		} else if (broken && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && natural != null) {
			extras(player, natural, hoard, 0, extra);
		}
		drops.addAll(extra);
	}

	/** The extras the card drop effects add to a broken block's drops (tests: the tour's dupe checks). */
	public static List<ItemStack> extraDrops(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> drops) {
		List<ItemStack> all = new ArrayList<>(drops);
		blockDrops(player, level, pos, state, all, true);
		return List.copyOf(all.subList(drops.size(), all.size()));
	}

	/** The extras the card loot effects add to a kill's drops (tests). */
	public static List<ItemStack> extraLoot(ServerPlayer player, LivingEntity killed, List<ItemStack> drops) {
		List<ItemStack> all = new ArrayList<>(drops);
		mobDrops(player, killed, all);
		return List.copyOf(all.subList(drops.size(), all.size()));
	}

	/** Seamfinder: whether this ore opens a vein this player hasn't broken into lately (its connected ore, up to 64). */
	private static boolean firstOfVein(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		Memory mem = memory(player);
		long at = pos.asLong();
		for (Set<Long> vein : mem.veins) {
			if (vein.contains(at)) {
				return false;
			}
		}
		Set<Long> vein = new HashSet<>();
		vein.add(at);
		MiningSkill.vein(level, pos, state.getBlock(), 64).forEach(p -> vein.add(p.asLong()));
		mem.veins.addLast(vein);
		while (mem.veins.size() > 8) {
			mem.veins.removeFirst();
		}
		return true;
	}

	/** A kill's loot: Trophy, Headtaker, Skinning, Butchering, Wild 2 and Hoard 4, each copying a plain stack only. */
	private static void mobDrops(ServerPlayer player, LivingEntity killed, List<ItemStack> drops) {
		Combat.Weapon weapon = Combat.melee(player.getMainHandItem());
		List<ItemStack> extra = new ArrayList<>();
		List<ItemStack> plain = drops.stream().filter(CardHooks::plain).toList();
		if (!plain.isEmpty()) {
			boolean animal = killed instanceof Animal;
			double wild = animal && set(player, "wild") >= 2 ? 0.10 : 0;
			double hoard = set(player, "hoard") >= 4 ? HOARD : 0;
			double trophy = weapon == Combat.Weapon.BLADE ? pct(player, "blades/trophy") : 0;
			ItemStack trophied = plain.get(player.getRandom().nextInt(plain.size()));
			ItemStack hide = animal ? plain.stream().filter(d -> d.is(SurvivalSkill.HIDES) || d.is(Items.FEATHER) || d.is(ItemTags.WOOL)).findFirst().orElse(null) : null;
			ItemStack meat = animal ? plain.stream().filter(d -> d.is(SurvivalSkill.GAME_MEAT) || d.is(ItemTags.MEAT)).findFirst().orElse(null) : null;
			for (ItemStack d : plain) {
				double single = 0;
				if (d == plain.getFirst()) {
					single += hoard;
				}
				if (d == trophied) {
					single += trophy;
				}
				if (d == hide) {
					single += pct(player, "survival/skinning");
				}
				if (d == meat) {
					single += pct(player, "survival/butchering");
				}
				extras(player, d, single, wild, extra);
			}
		}
		if (weapon == Combat.Weapon.HEAVY && roll(player, pct(player, "heavy_weapons/headtaker"))) {
			ItemStack head = Trophies.headOf(killed);
			if (head != null) {
				extra.add(head);
			}
		}
		drops.addAll(extra);
	}

	/** After a block broke: Echoing takes one touching ore of the same kind. */
	private static void broke(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (inner || !MiningSkill.isOre(state) || !roll(player, pct(player, "mining/echoing"))) {
			return;
		}
		for (Direction d : Direction.values()) {
			BlockPos next = pos.relative(d);
			if (level.getBlockState(next).getBlock() == state.getBlock()) {
				inner = true;
				try {
					player.gameMode.destroyBlock(next);
				} finally {
					inner = false;
				}
				Cards.feedback(player, "mining/echoing");
				return;
			}
		}
	}

	// ---------------------------------------------------------------- XP

	/** Mining XP from ore (Prospecting). */
	public static double oreXp(ServerPlayer player, BlockState state) {
		return MiningSkill.isOre(state) ? 1 + pct(player, "mining/prospecting") : 1;
	}

	/** Smithing XP from smelting (Hot-forged). */
	public static double smeltXp(ServerPlayer player) {
		return 1 + pct(player, "smithing/hot_forged");
	}

	/** Smithing XP from anvil jobs (Runewise). */
	public static double anvilXp(ServerPlayer player) {
		return 1 + pct(player, "smithing/runewise");
	}

	/** Discovery XP (Wayfinder): biomes and places. */
	public static double discoveryXp(ServerPlayer player, String kind) {
		return "biome".equals(kind) || "place".equals(kind) ? 1 + pct(player, "survival/wayfinder") : 1;
	}

	/** Diligent (Smithing XP), and the Kin set (allies within 8 of a Kin 2 player learn 5 % faster). */
	private static double xpBonus(ServerPlayer player, net.minecraft.resources.ResourceKey<dev.voidmc.mettle.api.Skill> skill) {
		double m = 1;
		if (skill.identifier().getPath().equals("smithing")) {
			m *= 1 + pct(player, "smithing/diligent");
		}
		for (ServerPlayer other : player.level().players()) {
			if (other != player && other.distanceToSqr(player) <= 64 && allies(player, other) && set(other, "kin") >= 2) {
				m *= 1.05;
				break;
			}
		}
		return m;
	}

	// ---------------------------------------------------------------- wear, hunger, effects, air, movement

	/** ItemStack#hurtAndBreak with an owner and slot: Unworn, Honed, Mending, the Iron set. Each point of wear may be skipped. */
	public static int wear(ItemStack stack, int amount, LivingEntity owner, EquipmentSlot slot) {
		if (amount <= 0 || !(owner instanceof ServerPlayer player) || !any(player)) {
			return amount;
		}
		double skip = 0;
		if (stack.is(ItemTags.PICKAXES)) {
			skip = pct(player, "mining/unworn");
		} else if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.SPEARS)) {
			skip = pct(player, "blades/honed");
		} else if (stack.is(ItemTags.AXES) || stack.is(Items.MACE) || stack.is(Items.TRIDENT)) {
			skip = pct(player, "heavy_weapons/unworn");
		}
		if (slot.isArmor() || stack.is(Items.SHIELD)) {
			skip = 1 - (1 - pct(player, "defense/mending")) * (set(player, "iron") >= 2 ? 0.9 : 1);
		}
		if (skip <= 0) {
			return amount;
		}
		int left = 0;
		for (int i = 0; i < amount; i++) {
			if (player.getRandom().nextDouble() >= skip) {
				left++;
			}
		}
		return left;
	}

	/** Player#causeFoodExhaustion: Iron-gut, Tireless (digging), Windless (sprinting). */
	public static float exhaustion(Player player, float amount) {
		if (amount <= 0) {
			return amount;
		}
		if (MythicEffects.trailFree(player) && Math.abs(amount - 0.005f) > 1e-6) {
			return 0f;
		}
		if (!any(player)) {
			return amount;
		}
		double m = 1 - pct(player, "survival/iron_gut");
		if (Math.abs(amount - 0.005f) < 1e-6) {
			m *= 1 - pct(player, "mining/tireless");
		} else if (player.isSprinting()) {
			m *= 1 - pct(player, "athletics/windless");
		}
		return (float) (amount * m);
	}

	/** Hearty: food fills you more (nutrition and saturation, rounded). */
	public static int nutrition(Player player, int nutrition) {
		double more = pct(player, "survival/hearty");
		return more <= 0 ? nutrition : (int) Math.round(nutrition * (1 + more));
	}

	/** LivingEntity#addEffect: Resolute, Antidote, Unshaken (shorter), Herbwise (potions drunk last longer). */
	public static MobEffectInstance effect(LivingEntity entity, MobEffectInstance effect) {
		if (entity instanceof ServerPlayer hero && Mythics.isHero(effect)) {
			Mythics.heroOfTheVillage(hero);
		}
		effect = MythicEffects.effect(entity, effect);
		if (!(entity instanceof ServerPlayer player) || effect.isInfiniteDuration() || effect.getDuration() <= 1 || !any(player)) {
			return effect;
		}
		Holder<MobEffect> kind = effect.getEffect();
		double m = 1;
		if (kind.value().getCategory() == MobEffectCategory.HARMFUL) {
			m *= 1 - pct(player, "defense/resolute");
			if (kind.equals(MobEffects.POISON) || kind.equals(MobEffects.HUNGER)) {
				m *= 1 - pct(player, "survival/antidote");
			}
			if ((kind.equals(MobEffects.SLOWNESS) || kind.equals(MobEffects.WEAKNESS)) && Combat.melee(player.getMainHandItem()) == Combat.Weapon.HEAVY) {
				m *= 1 - pct(player, "heavy_weapons/unshaken");
			}
		} else if (kind.value().getCategory() == MobEffectCategory.BENEFICIAL && player.isUsingItem() && player.getUseItem().is(Items.POTION)) {
			m *= 1 + pct(player, "survival/herbwise");
		}
		if (m == 1) {
			return effect;
		}
		return new MobEffectInstance(kind, Math.max(1, (int) Math.round(effect.getDuration() * m)), effect.getAmplifier(), effect.isAmbient(),
			effect.isVisible(), effect.showIcon());
	}

	/** LivingEntity#decreaseAirSupply's result: Deep-lunged and the Tide set hold breath longer (a skipped breath now and then). */
	public static int air(LivingEntity entity, int current, int next) {
		if (next >= current || !(entity instanceof Player player) || !any(player)) {
			return next;
		}
		double seconds = flat(player, "athletics/deep_lunged") + (set(player, "tide") >= 2 ? 5 : 0);
		return seconds > 0 && player.getRandom().nextDouble() < seconds / (15 + seconds) ? current : next;
	}

	/** Climbing: climb faster (the climb step of LivingEntity#handleRelativeFrictionAndCalculateMovement). */
	public static Vec3 climb(LivingEntity entity, Vec3 movement) {
		if (entity instanceof Player player && movement.y == 0.2 && entity.onClimbable()) {
			double more = pct(player, "athletics/climbing");
			if (more > 0) {
				return new Vec3(movement.x, 0.2 * (1 + more), movement.z);
			}
		}
		return movement;
	}

	/** Surefoot: soul sand and honey slow you less (Entity#getBlockSpeedFactor). */
	public static float blockSpeed(Entity entity, float factor) {
		if (factor < 1 && entity instanceof Player player) {
			double sure = pct(player, "athletics/surefoot");
			return (float) (factor + (1 - factor) * sure);
		}
		return factor;
	}

	/** Surefoot: cobwebs, powder snow and berry bushes hold you less (Entity#makeStuckInBlock). */
	public static Vec3 stuck(Entity entity, Vec3 multiplier) {
		if (entity instanceof Player player) {
			double sure = pct(player, "athletics/surefoot");
			if (sure > 0) {
				return new Vec3(multiplier.x + (1 - multiplier.x) * sure, multiplier.y + (1 - multiplier.y) * sure, multiplier.z + (1 - multiplier.z) * sure);
			}
		}
		return multiplier;
	}

	/** Item weights for this player: Hauler's (stone, ore, raw metal), Fitted (worn armour), the Hoard set. Both sides. */
	private static float weight(Player player, ItemInstance stack) {
		CardsState cards = CardStore.get(player);
		if (cards.traits().isEmpty() && cards.tiers().isEmpty()) {
			return 1f;
		}
		double m = 1;
		if (set(player, "hoard") >= 2) {
			m *= 0.95;
		}
		double haul = pct(player, "mining/haulers");
		if (haul > 0 && (stack.is(ItemTags.STONE_TOOL_MATERIALS) || stack.is(ItemTags.STONE_CRAFTING_MATERIALS) || stack.is(ConventionalItemTags.ORES)
			|| stack.is(ConventionalItemTags.RAW_MATERIALS) || stack.is(ConventionalItemTags.STONES) || stack.is(ConventionalItemTags.COBBLESTONES))) {
			m *= 1 - haul;
		}
		double fit = pct(player, "defense/fitted");
		if (fit > 0) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (slot.isArmor() && player.getItemBySlot(slot) == stack) {
					m *= 1 - fit;
					break;
				}
			}
		}
		return (float) m;
	}

	/** Stalking: an animal notices this player only within its flee distance × (1 − X). */
	public static boolean unnoticed(Mob mob, @org.jspecify.annotations.Nullable LivingEntity avoided, float maxDist) {
		if (!(mob instanceof Animal) || !(avoided instanceof Player player)) {
			return false;
		}
		double stalk = pct(player, "survival/stalking");
		double near = maxDist * (1 - stalk);
		return stalk > 0 && mob.distanceToSqr(player) > near * near;
	}

	/** Masterly: a crafted piece is a Masterwork some of the time (rolled from the smith's seed and piece count). */
	public static boolean masterly(ServerPlayer player, long n) {
		double chance = pct(player, "smithing/masterly");
		if (chance <= 0) {
			return false;
		}
		long z = player.getUUID().getLeastSignificantBits() * 31 + (n + 7) * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 29)) * 0xBF58476D1CE4E5B9L;
		return ((z ^ (z >>> 32)) >>> 11) * 0x1.0p-53 < chance;
	}

	/** The Smithing traits a crafted piece is stamped with (Tempered, Edged, Lightened, Quenched, Proofing). */
	public static Map<String, Double> forgedTraits(ServerPlayer player, boolean armour, boolean weapon, boolean dig) {
		Map<String, Double> out = new java.util.TreeMap<>();
		if ((weapon || dig) && pct(player, "smithing/tempered") > 0) {
			out.put("tempered", pct(player, "smithing/tempered"));
		}
		if (weapon && pct(player, "smithing/edged") > 0) {
			out.put("edged", pct(player, "smithing/edged"));
		}
		if (armour && pct(player, "smithing/lightened") > 0) {
			out.put("lightened", pct(player, "smithing/lightened"));
		}
		if (dig && pct(player, "smithing/quenched") > 0) {
			out.put("quenched", pct(player, "smithing/quenched"));
		}
		if (armour && flat(player, "smithing/proofing") > 0) {
			out.put("proofing", flat(player, "smithing/proofing"));
		}
		// The First Hammer (a Mythic): what you craft lasts 20 % longer and carries your mark in gold.
		if (Mythics.active(player, "first_hammer")) {
			out.put("first_hammer", 0.2);
		}
		return out;
	}

	// ---------------------------------------------------------------- ranged and shields (called from the skills' hooks)

	/** Draw speed (Quickdraw, the Swift set). Both sides. */
	public static float drawSpeed(Player player) {
		return (float) ((1 + pct(player, "marksmanship/quickdraw")) * (set(player, "swift") >= 4 ? 1.05 : 1));
	}

	/** Crossbow charge time (Cranking). Both sides. */
	public static int crossbowCharge(Player player, int ticks) {
		double more = pct(player, "marksmanship/cranking");
		return more <= 0 ? ticks : Math.max(1, (int) Math.round(ticks / (1 + more)));
	}

	/** Arrow speed (Swift-flighted, the Gale set). */
	public static float arrowSpeed(ServerPlayer player) {
		return (float) ((1 + pct(player, "marksmanship/swift_flighted")) * (set(player, "gale") >= 4 ? 1.1 : 1));
	}

	/** Spread while moving (Steady). */
	public static float uncertainty(ServerPlayer player, float uncertainty) {
		boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 1e-4;
		return moving ? (float) (uncertainty * (1 - pct(player, "marksmanship/steady"))) : uncertainty;
	}

	/** Twinned: whether this shot looses a second arrow (it uses one). */
	public static boolean twin(ServerPlayer player) {
		return roll(player, pct(player, "marksmanship/twinned"));
	}

	/** Thrifty (Marksmanship): the arrow isn't used up. */
	public static boolean keepArrow(ServerPlayer player) {
		return roll(player, pct(player, "marksmanship/thrifty"));
	}

	/** An arrow just loosed by this player: Piercing, and whether they stood still for Poised. */
	public static void loosed(ServerPlayer player, AbstractArrow arrow) {
		MythicEffects.loosed(player, arrow);
		if (!any(player)) {
			return;
		}
		if (roll(player, pct(player, "marksmanship/piercing"))) {
			((dev.voidmc.mettle.mixin.cards.ArrowAccess) arrow).mettle$setPierceLevel((byte) (arrow.getPierceLevel() + 1));
		}
		Memory mem = memory(player);
		if (player.level().getGameTime() - mem.stillSince >= 20) {
			POISED.put(arrow, true);
		}
	}

	/** The shield's raise delay (Quickguard). */
	public static int blockDelay(Player player, int delay) {
		double faster = pct(player, "defense/quickguard");
		return faster <= 0 ? delay : (int) Math.round(delay * (1 - faster));
	}

	/** The Echo set: perk cooldowns 10 % shorter at 2, 20 % at 4 (Cooldowns.scale). */
	public static float cooldownScale(Player player) {
		int echo = set(player, "echo");
		return echo >= 4 ? 0.8f : echo >= 2 ? 0.9f : 1f;
	}

	// ---------------------------------------------------------------- every tick

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!BLEEDS.isEmpty() && now % 20 == 0) {
			bleed(server);
		}
		boolean slow = now % 10 == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Memory mem = MEMORY.get(player.getUUID());
			if (mem == null) {
				if (!any(player)) {
					continue;
				}
				mem = memory(player);
			}
			// Sprint and stillness, for Charging, Long-winded and Poised.
			if (player.isSprinting()) {
				if (mem.sprintStart < 0) {
					mem.sprintStart = now;
					mem.chargeSpent = false;
				}
			} else {
				mem.sprintStart = -1;
			}
			Vec3 pos = player.position();
			if (pos.distanceToSqr(mem.lastPos) > 1e-4) {
				mem.stillSince = now;
				mem.lastPos = pos;
			}
			if (!slow) {
				continue;
			}
			if (mem.tempoStacks > 0 && now - mem.tempoAt > 30) {
				mem.tempoStacks = 0;
				timed(player, Attributes.ATTACK_SPEED, TEMPO, 0);
			}
			if (mem.flourishUntil > 0 && now > mem.flourishUntil) {
				mem.flourishUntil = 0;
				timed(player, Attributes.MOVEMENT_SPEED, FLOURISH, 0);
			}
			double winded = pct(player, "athletics/long_winded");
			timed(player, Attributes.MOVEMENT_SPEED, LONG_WINDED, winded > 0 && mem.sprintStart >= 0 && now - mem.sprintStart >= 200 ? winded : 0);
			// Pacesetting: allies sprinting within 8 blocks of the best pacesetter move faster.
			double pace = 0;
			if (player.isSprinting()) {
				for (ServerPlayer other : player.level().players()) {
					if (other.distanceToSqr(player) <= 64 && helps(other, player)) {
						pace = Math.max(pace, pct(other, "athletics/pacesetting"));
					}
				}
			}
			timed(player, Attributes.MOVEMENT_SPEED, PACESETTING, pace);
			if (now % 20 == 0) {
				regen(player, mem, now);
			}
			sixes(player, mem, now);
		}
	}

	/** The 6-tier sets that run over time (every 10 ticks). */
	private static void sixes(ServerPlayer player, Memory mem, long now) {
		if (now <= mem.kindledUntil && player.getRemainingFireTicks() > 40) {
			player.setRemainingFireTicks(40);
		}
		if (set(player, "swift") >= 6 && mem.sprintStart >= 0 && now - mem.sprintStart >= 60) {
			mem.slipArmedUntil = now + 40;
		}
		boolean hurtable = player.getHealth() < player.getMaxHealth() && !player.isDeadOrDying();
		if (set(player, "deep") >= 6 && hurtable && now % 100 == 0 && player.level().getMaxLocalRawBrightness(player.blockPosition()) <= 4) {
			player.heal(1f);
		}
		boolean feral = set(player, "wild") >= 6 && natural(player);
		timed(player, Attributes.MOVEMENT_SPEED, FERAL, feral ? 0.08 : 0);
		if (feral && hurtable && now % 80 == 0 && player.getFoodData().getFoodLevel() < 18
			&& player.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION)) {
			player.heal(1f);
		}
		if (set(player, "tide") >= 6 && player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
			MobEffectInstance sight = player.getEffect(MobEffects.NIGHT_VISION);
			if (sight == null || sight.getDuration() < 200) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
			}
		}
	}

	/** Natural ground for Feral: standing on soil, sand, snow or moss (not a floor someone laid). */
	private static boolean natural(Player player) {
		BlockState below = player.level().getBlockState(player.getOnPos());
		return player.onGround() && (below.is(BlockTags.DIRT) || below.is(BlockTags.SAND) || below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.SNOW)
			|| below.is(Blocks.GRAVEL) || below.is(BlockTags.BASE_STONE_OVERWORLD) && player.level().canSeeSky(player.blockPosition()));
	}

	/** Pact (Hex 6): a killing blow leaves the player on 4 hearts, strong and quick, and 3 max hearts short until they sleep. */
	private static boolean pact(ServerPlayer player) {
		if (set(player, "hex") < 6 || !CardCooldowns.ready(player, CardCooldowns.PACT)) {
			return false;
		}
		CardCooldowns.start(player, CardCooldowns.PACT, 6000);
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health != null && health.getModifier(PACT) == null) {
			health.addPermanentModifier(new AttributeModifier(PACT, -6, AttributeModifier.Operation.ADD_VALUE));
		}
		player.setHealth(Math.min(8f, player.getMaxHealth()));
		player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 160, 0));
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 160, 0));
		Cards.setFeedback(player, "hex");
		return true;
	}

	/** Rally Cry (Kin 6): sneak and swing an empty hand: allies within 16 blocks get Speed I and Strength I for 8 s. */
	public static void punched(ServerPlayer player) {
		if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || set(player, "kin") < 6 || !CardCooldowns.ready(player, CardCooldowns.RALLY)) {
			return;
		}
		CardCooldowns.start(player, CardCooldowns.RALLY, 1800);
		for (ServerPlayer other : player.level().players()) {
			if ((other == player || helps(player, other)) && other.distanceToSqr(player) <= 256) {
				other.addEffect(new MobEffectInstance(MobEffects.SPEED, 160, 0), player);
				other.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 160, 0), player);
			}
		}
		Cards.setFeedback(player, "kin");
	}

	/**
	 * Updraft (Gale 6): the client asked for the mid-air jump it already made; the server allows it every 8 s (half a
	 * second of slack for lag), and the client reads the same saved cooldown before it jumps.
	 */
	public static void updraft(ServerPlayer player) {
		if (set(player, "gale") < 6 || player.onGround() || CardCooldowns.remaining(player, CardCooldowns.UPDRAFT) > 10 || player.getAbilities().flying) {
			return;
		}
		CardCooldowns.start(player, CardCooldowns.UPDRAFT, UPDRAFT_TICKS);
		player.resetFallDistance();
	}

	/** Updraft's cooldown: 8 s. */
	public static final int UPDRAFT_TICKS = 160;

	/** LivingEntity#getJumpPower: a player's jump strength counts at most {@link Ceilings#JUMP_STRENGTH} (both sides). */
	public static float jumpPower(LivingEntity entity, float power) {
		if (!(entity instanceof Player player)) {
			return power;
		}
		return Ceilings.jumpPower(power, player.getAttributeValue(Attributes.JUMP_STRENGTH), player.getJumpBoostPower());
	}

	/** Projectile#shootFromRotation: Gale 4 throws things (tridents, snowballs, potions, axes) 10 % faster; bows have their own. */
	public static float thrown(Entity projectile, Entity shooter, float power) {
		if (shooter instanceof ServerPlayer player && !(projectile instanceof AbstractArrow && !(projectile instanceof net.minecraft.world.entity.projectile.arrow.ThrownTrident))
			&& set(player, "gale") >= 4) {
			return power * 1.1f;
		}
		return power;
	}

	/** Echo 6 (Reverberate): a perk cooldown just started is ready again at once, one time in four. */
	public static boolean reverberates(Player player) {
		return set(player, "echo") >= 6 && player.getRandom().nextInt(4) == 0;
	}

	/** Deep 4: below y 0, hostiles see this player 25 % less far (LivingEntity#getVisibilityPercent). */
	public static double visibility(LivingEntity self, @org.jspecify.annotations.Nullable Entity looking, double percent) {
		if (self instanceof Player player && looking instanceof net.minecraft.world.entity.monster.Enemy && player.getY() < 0 && set(player, "deep") >= 4) {
			return percent * 0.75;
		}
		return percent;
	}

	/** Ember 4: campfire food heals a heart when eaten. */
	public static void ateCooked(ServerPlayer player) {
		if (set(player, "ember") >= 4) {
			player.heal(2f);
		}
	}

	/**
	 * Recovering and the Kin set, once a second: "X faster" adds X of what vanilla's own regeneration heals this second,
	 * and only when it heals at all (the natural regeneration rule on, fed to 18 or more), costing hunger as vanilla's does.
	 */
	private static void regen(ServerPlayer player, Memory mem, long now) {
		if (player.getHealth() >= player.getMaxHealth() || player.isDeadOrDying()) {
			return;
		}
		double base = vanillaRegen(player);
		if (base <= 0) {
			return;
		}
		double rate = 0;
		if (now - mem.lastHurt >= 100) {
			rate += pct(player, "defense/recovering");
		}
		for (ServerPlayer other : player.level().players()) {
			if (other.distanceToSqr(player) <= 64 && (other == player || helps(other, player)) && set(other, "kin") >= 4) {
				rate += 0.10;
				break;
			}
		}
		if (rate > 0) {
			float heal = (float) Math.min(player.getMaxHealth() - player.getHealth(), base * rate);
			player.heal(heal);
			// Vanilla pays 6 exhaustion for each health point it heals.
			player.getFoodData().addExhaustion(heal * 6);
		}
	}

	/** The health vanilla's natural regeneration heals this player in a second right now (FoodData#tick). */
	static double vanillaRegen(ServerPlayer player) {
		if (!player.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION)) {
			return 0;
		}
		var food = player.getFoodData();
		if (food.getSaturationLevel() > 0 && food.getFoodLevel() >= 20) {
			return Math.min(food.getSaturationLevel(), 6) / 6.0 * 2;
		}
		return food.getFoodLevel() >= 18 ? 0.25 : 0;
	}

	private static void bleed(MinecraftServer server) {
		for (LivingEntity target : List.copyOf(BLEEDS.keySet())) {
			double[] b = BLEEDS.get(target);
			if (b == null || !target.isAlive() || target.isRemoved() || !(target.level() instanceof ServerLevel level)) {
				BLEEDS.remove(target);
				BLEED_BY.remove(target);
				continue;
			}
			float pulse = (float) (b[0] / 3.0);
			ServerPlayer attacker = server.getPlayerList().getPlayer(BLEED_BY.getOrDefault(target, new UUID(0, 0)));
			DamageSource source = level.damageSources().source(BleedDamage.BLEED, null, attacker);
			BladesLivingAccess access = (BladesLivingAccess) target;
			int cooldown = target.damageCooldownTime;
			float lastHurt = access.mettle$lastHurt();
			target.damageCooldownTime = 0;
			inner = true;
			try {
				target.hurtServer(level, source, pulse);
			} finally {
				inner = false;
			}
			if (target.isAlive()) {
				target.damageCooldownTime = cooldown;
				access.mettle$setLastHurt(lastHurt);
			}
			b[1]--;
			if (b[1] <= 0) {
				BLEEDS.remove(target);
				BLEED_BY.remove(target);
			}
		}
	}

	/** Knocks {@code target} away from {@code from}, as a knockback of {@code strength} would. */
	private static void push(LivingEntity target, Entity from, double strength) {
		double scaled = strength * (1 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		if (scaled <= 0) {
			return;
		}
		Vec3 away = target.position().subtract(from.position()).multiply(1, 0, 1).normalize();
		target.push(away.x * scaled, target.onGround() ? 0.1 * scaled : 0, away.z * scaled);
	}

	/** A short-lived attribute modifier with a fixed id; 0 removes it. */
	private static void timed(ServerPlayer player, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (amount <= 0) {
			if (current != null) {
				instance.removeModifier(id);
				if (attribute.is(Attributes.MOVEMENT_SPEED)) {
					Ceilings.movement(player);
				}
			}
			return;
		}
		if (current != null && current.amount() == amount) {
			return;
		}
		instance.removeModifier(id);
		instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		if (attribute.is(Attributes.MOVEMENT_SPEED)) {
			Ceilings.movement(player);
		}
	}

	/** Test hook: a target's mark from Marking (0 when none). */
	public static double mark(LivingEntity target) {
		double[] m = MARKED.get(target);
		return m == null ? 0 : m[0];
	}
}
