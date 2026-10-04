package dev.voidmc.lod.mixin;

import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The hashed world seed a server sends its clients (for the biome blending): one per world, so a server that
 * starts a new world keeps its far view apart from the old one's.
 */
@Mixin(BiomeManager.class)
public interface BiomeManagerAccessor {
	@Accessor("biomeZoomSeed")
	long voidLod$biomeZoomSeed();
}
