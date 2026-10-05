package dev.voidmc.atlas.client.mixin;

import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The hashed world seed a server sends its clients (for biome blending): one per world, so a server that starts a new
 * world keeps its map apart from the old one's.
 */
@Mixin(BiomeManager.class)
public interface BiomeManagerAccessor {
	@Accessor("biomeZoomSeed")
	long atlas$biomeZoomSeed();
}
