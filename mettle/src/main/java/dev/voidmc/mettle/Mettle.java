package dev.voidmc.mettle;

import dev.voidmc.mettle.burden.BurdenModule;
import dev.voidmc.mettle.character.CharacterModule;
import dev.voidmc.mettle.core.CoreModule;
import dev.voidmc.mettle.gear.GearModule;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** VOID Mettle: backgrounds, attributes, skills, perks and carry weight. See {@code mettle/docs/ARCHITECTURE.md}. */
public final class Mettle implements ModInitializer {
	public static final String ID = "mettle";
	public static final Logger LOGGER = LoggerFactory.getLogger("VOID Mettle");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(ID, path);
	}

	@Override
	public void onInitialize() {
		CoreModule.init();
		CharacterModule.init();
		BurdenModule.init();
		GearModule.init();
	}
}
