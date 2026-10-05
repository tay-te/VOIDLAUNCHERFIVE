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

	private MettleRegistries() {
	}
}
