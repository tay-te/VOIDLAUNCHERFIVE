package dev.voidmc.mettle.character.skill;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import dev.voidmc.mettle.character.skill.heavy.HeavyHit;
import dev.voidmc.mettle.character.skill.heavy.HeavyRules;
import dev.voidmc.mettle.character.skill.heavy.Stagger;
import dev.voidmc.mettle.character.skill.heavy.Stance;
import dev.voidmc.mettle.character.skill.heavy.Strikes;
import dev.voidmc.mettle.character.skill.heavy.ThrownAxe;
import dev.voidmc.mettle.character.skill.heavy.Trophies;
import dev.voidmc.mettle.character.skill.heavy.Woodcraft;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Heavy Weapons tree (TREES.md §5): axes, maces and tridents. XP comes from {@link Combat} (and, for thrown axes,
 * from here); the perk definitions are data in {@code data/mettle/mettle/perk/heavy_weapons/}, the hooks into vanilla
 * are mixins in {@code mixin.heavyweapons}, and the behaviours live in {@code skill.heavy}:
 *
 * <ul>
 * <li>{@code Woodcraft}: Feller, Rhythm of the Axe, Timber, Old Growth, Stonebreaker (breaking blocks)</li>
 * <li>{@code Strikes}: Cleave, Bone Breaker, Stagger, Overhead Blow, Earthshaker, Berserker's lifesteal (after a hit)</li>
 * <li>{@code Stance}: Iron Grip and Berserker's rage (conditional attribute modifiers)</li>
 * <li>{@code ThrownAxe}: Throwing Axe; {@code Trophies}: Headhunter</li>
 * </ul>
 *
 * Heft and Berserker's damage are multipliers on the hit as it arrives ({@link #outgoing}); Armour Crusher shaves the
 * target's armour as vanilla applies it ({@link #armour}). Log Hauler is a weight perk: data only until burden reads it.
 */
public final class HeavyWeaponsSkill {
	public static final Identifier HEFT = Mettle.id("heavy_weapons/heft");
	public static final Identifier FELLER = Mettle.id("heavy_weapons/feller");
	public static final Identifier CLEAVE = Mettle.id("heavy_weapons/cleave");
	public static final Identifier LOG_HAULER = Mettle.id("heavy_weapons/log_hauler");
	public static final Identifier RHYTHM_OF_THE_AXE = Mettle.id("heavy_weapons/rhythm_of_the_axe");
	public static final Identifier THROWING_AXE = Mettle.id("heavy_weapons/throwing_axe");
	public static final Identifier ARMOUR_CRUSHER = Mettle.id("heavy_weapons/armour_crusher");
	public static final Identifier BONE_BREAKER = Mettle.id("heavy_weapons/bone_breaker");
	public static final Identifier IRON_GRIP = Mettle.id("heavy_weapons/iron_grip");
	public static final Identifier STAGGER = Mettle.id("heavy_weapons/stagger");
	public static final Identifier TIMBER = Mettle.id("heavy_weapons/timber");
	public static final Identifier BERSERKER = Mettle.id("heavy_weapons/berserker");
	public static final Identifier EARTHSHAKER = Mettle.id("heavy_weapons/earthshaker");
	public static final Identifier HEADHUNTER = Mettle.id("heavy_weapons/headhunter");
	public static final Identifier OLD_GROWTH = Mettle.id("heavy_weapons/old_growth");
	public static final Identifier STONEBREAKER = Mettle.id("heavy_weapons/stonebreaker");
	public static final Identifier OVERHEAD_BLOW = Mettle.id("heavy_weapons/overhead_blow");

	public static final String HOSTILE_AXE_KILLS = "axe_hostile_kills";

	private HeavyWeaponsSkill() {
	}

	public static void init() {
		Objects.requireNonNull(ThrownAxe.TYPE);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel serverLevel) {
				Woodcraft.afterBreak(serverLevel, sp, pos, state);
			}
		});
		UseItemCallback.EVENT.register(HeavyWeaponsSkill::useItem);
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, source) -> {
			if (killer instanceof ServerPlayer player) {
				afterKill(level, player, killed, source);
			}
		});
		// Staggered mobs (and mobs owed a miss by Stagger) land no melee hit. Checked on the damage rather than in the
		// attack goal, so every mob's own attack code is covered.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(source.getEntity() instanceof Mob mob && source.getDirectEntity() == mob && !source.is(DamageTypeTags.IS_EXPLOSION)
				&& Stagger.cancelsAttack(mob)));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseTaken, taken, blocked) -> {
			if (entity instanceof ServerPlayer player) {
				Stance.update(player);
			}
		});
		// Berserker: Slowness doesn't take while you rage.
		ServerMobEffectEvents.ALLOW_ADD.register((effect, entity, context) -> {
			if (effect.getEffect().equals(MobEffects.SLOWNESS) && entity instanceof ServerPlayer player && Stance.raging(player)) {
				chip(player, BERSERKER);
				return false;
			}
			return true;
		});
		ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, previous, next) -> {
			if (slot == EquipmentSlot.MAINHAND && entity instanceof ServerPlayer player) {
				Stance.update(player);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Stagger.tick(server);
			if (server.getTickCount() % 5 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					Stance.update(player);
				}
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> forget(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Stagger.clear());
	}

	/** A feedback chip for one of this tree's perks: {@code mettle.feedback.heavy_weapons.<perk>}. */
	public static void chip(ServerPlayer player, Identifier perk, Object... args) {
		SignaturePerks.feedback(player, perk, Component.translatable("mettle.feedback." + perk.getPath().replace('/', '.'), args));
	}

	// ---------------------------------------------------------------- damage (called from mixins)

	/** A hit by this player with a heavy weapon: a melee swing with an axe, mace or trident, or a thrown axe. */
	public static boolean isHeavyHit(DamageSource source, ServerPlayer player) {
		return source.getEntity() == player && (source.getDirectEntity() instanceof ThrownAxe || Combat.weapon(source, player) == Combat.Weapon.HEAVY);
	}

	/** LivingEntity#hurtServer's damage on arrival: Heft and Berserker's +40 %. */
	public static float outgoing(LivingEntity target, DamageSource source, float damage) {
		if (damage <= 0 || Strikes.splashing() || !(source.getEntity() instanceof ServerPlayer player) || player == target || !isHeavyHit(source, player)) {
			return damage;
		}
		float m = 1f;
		int heft = Perks.rank(player, HEFT);
		if (heft > 0) {
			m *= HeavyRules.heft(heft);
		}
		if (Stance.raging(player)) {
			m *= HeavyRules.BERSERK_DAMAGE;
		}
		float out = damage * m;
		HeavyHit hit = HeavyHit.of(player, target);
		if (hit != null) {
			hit.damage = out;
		}
		return out;
	}

	/** Armour Crusher: the target's armour counts for a quarter less against your heavy hits. */
	public static float armour(DamageSource source, float armor) {
		if (armor > 0 && source.getEntity() instanceof ServerPlayer player && isHeavyHit(source, player) && Perks.has(player, ARMOUR_CRUSHER)) {
			return armor * HeavyRules.ARMOUR_CRUSHER_KEEP;
		}
		return armor;
	}

	/** A thrown axe struck: Heavy Weapons XP (Combat leaves projectiles to Marksmanship) and Berserker's lifesteal. */
	public static void thrownHit(ServerPlayer player, LivingEntity target, float dealt) {
		if (!player.isCreative()) {
			Progress.award(player, Combat.HEAVY_WEAPONS, dealt * Combat.targetFactor(target));
		}
		Strikes.lifesteal(player, dealt);
	}

	// ---------------------------------------------------------------- events

	/** Throwing Axe: sneak and use an axe (with no shield to raise instead) to throw it. */
	private static InteractionResult useItem(Player player, Level level, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.isSpectator() || !stack.is(ItemTags.AXES)
			|| player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) || !Perks.has(player, THROWING_AXE)) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp && level instanceof ServerLevel serverLevel) {
			ThrownAxe.throwFrom(sp, serverLevel, stack);
		}
		return InteractionResult.SUCCESS;
	}

	private static void afterKill(ServerLevel level, ServerPlayer player, LivingEntity killed, DamageSource source) {
		if (player.isCreative()) {
			return;
		}
		Entity direct = source.getDirectEntity();
		boolean thrown = direct instanceof ThrownAxe;
		if (thrown) {
			Progress.award(player, Combat.HEAVY_WEAPONS, CombatXp.kill(killed.getMaxHealth()) * Combat.targetFactor(killed));
		}
		boolean axe = thrown || direct == player && player.getMainHandItem().is(ItemTags.AXES);
		if (!axe) {
			return;
		}
		if (killed instanceof Enemy) {
			FeatCounters.add(player, HOSTILE_AXE_KILLS, 1);
		}
		if (Perks.has(player, HEADHUNTER) && level.getRandom().nextFloat() < HeavyRules.HEADHUNTER_CHANCE) {
			ItemStack head = Trophies.headOf(killed);
			if (head != null) {
				killed.spawnAtLocation(level, head);
				chip(player, HEADHUNTER, head.getHoverName());
			}
		}
	}

	public static void forget(UUID player) {
		Woodcraft.forget(player);
		Stance.forget(player);
		Strikes.forget(player);
	}
}
