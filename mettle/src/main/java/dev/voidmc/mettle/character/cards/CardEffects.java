package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.api.Keyword;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Trait;
import dev.voidmc.mettle.character.skill.Combat;
import dev.voidmc.mettle.character.skill.survival.Camp;
import dev.voidmc.mettle.core.CardStore;
import dev.voidmc.mettle.core.CardsState;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * What slotted cards do to vanilla numbers (docs/CARDS.md §5.3): one attribute modifier per active trait
 * ({@code mettle:trait/<skill>/<trait>}, the capped total of its slotted copies) and per set tier
 * ({@code mettle:set/<keyword>/<tier>}). Conditional ones ("while you hold a blade", "while you sprint") are re-checked
 * every 10 ticks. Behaviour traits and sets read {@link #value} / {@link #tier} from their hooks.
 */
public final class CardEffects {
	private static final Map<UUID, Integer> CONDITIONS = new HashMap<>();
	private static final String[] WHEN = {"holding_blade", "holding_heavy", "sprinting", "on_path", "bloodied_blade"};

	private CardEffects() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				CardsState cards = CardStore.get(player);
				if (cards.traits().isEmpty() && cards.tiers().isEmpty()) {
					continue;
				}
				int mask = conditions(player);
				Integer before = CONDITIONS.put(player.getUUID(), mask);
				if (before == null || before != mask) {
					apply(player);
				}
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> CONDITIONS.remove(player.getUUID()));
	}

	// ---------------------------------------------------------------- reading (both sides)

	/** A trait's active total for the player, in its unit (9 = 9 %), boosted and capped; 0 when none is slotted. */
	public static double value(Player player, Identifier trait) {
		return CardStore.get(player).trait(trait);
	}

	/** A percent trait's active total as a fraction (9 % → 0.09). */
	public static double fraction(Player player, Identifier trait) {
		return CardStore.get(player).trait(trait) / 100.0;
	}

	/** The tier an active set reached for the player (0, 2, 4 or 6). */
	public static int tier(Player player, Identifier keyword) {
		return CardStore.get(player).tier(keyword);
	}

	public static boolean set(Player player, Identifier keyword, int tier) {
		return tier(player, keyword) >= tier;
	}

	/** Whether a condition of a trait or set effect holds now. */
	public static boolean holds(Player player, String when) {
		return switch (when) {
			case "always" -> true;
			case "holding_blade" -> Combat.melee(player.getMainHandItem()) == Combat.Weapon.BLADE;
			case "holding_heavy" -> Combat.melee(player.getMainHandItem()) == Combat.Weapon.HEAVY;
			case "sprinting" -> player.isSprinting();
			case "on_path" -> Camp.onPath(player);
			case "bloodied_blade" -> player.getHealth() < player.getMaxHealth() / 2 && Combat.melee(player.getMainHandItem()) == Combat.Weapon.BLADE;
			default -> false;
		};
	}

	private static int conditions(Player player) {
		int mask = 0;
		for (int i = 0; i < WHEN.length; i++) {
			if (holds(player, WHEN[i])) {
				mask |= 1 << i;
			}
		}
		return mask;
	}

	// ---------------------------------------------------------------- the modifiers

	/** Adds, updates and removes the trait and set modifiers to match the player's cards. Idempotent. */
	public static void apply(ServerPlayer player) {
		CardsState cards = CardStore.get(player);
		Registry<Trait> traits = player.level().registryAccess().lookupOrThrow(MettleRegistries.TRAIT);
		Registry<Keyword> keywords = player.level().registryAccess().lookupOrThrow(MettleRegistries.KEYWORD);
		Set<Identifier> wanted = new HashSet<>();
		Registry<dev.voidmc.mettle.api.Talent> talents = player.level().registryAccess().lookupOrThrow(MettleRegistries.TALENT);
		cards.traits().forEach((id, total) -> effectOf(traits, talents, id).ifPresent(e -> {
			if (e.attribute().isPresent() && holds(player, e.when())) {
				Identifier mid = Mettle.id("trait/" + id.getPath());
				// A jump or swim promise ("X higher", "X faster") becomes the strength or efficiency that gives it (CardPhysics).
				double amount = CardPhysics.attributeAmount(e.attribute().get().toString(), e.operation(), total * e.scale());
				if (put(player, e.attribute().get(), mid, amount, e.operation())) {
					wanted.add(mid);
				}
			}
		}));
		Map<Identifier, Integer> tiers = new HashMap<>(cards.tiers());
		// A Dragon Standard lends its holder's best set to allies near it (Dragonfall).
		MythicEffects.borrowed(player).forEach((k, t) -> tiers.merge(k, t, Math::max));
		tiers.forEach((id, reached) -> keywords.getOptional(id).ifPresent(k -> {
			for (int tier : new int[] {2, 4, 6}) {
				Keyword.SetBonus bonus = k.sets().get(tier);
				if (tier > reached || bonus == null) {
					continue;
				}
				List<Keyword.Modifier> mods = bonus.effects();
				for (int i = 0; i < mods.size(); i++) {
					Keyword.Modifier m = mods.get(i);
					if (!holds(player, m.when())) {
						continue;
					}
					Identifier mid = Mettle.id("set/" + id.getPath() + "/" + tier + (i == 0 ? "" : "_" + i));
					if (put(player, m.attribute(), mid, CardPhysics.attributeAmount(m.attribute().toString(), m.operation(), m.amount()), m.operation())) {
						wanted.add(mid);
					}
				}
			}
		}));
		for (Holder.Reference<Attribute> attribute : BuiltInRegistries.ATTRIBUTE.listElements().toList()) {
			AttributeInstance instance = player.getAttributes().getInstance(attribute);
			if (instance == null) {
				continue;
			}
			for (AttributeModifier m : List.copyOf(instance.getModifiers())) {
				String path = m.id().getPath();
				if (m.id().getNamespace().equals(Mettle.ID) && (path.startsWith("trait/") || path.startsWith("set/")) && !wanted.contains(m.id())) {
					instance.removeModifier(m.id());
				}
			}
		}
		// What cards add to movement stays under its ceiling (docs/CARDS.md §14).
		Ceilings.movement(player);
	}

	/** A trait's effect, or a talent's (its value is the trait {@code mettle:talent/<id>}, BUILD_FEEL.md §13). */
	static Optional<Trait.Effect> effectOf(Registry<Trait> traits, Registry<dev.voidmc.mettle.api.Talent> talents, Identifier id) {
		if (id.getPath().startsWith("talent/")) {
			return talents.getOptional(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring("talent/".length())))
				.map(dev.voidmc.mettle.api.Talent::effect);
		}
		return traits.getOptional(id).map(Trait::effect);
	}

	/** Sets one modifier, touching it only when its amount changed. False when the attribute doesn't exist here. */
	private static boolean put(ServerPlayer player, Identifier attributeId, Identifier id, double amount, String operation) {
		Optional<Holder.Reference<Attribute>> attribute = BuiltInRegistries.ATTRIBUTE.get(attributeId);
		if (attribute.isEmpty()) {
			return false;
		}
		AttributeInstance instance = player.getAttribute(attribute.get());
		if (instance == null) {
			return false;
		}
		AttributeModifier.Operation op = switch (operation) {
			case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
			case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
			default -> AttributeModifier.Operation.ADD_VALUE;
		};
		AttributeModifier current = instance.getModifier(id);
		if (current != null && current.amount() == amount && current.operation() == op) {
			return true;
		}
		instance.removeModifier(id);
		AttributeModifier modifier = new AttributeModifier(id, amount, op);
		// Max health is saved with the player, so health is not clamped to the vanilla 20 on load (ARCHITECTURE.md §6).
		if (attribute.get().is(Attributes.MAX_HEALTH)) {
			instance.addPermanentModifier(modifier);
		} else {
			instance.addTransientModifier(modifier);
		}
		return true;
	}
}
