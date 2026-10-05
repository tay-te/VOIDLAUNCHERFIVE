package dev.voidmc.mettle.core;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Everything Mettle does to vanilla numbers goes through attribute modifiers with fixed ids, rebuilt here
 * (ARCHITECTURE.md §6). Idempotent: a modifier is only touched when its amount changes, because every change resyncs
 * the attribute to the client.
 *
 * <p>Call on join, after respawn (respawning drops every modifier) and whenever the character changes.
 */
public final class EffectsApplier {
	/** Carry capacity per point of Strength, counted from 1 (BALANCE.md §4). */
	public static final double CAPACITY_PER_STRENGTH = 3.0;

	private EffectsApplier() {
	}

	public static void apply(ServerPlayer player) {
		CharacterState state = CharacterStore.get(player);
		Background background = CharacterStore.background(player.level().registryAccess(), state);

		int strength = state.attribute(CharacterAttribute.STRENGTH, background);
		int agility = state.attribute(CharacterAttribute.AGILITY, background);
		int vitality = state.attribute(CharacterAttribute.VITALITY, background);
		int sOver = strength - CharacterAttribute.BASE;
		int aOver = agility - CharacterAttribute.BASE;
		int vOver = vitality - CharacterAttribute.BASE;

		set(player, MettleAttributes.CARRY_CAPACITY, CharacterAttribute.STRENGTH, "carry_capacity", strength * CAPACITY_PER_STRENGTH, AttributeModifier.Operation.ADD_VALUE, false);
		set(player, Attributes.ATTACK_DAMAGE, CharacterAttribute.STRENGTH, "attack_damage", 0.02 * sOver, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
		set(player, Attributes.BLOCK_BREAK_SPEED, CharacterAttribute.STRENGTH, "block_break_speed", 0.02 * sOver, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
		set(player, Attributes.MOVEMENT_SPEED, CharacterAttribute.AGILITY, "movement_speed", 0.01 * aOver, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
		set(player, Attributes.ATTACK_SPEED, CharacterAttribute.AGILITY, "attack_speed", 0.015 * aOver, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
		set(player, Attributes.SNEAKING_SPEED, CharacterAttribute.AGILITY, "sneaking_speed", 0.02 * aOver, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
		// Half a heart per two points above 3. Permanent, so it is saved with the player and health is not clamped to
		// the vanilla 20 when the world loads.
		set(player, Attributes.MAX_HEALTH, CharacterAttribute.VITALITY, "max_health", Math.floorDiv(vOver, 2), AttributeModifier.Operation.ADD_VALUE, true);
		perkEffects(player, state);
	}

	/**
	 * Perks that are plain attribute changes ("attr" hooks in TREES.md): one modifier per perk and attribute, sized by
	 * rank. Modifiers of perks no longer held are removed.
	 */
	private static void perkEffects(ServerPlayer player, CharacterState state) {
		Registry<Perk> perks = player.level().registryAccess().lookupOrThrow(MettleRegistries.PERK);
		Set<Identifier> wanted = new HashSet<>();
		state.perks().forEach((key, rank) -> perks.getOptional(key).ifPresent(perk -> {
			for (Perk.AttributeEffect e : perk.effects()) {
				Optional<Holder.Reference<Attribute>> attribute = BuiltInRegistries.ATTRIBUTE.get(e.attribute());
				if (attribute.isEmpty() || e.amounts().isEmpty()) {
					continue;
				}
				Identifier id = Mettle.id("perk/" + key.identifier().getPath() + "/" + e.attribute().getPath());
				wanted.add(id);
				double amount = e.amounts().get(Math.min(rank, e.amounts().size()) - 1);
				AttributeModifier.Operation op = switch (e.operation()) {
					case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
					case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
					default -> AttributeModifier.Operation.ADD_VALUE;
				};
				AttributeInstance instance = player.getAttribute(attribute.get());
				if (instance != null) {
					AttributeModifier current = instance.getModifier(id);
					if (current == null || current.amount() != amount || current.operation() != op) {
						instance.removeModifier(id);
						// Max health is saved with the player, so health is not clamped to the vanilla 20 on load (§6).
						if (attribute.get().is(Attributes.MAX_HEALTH)) {
							instance.addPermanentModifier(new AttributeModifier(id, amount, op));
						} else {
							instance.addTransientModifier(new AttributeModifier(id, amount, op));
						}
					}
				}
			}
		}));
		// Drop the modifiers of refunded perks (positional ones such as Hard Hat's armour manage themselves).
		for (AttributeInstance instance : player.getAttributes().getSyncableAttributes()) {
			for (AttributeModifier m : List.copyOf(instance.getModifiers())) {
				String path = m.id().getPath();
				if (m.id().getNamespace().equals(Mettle.ID) && path.startsWith("perk/") && !path.endsWith("_armor") && !wanted.contains(m.id())) {
					instance.removeModifier(m.id());
				}
			}
		}
	}

	private static void set(ServerPlayer player, Holder<Attribute> attribute, CharacterAttribute source, String target, double amount,
		AttributeModifier.Operation operation, boolean permanent) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		Identifier id = Mettle.id("attr/" + source.getSerializedName() + "/" + target.toLowerCase(Locale.ROOT));
		AttributeModifier current = instance.getModifier(id);
		if (amount == 0.0) {
			if (current != null) {
				instance.removeModifier(id);
			}
			return;
		}
		if (current != null && current.amount() == amount && current.operation() == operation) {
			return;
		}
		instance.removeModifier(id);
		AttributeModifier modifier = new AttributeModifier(id, amount, operation);
		if (permanent) {
			instance.addPermanentModifier(modifier);
		} else {
			instance.addTransientModifier(modifier);
		}
	}
}
