package dev.voidmc.mettle.character.draft;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;

/** The shipped perks as a draft deck, and a character sheet made of plain maps, for the draft tests. */
final class TestDeck {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	static final Path PERKS = Path.of("src/main/resources/data/mettle/mettle/perk");
	static final String[] RELEASED = {"mining", "blades", "heavy_weapons", "marksmanship", "defense", "athletics", "survival", "smithing", "stealth"};

	private TestDeck() {
	}

	/** Every shipped perk, keyed {@code mettle:<skill>/<name>}. */
	static Map<ResourceKey<Perk>, Perk> shipped() {
		Map<ResourceKey<Perk>, Perk> out = new HashMap<>();
		try (Stream<Path> files = Files.walk(PERKS)) {
			for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
				String rel = PERKS.relativize(p).toString().replace('\\', '/').replace(".json", "");
				Perk perk = Perk.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(p))).getOrThrow();
				out.put(perk(rel), perk);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out;
	}

	static DraftRules.Deck deck() {
		return new DraftRules.Deck(shipped());
	}

	static ResourceKey<Perk> perk(String path) {
		return ResourceKey.create(MettleRegistries.PERK, Identifier.fromNamespaceAndPath("mettle", path));
	}

	static ResourceKey<Skill> skill(String path) {
		return ResourceKey.create(MettleRegistries.SKILL, Identifier.fromNamespaceAndPath("mettle", path));
	}

	/** A character as plain maps: skill levels, one attribute value for every skill, held ranks. */
	static final class Sheet implements DraftRules.Sheet {
		final Map<ResourceKey<Skill>, Integer> levels = new HashMap<>();
		final Map<ResourceKey<Perk>, Integer> ranks = new HashMap<>();
		int attribute = 20;

		Sheet level(String skill, int level) {
			this.levels.put(skill(skill), level);
			return this;
		}

		Sheet hold(String perk, int rank) {
			this.ranks.put(perk(perk), rank);
			return this;
		}

		@Override
		public int level(ResourceKey<Skill> skill) {
			return this.levels.getOrDefault(skill, 0);
		}

		@Override
		public int attribute(ResourceKey<Skill> skill) {
			return this.attribute;
		}

		@Override
		public int rank(ResourceKey<Perk> perk) {
			return this.ranks.getOrDefault(perk, 0);
		}
	}
}
