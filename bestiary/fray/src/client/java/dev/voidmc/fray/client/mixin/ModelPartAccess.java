package dev.voidmc.fray.client.mixin;

import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The elbow cut (Elbows) replaces an arm's boxes and adds its forearm child. */
@Mixin(ModelPart.class)
public interface ModelPartAccess {
	@Accessor("cubes")
	List<ModelPart.Cube> fray$cubes();

	@Accessor("cubes")
	@Mutable
	void fray$setCubes(List<ModelPart.Cube> cubes);

	@Accessor("children")
	Map<String, ModelPart> fray$children();
}
