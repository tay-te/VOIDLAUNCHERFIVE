package dev.voidmc.expanse.mixin;

import dev.voidmc.expanse.Expanse;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Frogs by the water. Vanilla only lets frogs into the swamps, where any patch of grass is near water.
 * LivingWorld adds them to the wet land biomes and the river biome too, along the Earth terrain's rivers,
 * and this keeps them there: outside the biomes in {@code #expanse:habitat/frogs_anywhere} (the swamps), a
 * frog spawns naturally only with water within three blocks.
 */
@Mixin(Frog.class)
abstract class FrogSpawnMixin {
	@Unique
	private static final TagKey<Biome> ANYWHERE = TagKey.create(Registries.BIOME, Expanse.id("habitat/frogs_anywhere"));

	@Inject(method = "checkFrogSpawnRules", at = @At("RETURN"), cancellable = true)
	private static void expanse$byTheWater(EntityType<? extends Animal> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos,
		RandomSource random, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() || reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION
			|| level.getBiome(pos).is(ANYWHERE)) {
			return;
		}
		// While a chunk generates only that chunk may be read (its neighbours have no blocks yet).
		boolean ownChunkOnly = reason == EntitySpawnReason.CHUNK_GENERATION;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dy = -1; dy <= 0; dy++) {
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					p.setWithOffset(pos, dx, dy, dz);
					if (ownChunkOnly && (p.getX() >> 4 != pos.getX() >> 4 || p.getZ() >> 4 != pos.getZ() >> 4)) {
						continue;
					}
					if (level.getFluidState(p).is(FluidTags.WATER)) {
						return;
					}
				}
			}
		}
		cir.setReturnValue(false);
	}
}
