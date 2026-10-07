package dev.voidmc.gloam.mixin;

import dev.voidmc.gloam.season.SeasonResolvers;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.ColorResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's grass, foliage and leaf-litter colour resolvers, wrapped so they give the season's colours
 * ({@link SeasonResolvers}). Everything that tints blocks by biome asks these: the client level's blended tint (and so
 * the chunk meshes and every block tint source), Sodium's own colour cache, VOID LOD's texture colours. The client
 * level keys its tint caches by these very objects, so they are swapped once, as the class is set up.
 */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {
	@Shadow
	@Final
	@Mutable
	public static ColorResolver GRASS_COLOR_RESOLVER;

	@Shadow
	@Final
	@Mutable
	public static ColorResolver FOLIAGE_COLOR_RESOLVER;

	@Shadow
	@Final
	@Mutable
	public static ColorResolver DRY_FOLIAGE_COLOR_RESOLVER;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void gloam$seasons(CallbackInfo ci) {
		GRASS_COLOR_RESOLVER = SeasonResolvers.grass(GRASS_COLOR_RESOLVER);
		FOLIAGE_COLOR_RESOLVER = SeasonResolvers.foliage(FOLIAGE_COLOR_RESOLVER);
		DRY_FOLIAGE_COLOR_RESOLVER = SeasonResolvers.dryFoliage(DRY_FOLIAGE_COLOR_RESOLVER);
	}
}
