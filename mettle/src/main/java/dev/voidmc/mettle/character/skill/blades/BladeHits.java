package dev.voidmc.mettle.character.skill.blades;

import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.BladesSkill;
import dev.voidmc.mettle.character.skill.Combat;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * How hard a blade hit lands: Quick Draw, Momentum, Single Combat, Giant Slayer and Executioner's finishing blow on
 * bosses and players. The bonuses add up (+60 % and +20 % make +80 %), and one chip names every perk that fired.
 */
public final class BladeHits {
	private static final Set<EntityType<?>> LARGE = Set.of(EntityTypes.RAVAGER, EntityTypes.HOGLIN, EntityTypes.ZOGLIN, EntityTypes.ENDERMAN,
		EntityTypes.POLAR_BEAR, EntityTypes.WARDEN);
	private static final double ARENA = 8.0;

	private BladeHits() {
	}

	/** Giant Slayer's quarry. */
	public static boolean isLarge(Entity entity) {
		return LARGE.contains(entity.getType());
	}

	/** Executioner never kills these outright; they take +50 % instead. */
	public static boolean bossOrPlayer(LivingEntity entity) {
		return entity instanceof Player || entity.is(ConventionalEntityTypeTags.BOSSES);
	}

	/** A blade hit by {@code player} on {@code target}, before armour. */
	public static float dealt(ServerPlayer player, LivingEntity target, float damage, boolean primary) {
		long now = player.level().getGameTime();
		BladeState state = BladeState.of(player);
		float bonus = 0;
		Chip chip = new Chip();

		int quickDraw = Perks.rank(player, BladesSkill.QUICK_DRAW);
		if (quickDraw > 0 && target instanceof Mob mob && mob.getTarget() != player && mob.getLastHurtByMob() != player) {
			bonus += BladeMath.quickDraw(quickDraw);
			chip.add(BladesSkill.QUICK_DRAW, "quick_draw", 20 * quickDraw);
		}
		if (primary && Perks.has(player, BladesSkill.MOMENTUM)) {
			int stacks = BladeMath.momentum(state.momentumStacks, state.momentumTarget == target.getId(), now - state.momentumAt);
			state.momentumStacks = stacks;
			state.momentumTarget = target.getId();
			state.momentumAt = now;
			if (stacks > 0) {
				bonus += BladeMath.momentumBonus(stacks);
				chip.add(BladesSkill.MOMENTUM, "momentum", 10 * stacks);
			}
		}
		if (Perks.has(player, BladesSkill.SINGLE_COMBAT) && loneHostile(player) == target) {
			bonus += 0.2f;
			chip.add(BladesSkill.SINGLE_COMBAT, "single_combat");
		}
		if (isLarge(target) && Perks.has(player, BladesSkill.GIANT_SLAYER)) {
			bonus += 0.25f;
			chip.add(BladesSkill.GIANT_SLAYER, "giant_slayer");
		}
		if (bossOrPlayer(target) && Perks.has(player, BladesSkill.EXECUTIONER)
			&& BladeMath.finishingBlow(target.getHealth() + target.getAbsorptionAmount(), damage * (1 + bonus), target.getMaxHealth())) {
			bonus += 0.5f;
			chip.add(BladesSkill.EXECUTIONER, "executioner.boss");
		}
		chip.send(player);
		return damage * (1 + bonus);
	}

	/** A hit on a player: Single Combat's guard while they hold a blade against their one foe. */
	public static float taken(ServerPlayer player, DamageSource source, float damage) {
		if (source.getEntity() instanceof Mob attacker && Combat.melee(player.getMainHandItem()) == Combat.Weapon.BLADE
			&& Perks.has(player, BladesSkill.SINGLE_COMBAT) && loneHostile(player) == attacker) {
			BladesSkill.chip(player, BladesSkill.SINGLE_COMBAT, "single_combat.guard");
			return damage * 0.8f;
		}
		return damage;
	}

	/** The only hostile within 8 blocks, or null when there are none or several. */
	public static @Nullable Mob loneHostile(ServerPlayer player) {
		List<Mob> near = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(ARENA),
			m -> m instanceof Enemy && m.isAlive() && m.distanceToSqr(player) <= ARENA * ARENA);
		return near.size() == 1 ? near.getFirst() : null;
	}

	/** One chip for everything a hit set off, named after the first perk. */
	private static final class Chip {
		private @Nullable Identifier perk;
		private @Nullable MutableComponent text;

		void add(Identifier perk, String key, Object... args) {
			Component part = Component.translatable("mettle.feedback.blades." + key, args);
			if (this.text == null) {
				this.perk = perk;
				this.text = part.copy();
			} else {
				this.text.append("   ").append(part);
			}
		}

		void send(ServerPlayer player) {
			if (this.perk != null) {
				BladesSkill.feedback(player, this.perk, this.text);
			}
		}
	}
}
