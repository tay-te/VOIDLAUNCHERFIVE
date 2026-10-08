package dev.voidmc.lod.mixin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A structure template's palette keeps its blocks by kind in a plain map, filled as they are first asked for. The game's
 * generation threads already share templates; VOID LOD's structure sites ({@code source.StructureSites}) lay out jigsaw
 * structures on a thread of their own as well, and two threads filling that map at once fail chunk generation with a
 * ConcurrentModificationException. A concurrent map makes it safe for all of them.
 */
@Mixin(StructureTemplate.Palette.class)
public abstract class StructurePaletteMixin {
	@Shadow
	@Final
	@Mutable
	private Map<Block, List<StructureTemplate.StructureBlockInfo>> cache;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void voidLod$threadSafe(CallbackInfo ci) {
		this.cache = new ConcurrentHashMap<>();
	}
}
