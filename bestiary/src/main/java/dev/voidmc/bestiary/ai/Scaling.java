package dev.voidmc.bestiary.ai;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.bestiary.ai.profile.CreatureProfile;
import dev.voidmc.bestiary.danger.DangerMath;
import dev.voidmc.bestiary.elite.Affix;
import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.FrayAttributes;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What danger, difficulty and elite status do to a creature's numbers (BESTIARY.md §6.2, §6.3, §7): health, poise,
 * armour, speed and size as attribute modifiers with fixed ids (applying twice changes nothing; they save with the
 * entity like any vanilla modifier), damage as a factor in Fray's pipeline (and on arrows).
 */
public final class Scaling {
	public static final Identifier HEALTH = Bestiary.id("danger_health");
	public static final Identifier POISE = Bestiary.id("danger_poise");
	public static final Identifier ELITE_SCALE = Bestiary.id("elite_scale");
	public static final Identifier IRON_ARMOUR = Bestiary.id("ironbound_armour");
	public static final Identifier IRON_POISE = Bestiary.id("ironbound_poise");
	public static final Identifier SWIFT = Bestiary.id("swift");
	public static final Identifier FAMILY = Bestiary.id("family");
	/** Elites (§7): health ×3, damage ×1.4, drawn 1.15× bigger (poise ×1.5 is Fray's elite rank). */
	public static final float ELITE_HEALTH = 3, ELITE_DAMAGE = 1.4F, ELITE_SIZE = 0.15F;
	/** Leaders buff allies within 12 blocks by 15 % (§5). */
	public static final float LEADER_BUFF = 1.15F;

	private Scaling() {
	}

	private static void modifier(Mob mob, Holder<Attribute> attr, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance inst = mob.getAttribute(attr);
		if (inst == null) {
			return;
		}
		inst.removeModifier(id);
		if (amount != 0) {
			inst.addPermanentModifier(new AttributeModifier(id, amount, op));
		}
	}

	/** Applies the modifiers for this creature's data (idempotent). {@code fresh}: a new creature starts at full health. */
	public static void apply(Mob mob, CreatureProfile p, CreatureData d, boolean fresh) {
		float health = DangerMath.health(d.danger()) * DangerMath.difficultyHealth(mob.level().getDifficulty().getId()) * (d.elite() ? ELITE_HEALTH : 1);
		float frac = mob.getHealth() / Math.max(1, mob.getMaxHealth());
		modifier(mob, Attributes.MAX_HEALTH, HEALTH, health - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (!Float.isNaN(p.poise()) && mob.getAttribute(FrayAttributes.POISE) != null) {
			mob.getAttribute(FrayAttributes.POISE).setBaseValue(p.poise());
		}
		modifier(mob, FrayAttributes.POISE, POISE, DangerMath.poise(d.danger()) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(mob, Attributes.SCALE, ELITE_SCALE, d.elite() ? ELITE_SIZE : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modifier(mob, Attributes.ARMOR, IRON_ARMOUR, d.has(Affix.IRONBOUND.id) ? 6 : 0, AttributeModifier.Operation.ADD_VALUE);
		modifier(mob, FrayAttributes.POISE, IRON_POISE, d.has(Affix.IRONBOUND.id) ? 0.6 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(mob, Attributes.MOVEMENT_SPEED, SWIFT, d.has(Affix.SWIFT.id) ? 0.3 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		for (Map.Entry<DamageKind, Float> e : p.resist().entrySet()) {
			Holder<Attribute> h = FrayAttributes.RESIST.get(e.getKey());
			if (h != null) {
				modifier(mob, h, FAMILY, e.getValue(), AttributeModifier.Operation.ADD_VALUE);
			}
		}
		mob.setHealth(fresh ? mob.getMaxHealth() : Math.max(1, frac * mob.getMaxHealth()));
	}

	/** The damage factor of everything this creature deals: danger × difficulty × elite × its leader's buff. */
	public static float damage(Mob mob) {
		CreatureData d = CreatureData.of(mob);
		float danger = d.danger() < 1 ? 1 : d.danger();
		float f = DangerMath.damage(danger) * DangerMath.difficultyDamage(mob.level().getDifficulty().getId());
		if (d.elite()) {
			f *= ELITE_DAMAGE;
		}
		if (d.leader().isPresent() && leaderNear(mob, d.leader().get())) {
			f *= LEADER_BUFF;
		}
		return f;
	}

	static boolean leaderNear(Mob mob, UUID leader) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		Entity l = level.getEntity(leader);
		return l != null && l.isAlive() && l.distanceToSqr(mob) <= 12 * 12;
	}
}
