package dev.voidmc.mettle.mixin.burden;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The sprint gate LocalPlayer asks ({@code hasEnoughFoodToDoExhaustiveManoeuvres}), so the server asks the very same
 * question before it stops a sprint: every hook on it counts (burden's, then Athletics' Hauler's Stride).
 */
@Mixin(Player.class)
public interface PlayerSprintGate {
	@Invoker("hasEnoughFoodToDoExhaustiveManoeuvres")
	boolean mettle$mayExert();
}
