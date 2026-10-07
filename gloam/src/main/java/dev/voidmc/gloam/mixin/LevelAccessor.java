package dev.voidmc.gloam.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla's own rain and thunder levels, read past anything that makes {@code getRainLevel} regional. */
@Mixin(Level.class)
public interface LevelAccessor {
	@Accessor("rainLevel")
	float gloam$rainLevel();

	@Accessor("oRainLevel")
	float gloam$oRainLevel();

	@Accessor("thunderLevel")
	float gloam$thunderLevel();

	@Accessor("oThunderLevel")
	float gloam$oThunderLevel();
}
