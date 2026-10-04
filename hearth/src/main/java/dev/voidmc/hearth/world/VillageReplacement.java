package dev.voidmc.hearth.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.HearthConfig;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * Hearth's living settlements take the place of the world's static villages (decided with the user, 2026-10-04; see
 * docs/ARCHITECTURE.md §11). When on, every structure set made only of {@code #minecraft:village} structures — vanilla's,
 * Expanse's seven regional villages, any mod's that joins the tag — is left out of the world's possible structures, so
 * none generates and {@code /locate} finds none; Hearth's settlements fill the land instead. Expanse played without
 * Hearth keeps its villages. Off ({@code config/hearth/villages.json}: {@code "replace_villages": false}) keeps them.
 *
 * <p>Read once when a world's structures are set up, so changing it affects worlds created or loaded afterwards; chunks
 * already generated keep whatever villages they have.
 */
public final class VillageReplacement {
	private static volatile Settings settings = Settings.DEFAULTS;

	private VillageReplacement() {
	}

	public static void init() {
		settings = HearthConfig.load("villages", Settings.CODEC, Settings.DEFAULTS);
	}

	public static boolean active() {
		return settings.replaceVillages();
	}

	/** Whether a structure set holds nothing but villages (and is therefore Hearth's to replace). */
	public static boolean isVillageSet(StructureSet set) {
		return !set.structures().isEmpty() && set.structures().stream().allMatch(e -> e.structure().is(StructureTags.VILLAGE));
	}

	record Settings(boolean replaceVillages) {
		static final Settings DEFAULTS = new Settings(true);
		static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("replace_villages", DEFAULTS.replaceVillages).forGetter(Settings::replaceVillages)
		).apply(i, Settings::new));
	}
}
