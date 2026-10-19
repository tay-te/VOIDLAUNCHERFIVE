package dev.voidmc.hearth.testing;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;

/** Boots Minecraft for tests that make item stacks: the registries, and the items' components (bound once per JVM). */
public final class ItemBoot {
	private static boolean done;

	private ItemBoot() {
	}

	public static synchronized void boot() {
		if (done) {
			return;
		}
		done = true;
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		// item components (max stack size...) are bound when a world loads its registries; do the same here
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
			.forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
	}
}
