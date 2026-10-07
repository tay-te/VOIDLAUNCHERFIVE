package dev.voidmc.mettle.character.skill;

import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.character.Progress;
import dev.voidmc.mettle.character.skill.athletics.AthleticsRules;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Shared combat plumbing for the four combat trees: which weapon a hit was made with, and the XP every hit, block and
 * kill earns (BALANCE.md §3). Each tree's perks live in its own class (BladesSkill, HeavyWeaponsSkill,
 * MarksmanshipSkill, DefenseSkill); this class only decides who learns what from a fight.
 */
public final class Combat {
	public static final ResourceKey<Skill> BLADES = Progress.skill("blades");
	public static final ResourceKey<Skill> HEAVY_WEAPONS = Progress.skill("heavy_weapons");
	public static final ResourceKey<Skill> MARKSMANSHIP = Progress.skill("marksmanship");
	public static final ResourceKey<Skill> DEFENSE = Progress.skill("defense");

	public enum Weapon { BLADE, HEAVY, RANGED }

	/** Defense's per-minute cap, per player (CombatXp#DEFENSE_CAP_PER_MINUTE). */
	private static final Map<UUID, AthleticsRules.Meter> DEFENSE_METERS = new HashMap<>();

	private Combat() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Combat::afterDamage);
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, source) -> {
			if (killer instanceof ServerPlayer player && !player.isCreative()) {
				ResourceKey<Skill> skill = skillFor(source, player);
				if (skill != null) {
					Progress.award(player, skill, CombatXp.kill(killed.getMaxHealth()) * targetFactor(killed));
				}
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> DEFENSE_METERS.remove(player.getUUID()));
	}

	/** A melee weapon's class: swords and spears are blades; axes, maces and tridents are heavy. */
	public static @Nullable Weapon melee(ItemStack stack) {
		if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.SPEARS)) {
			return Weapon.BLADE;
		}
		if (stack.is(ItemTags.AXES) || stack.is(Items.MACE) || stack.is(Items.TRIDENT)) {
			return Weapon.HEAVY;
		}
		return null;
	}

	/** The weapon a damage source was dealt with by this player, or null if it wasn't a weapon hit of theirs. */
	public static @Nullable Weapon weapon(DamageSource source, Player player) {
		if (source.getEntity() != player) {
			return null;
		}
		if (source.getDirectEntity() instanceof AbstractArrow || source.getDirectEntity() instanceof ThrownTrident) {
			return Weapon.RANGED;
		}
		return source.getDirectEntity() == player ? melee(player.getMainHandItem()) : null;
	}

	private static @Nullable ResourceKey<Skill> skillFor(DamageSource source, ServerPlayer player) {
		if (source.is(dev.voidmc.mettle.character.skill.blades.BleedDamage.BLEED) && source.getEntity() == player) {
			return BLADES;
		}
		Weapon w = weapon(source, player);
		if (w == null) {
			return null;
		}
		return switch (w) {
			case BLADE -> BLADES;
			case HEAVY -> HEAVY_WEAPONS;
			case RANGED -> MARKSMANSHIP;
		};
	}

	/** Fighting animals and players teaches half as much; everything else full (spawner mobs: see CHARACTER.md §3.3). */
	public static double targetFactor(LivingEntity target) {
		if (target instanceof Animal) {
			return 0.5;
		}
		if (target instanceof Player) {
			return 0.5;
		}
		return 1.0;
	}

	private static void afterDamage(LivingEntity target, DamageSource source, float baseTaken, float taken, boolean blocked) {
		// Defense: what a player's shield blocked, and what their armour absorbed of the rest. "taken" is the hit
		// after the shield and before armour (Fabric passes hurtServer's own amount).
		if (target instanceof ServerPlayer defender && !defender.isCreative()) {
			double blockedXp = blocked ? Math.max(0, baseTaken - taken) : 0;
			double absorbed = 0;
			if (taken > 0 && defender.getArmorValue() > 0 && !source.is(DamageTypeTags.BYPASSES_ARMOR)) {
				absorbed = taken * CombatXp.armourFraction(defender.getArmorValue(), defender.getAttributeValue(Attributes.ARMOR_TOUGHNESS), taken);
			}
			double xp = CombatXp.defense(blockedXp, absorbed);
			if (xp > 0) {
				double paid = DEFENSE_METERS.computeIfAbsent(defender.getUUID(), u -> new AthleticsRules.Meter(CombatXp.DEFENSE_CAP_PER_MINUTE))
					.take(xp, defender.level().getGameTime());
				Progress.award(defender, DEFENSE, paid);
			}
		}
		// Weapons: damage dealt, capped at what the target had left.
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != target && !attacker.isCreative()) {
			ResourceKey<Skill> skill = skillFor(source, attacker);
			if (skill == null) {
				return;
			}
			double dealt = Math.min(taken, target.getHealth() + taken);
			double xp = CombatXp.hit(skill == MARKSMANSHIP, dealt, attacker.distanceTo(target)) * targetFactor(target);
			Progress.award(attacker, skill, xp);
		}
	}
}
