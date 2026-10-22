package dev.voidmc.fray.client.mixin;

import java.util.List;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** After the elbow cut the model's part list is rebuilt, so {@code resetPose} straightens the forearms too. */
@Mixin(Model.class)
public interface ModelAccess {
	@Accessor("allParts")
	@Mutable
	void fray$setAllParts(List<ModelPart> parts);
}
