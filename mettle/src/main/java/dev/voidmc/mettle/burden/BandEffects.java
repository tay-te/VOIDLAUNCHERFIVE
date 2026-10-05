package dev.voidmc.mettle.burden;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.BandRules;
import dev.voidmc.mettle.api.LoadBand;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The attribute side of the bands (BURDEN.md §3): speed, jump and fall damage on the player, speed on a mount it steers.
 * Fixed ids ({@code mettle:burden/band/<attribute>}, {@code mettle:burden/rider/movement_speed}); a modifier is only
 * touched when its amount changes, because every change resyncs the attribute. Idempotent, so it also restores the
 * modifiers a respawn drops.
 */
public final class BandEffects {
	public static final Identifier SPEED = Mettle.id("burden/band/movement_speed");
	public static final Identifier JUMP = Mettle.id("burden/band/jump_strength");
	public static final Identifier FALL = Mettle.id("burden/band/fall_damage_multiplier");
	public static final Identifier MOUNT = Mettle.id("burden/rider/movement_speed");

	private BandEffects() {
	}

	public static void apply(Player player, LoadBand band, BandRules rules) {
		set(player, Attributes.MOVEMENT_SPEED, SPEED, Bands.scaled(Bands.SPEED, band, rules));
		set(player, Attributes.JUMP_STRENGTH, JUMP, Bands.scaled(Bands.JUMP, band, rules));
		set(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL, Bands.scaled(Bands.FALL, band, rules));
	}

	/** Slows the mount this player steers (Overburdened −20 %, Pinned −40 %); a Burdened rider costs it nothing. */
	public static void mount(@Nullable Entity vehicle, LoadBand band, BandRules rules) {
		if (vehicle instanceof LivingEntity mount) {
			set(mount, Attributes.MOVEMENT_SPEED, MOUNT, Bands.scaled(Bands.MOUNT, band, rules));
		}
	}

	public static void dismount(@Nullable Entity vehicle) {
		if (vehicle instanceof LivingEntity mount) {
			set(mount, Attributes.MOVEMENT_SPEED, MOUNT, 0f);
		}
	}

	private static void set(LivingEntity entity, Holder<Attribute> attribute, Identifier id, float amount) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (amount == 0f) {
			if (current != null) {
				instance.removeModifier(id);
			}
			return;
		}
		if (current != null && current.amount() == amount) {
			return;
		}
		instance.removeModifier(id);
		instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}
}
