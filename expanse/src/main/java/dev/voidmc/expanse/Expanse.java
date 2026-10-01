package dev.voidmc.expanse;

import dev.voidmc.expanse.registry.ExpanseBlocks;
import dev.voidmc.expanse.registry.ExpanseItems;
import dev.voidmc.expanse.registry.ExpanseTab;
import dev.voidmc.expanse.registry.WoodSet;
import dev.voidmc.expanse.command.DevHarness;
import dev.voidmc.expanse.command.ExpanseCommands;
import dev.voidmc.expanse.entity.ExpanseEntities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biomes;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.item.v1.BlockTransformerHelper;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Expanse implements ModInitializer {
	public static final String MOD_ID = "expanse";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ExpanseBlocks.init();
		ExpanseItems.init();
		ExpanseEntities.init();
		ExpanseTab.init();
		dev.voidmc.expanse.world.tree.ExpanseTreePlacers.init();
		dev.voidmc.expanse.world.feature.ExpanseFeatures.init();
		dev.voidmc.expanse.world.SitedJigsawStructure.init();
		// The Earth terrain: the model's density function, and the generator that pours its rivers.
		net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("terrain"),
			dev.voidmc.expanse.world.terrain.TerrainFunction.CODEC);
		net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.CHUNK_GENERATOR, id("earth"),
			dev.voidmc.expanse.world.terrain.EarthChunkGenerator.CODEC);
		registerWoodBehaviour();
		addVanillaSpawns();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> ExpanseCommands.register(dispatcher));
		DevHarness.install();
		// The Earth terrain changes how the overworld is made (and its height), so it is a pack a player can
		// switch off when creating a world rather than something baked into the mod's own data.
		FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(mod -> ResourceLoader.registerBuiltinPack(
			id("earth"), mod, Component.literal("VOID Expanse: Earth"), PackActivationType.DEFAULT_ENABLED));
	}

	/** What vanilla does for oak in code rather than data: axes strip it, fire spreads through it. */
	private static void registerWoodBehaviour() {
		FlammableBlockRegistry fire = FlammableBlockRegistry.getDefaultInstance();
		for (WoodSet wood : ExpanseBlocks.woods()) {
			BlockTransformerHelper.registerStripping(wood.log, wood.strippedLog);
			BlockTransformerHelper.registerStripping(wood.wood, wood.strippedWood);
			for (Block b : new Block[]{wood.log, wood.wood, wood.strippedLog, wood.strippedWood}) {
				fire.add(b, 5, 5);
			}
			for (Block b : new Block[]{wood.planks, wood.stairs, wood.slab, wood.fence, wood.fenceGate}) {
				fire.add(b, 5, 20);
			}
			fire.add(wood.leaves, 30, 60);
		}
		fire.add(ExpanseBlocks.AZURE_WISTERIA_LEAVES, 30, 60);
		for (Block b : new Block[]{ExpanseBlocks.HEATHER, ExpanseBlocks.EDELWEISS, ExpanseBlocks.CATTAIL, ExpanseBlocks.SPANISH_MOSS,
			ExpanseBlocks.WISTERIA_BLOSSOMS, ExpanseBlocks.AZURE_WISTERIA_BLOSSOMS}) {
			fire.add(b, 60, 100);
		}
	}

	/** The new animals also turn up in the vanilla biomes they would plausibly live in. */
	private static void addVanillaSpawns() {
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA,
			Biomes.MEADOW, Biomes.GROVE), MobCategory.CREATURE, ExpanseEntities.ELK, 5, 2, 4);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES, Biomes.SNOWY_TAIGA),
			MobCategory.CREATURE, ExpanseEntities.MAMMOTH, 3, 1, 3);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.JUNGLE),
			MobCategory.CREATURE, ExpanseEntities.CAPYBARA, 4, 2, 3);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.BEACH, Biomes.MANGROVE_SWAMP),
			MobCategory.CREATURE, ExpanseEntities.CRAB, 5, 2, 4);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
