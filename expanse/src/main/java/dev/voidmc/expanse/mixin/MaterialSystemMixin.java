package dev.voidmc.expanse.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.expanse.world.terrain.SurfaceBands;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The surface pass on the Earth ({@link SurfaceBands}): the chunk's rule context gets the bands of its rule
 * before the rule is compiled (so the ore veins fill only the heights they are read at), and the walk down
 * each column does not ask the rule about stone under the preliminary surface at a height where nothing in
 * the rule can change it; the rule would have answered null, which leaves the block as it is. The walk itself,
 * its stone depths and water heights, is vanilla's, untouched.
 */
@Mixin(MaterialSystem.class)
abstract class MaterialSystemMixin implements SurfaceBands.Earthly {
	@Unique
	private volatile boolean expanse$earth;

	@Override
	public void expanse$markEarth() {
		this.expanse$earth = true;
	}

	@WrapOperation(method = "buildSurface", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/levelgen/material/rule/MaterialRule;compile(Lnet/minecraft/world/level/levelgen/material/MaterialRuleContext;)Lnet/minecraft/world/level/levelgen/material/rule/RuleEvaluator;"))
	private RuleEvaluator expanse$compileWithBands(MaterialRule rule, MaterialRuleContext context, Operation<RuleEvaluator> original,
		@Local(argsOnly = true) WorldGenerationContext generationContext, @Local(argsOnly = true) ChunkAccess protoChunk,
		@Local(argsOnly = true) @Nullable Set<Holder<Biome>> possibleBiomes) {
		if (this.expanse$earth) {
			LevelHeightAccessor heights = protoChunk.getHeightAccessorForGeneration();
			((SurfaceBands.Banded) (Object) context).expanse$setBands(
				SurfaceBands.of(rule, possibleBiomes, generationContext, heights.getMinY(), heights.getMaxY()));
		}
		return original.call(rule, context);
	}

	@WrapOperation(method = "buildSurface", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/levelgen/material/rule/RuleEvaluator;tryApply(III)Lnet/minecraft/world/level/block/state/BlockState;"))
	private @Nullable BlockState expanse$leaveDeepStone(RuleEvaluator rule, int blockX, int blockY, int blockZ, Operation<BlockState> original,
		@Local MaterialRuleContext context) {
		SurfaceBands bands = ((SurfaceBands.Banded) (Object) context).expanse$bands();
		if (bands != null && bands.quiet(blockY) && blockY < context.getMinSurfaceLevel()) {
			return null;
		}
		return original.call(rule, blockX, blockY, blockZ);
	}
}
