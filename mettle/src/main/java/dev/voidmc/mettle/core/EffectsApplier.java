package dev.voidmc.mettle.core;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Background;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
	public static final double CAPACITY_PER_STRENGTH = 5.0;
	private static final double OLD_BASE_CARRY_CAPACITY = 40.0;

	/**
	 * Run first on every {@link #apply}: work that the modifiers depend on (cards v2 works out which slotted cards are
	 * active, docs/CARDS.md §5.3). They may set attachments but never call {@link #apply}.
	 */
	public static final List<java.util.function.Consumer<ServerPlayer>> BEFORE = new java.util.concurrent.CopyOnWriteArrayList<>();
	/** Run last on every {@link #apply}: other modifiers with fixed ids (card traits and sets). */
	public static final List<java.util.function.Consumer<ServerPlayer>> AFTER = new java.util.concurrent.CopyOnWriteArrayList<>();
	/** The keyword whose set softens cursed cards (docs/CARDS.md §6.3, Hex 2 and 4). */
	private static final Identifier HEX = Identifier.fromNamespaceAndPath(Mettle.ID, "hex");

	private EffectsApplier() {
	}

	/**
	 * The perks a player has active: those in the character record (earned perks, and drafted ones an op gave outside
	 * the deck) and those of slotted, non-dormant cards (docs/CARDS.md §5.3), the higher rank where both give one.
	 */
	public static Map<net.minecraft.resources.ResourceKey<Perk>, Integer> activePerks(CharacterState state, CardsState cards) {
		if (cards.active().isEmpty()) {
			return state.perks();
		}
		Map<net.minecraft.resources.ResourceKey<Perk>, Integer> out = new HashMap<>(state.perks());
		cards.active().forEach((k, v) -> out.merge(k, v, Math::max));
		return out;
	}

	public static void apply(ServerPlayer player) {
		for (var hook : BEFORE) {
			hook.accept(player);
		}
		CharacterState state = CharacterStore.get(player);
		Background background = CharacterStore.background(player.level().registryAccess(), state);

		int strength = state.attribute(CharacterAttribute.STRENGTH, background);
		int agility = state.attribute(CharacterAttribute.AGILITY, background);
		int vitality = state.attribute(CharacterAttribute.VITALITY, background);
		int sOver = strength - CharacterAttribute.BASE;
		int aOver = agility - CharacterAttribute.BASE;
		int vOver = vitality - CharacterAttribute.BASE;

		// Players saved before the 2026-10 balance pass kept the old default base (40) in their attribute data.
		var capacity = player.getAttribute(MettleAttributes.CARRY_CAPACITY);
		if (capacity != null && capacity.getBaseValue() == OLD_BASE_CARRY_CAPACITY) {
			capacity.setBaseValue(MettleAttributes.BASE_CARRY_CAPACITY);
		}
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
		for (var hook : AFTER) {
			hook.accept(player);
		}
	}

	/**
	 * Perks that are plain attribute changes ("attr" hooks in TREES.md): one modifier per perk and attribute, sized by
	 * rank. Modifiers of perks no longer held are removed.
	 */
	private static void perkEffects(ServerPlayer player, CharacterState state) {
		Registry<Perk> perks = player.level().registryAccess().lookupOrThrow(MettleRegistries.PERK);
		Set<Identifier> wanted = new HashSet<>();
		CardsState cards = CardStore.get(player);
		int hex = cards.tier(HEX);
		activePerks(state, cards).forEach((key, rank) -> perks.getOptional(key).ifPresent(perk -> {
			for (Perk.AttributeEffect e : perk.effects()) {
				Optional<Holder.Reference<Attribute>> attribute = BuiltInRegistries.ATTRIBUTE.get(e.attribute());
				if (attribute.isEmpty() || e.amounts().isEmpty()) {
					continue;
				}
				Identifier id = Mettle.id("perk/" + key.identifier().getPath() + "/" + e.attribute().getPath());
				wanted.add(id);
				double amount = e.amounts().get(Math.min(rank, e.amounts().size()) - 1);
				if (perk.kind() == Perk.Kind.CURSED && hex >= 2) {
					amount = hexed(e.attribute(), amount, hex);
				}
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
		// Drop the modifiers of refunded perks (positional ones such as Hard Hat's armour manage themselves). Every
		// attribute, not only the synced ones: attack damage is server-only, and cursed cards change it.
		for (Holder.Reference<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.listElements().toList()) {
			AttributeInstance instance = player.getAttributes().getInstance(attribute);
			if (instance == null) {
				continue;
			}
			for (AttributeModifier m : List.copyOf(instance.getModifiers())) {
				String path = m.id().getPath();
				if (m.id().getNamespace().equals(Mettle.ID) && path.startsWith("perk/") && !path.endsWith("_armor") && !wanted.contains(m.id())) {
					instance.removeModifier(m.id());
				}
			}
		}
	}

	/**
	 * Hex 2: a cursed card's downside is 25 % smaller; Hex 4: its upside 15 % stronger. A downside is a negative amount,
	 * except fall damage, where more is worse.
	 */
	public static double hexed(Identifier attribute, double amount, int hexTier) {
		boolean worse = attribute.getPath().equals("fall_damage_multiplier") ? amount > 0 : amount < 0;
		if (worse) {
			return amount * 0.75;
		}
		return hexTier >= 4 ? amount * 1.15 : amount;
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
