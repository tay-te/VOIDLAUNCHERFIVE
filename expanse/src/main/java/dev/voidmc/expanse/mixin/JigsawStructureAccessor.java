package dev.voidmc.expanse.mixin;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to a jigsaw structure's settings, so a structure that decides its own start point (a hall
 * floor deep underground) can run vanilla's jigsaw assembly from there with the same settings.
 */
@Mixin(JigsawStructure.class)
public interface JigsawStructureAccessor {
	@Accessor("startPool")
	Holder<StructureTemplatePool> expanse$startPool();

	@Accessor("startJigsawName")
	Optional<Identifier> expanse$startJigsawName();

	@Accessor("maxDepth")
	int expanse$maxDepth();

	@Accessor("useExpansionHack")
	boolean expanse$useExpansionHack();

	@Accessor("maxDistanceFromCenter")
	JigsawStructure.MaxDistance expanse$maxDistanceFromCenter();

	@Accessor("poolAliases")
	List<PoolAliasBinding> expanse$poolAliases();

	@Accessor("dimensionPadding")
	DimensionPadding expanse$dimensionPadding();

	@Accessor("liquidSettings")
	LiquidSettings expanse$liquidSettings();
}
