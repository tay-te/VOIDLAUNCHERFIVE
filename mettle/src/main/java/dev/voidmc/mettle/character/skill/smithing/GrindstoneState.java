package dev.voidmc.mettle.character.skill.smithing;

import net.minecraft.world.Container;

/** What Smithing remembers on a grindstone menu (implemented by its mixin): the player and what the result slot is. */
public interface GrindstoneState {
	Grinding.Mode mettle$mode();

	Container mettle$inputs();
}
