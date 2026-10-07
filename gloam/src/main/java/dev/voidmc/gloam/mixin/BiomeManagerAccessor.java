package dev.voidmc.gloam.mixin;

import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The hashed world seed a server sends its clients: one per world, the same for everyone in it, so it seeds the air. */
@Mixin(BiomeManager.class)
public interface BiomeManagerAccessor {
	@Accessor("biomeZoomSeed")
	long gloam$biomeZoomSeed();
}
