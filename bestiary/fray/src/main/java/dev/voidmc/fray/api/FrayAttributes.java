package dev.voidmc.fray.api;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Fray's attributes (CONTRACTS.md §7.1), client-synced. Fray holds the neutral defaults; Mettle, Arsenal and Alembic
 * add modifiers. Registered when this class loads (Fray's init touches it first).
 *
 * @since 0.1.0 (2026-10-21)
 */
public final class FrayAttributes {
	public static final Holder<Attribute> MAX_STAMINA = reg("max_stamina", 100, 0, 1000, Attribute.Sentiment.POSITIVE);
	public static final Holder<Attribute> STAMINA_REGEN = reg("stamina_regen", 25, 0, 200, Attribute.Sentiment.POSITIVE);
	public static final Holder<Attribute> MAX_MANA = reg("max_mana", 40, 0, 2000, Attribute.Sentiment.POSITIVE);
	public static final Holder<Attribute> MANA_REGEN = reg("mana_regen", 2, 0, 100, Attribute.Sentiment.POSITIVE);
	public static final Holder<Attribute> POISE = reg("poise", 20, 0, 10000, Attribute.Sentiment.POSITIVE);
	public static final Holder<Attribute> SPELL_POWER = reg("spell_power", 1, 0, 5, Attribute.Sentiment.POSITIVE);
	public static final Holder<Attribute> CAST_SPEED = reg("cast_speed", 1, 0.25, 3, Attribute.Sentiment.POSITIVE);
	public static final Holder<Attribute> CRIT_CHANCE = reg("crit_chance", 0, 0, 1, Attribute.Sentiment.POSITIVE);
	public static final Holder<Attribute> CRIT_DAMAGE = reg("crit_damage", 1.5, 1, 5, Attribute.Sentiment.POSITIVE);
	public static final Holder<Attribute> MAX_TOXICITY = reg("max_toxicity", 100, 0, 1000, Attribute.Sentiment.POSITIVE);
	public static final Map<DamageKind, Holder<Attribute>> RESIST = new EnumMap<>(DamageKind.class);
	public static final Map<StatusType, Holder<Attribute>> STATUS_RESIST = new EnumMap<>(StatusType.class);

	static {
		for (DamageKind k : DamageKind.VALUES) {
			RESIST.put(k, reg("resist/" + k.id(), 0, -1, 0.9, Attribute.Sentiment.POSITIVE));
		}
		for (StatusType s : StatusType.METERED) {
			STATUS_RESIST.put(s, reg("status_resist/" + s.id(), 0, -1, 10, Attribute.Sentiment.POSITIVE));
		}
	}

	private FrayAttributes() {
	}

	private static Holder<Attribute> reg(String path, double def, double min, double max, Attribute.Sentiment sentiment) {
		Identifier id = Identifier.fromNamespaceAndPath("fray", path);
		return Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, id,
			new RangedAttribute("attribute.name.fray." + path.replace('/', '.'), def, min, max).setSyncable(true).setSentiment(sentiment));
	}

	/** Forces registration (called from Fray's init). */
	public static void init() {
	}
}
