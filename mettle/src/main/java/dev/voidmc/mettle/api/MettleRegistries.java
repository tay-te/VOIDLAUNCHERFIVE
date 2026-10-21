package dev.voidmc.mettle.api;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Mettle's data registries. Both are datapack registries synced to clients, loaded from
 * {@code data/<namespace>/mettle/background/*.json}, {@code .../skill/*.json} and {@code .../perk/<skill>/*.json}.
 */
public final class MettleRegistries {
	public static final ResourceKey<Registry<Background>> BACKGROUND = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("mettle", "background"));
	public static final ResourceKey<Registry<Perk>> PERK = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("mettle", "perk"));
	public static final ResourceKey<Registry<Skill>> SKILL = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("mettle", "skill"));
	/** Card traits (docs/CARDS.md §4), {@code .../trait/<skill>/<id>.json}. */
	public static final ResourceKey<Registry<Trait>> TRAIT = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("mettle", "trait"));
	/** Card keywords and their set bonuses (§6), {@code .../keyword/<id>.json}. */
	public static final ResourceKey<Registry<Keyword>> KEYWORD = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("mettle", "keyword"));
	/** Mythic cards (§9), {@code .../mythic/<id>.json}. */
	public static final ResourceKey<Registry<Mythic>> MYTHIC = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("mettle", "mythic"));
	/** Each skill's card prefix, native keywords and keyword pool (§2.1, §6.2), {@code .../card_skill/<skill>.json}. */
	public static final ResourceKey<Registry<CardSkill>> CARD_SKILL = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("mettle", "card_skill"));

	/**
	 * The words bynames are made of (docs/BUILD_FEEL.md §2.1): {@code .../byname/keywords.json}, {@code skills.json} and
	 * {@code pairs.json}, so a server can rename them.
	 */
	public static final ResourceKey<Registry<BynameWords>> BYNAME = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("mettle", "byname"));

	private MettleRegistries() {
	}
}
