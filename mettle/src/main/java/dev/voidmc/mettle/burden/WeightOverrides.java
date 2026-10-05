package dev.voidmc.mettle.burden;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.burden.WeightDeriver.Rule;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Datapack weight overrides: {@code data/<ns>/mettle/weight/*.json}, each {@code {"values": {"<item or #tag>": kg}}}.
 * They win over leaves and recipes. Tags apply first and single items after, so a file can set a tag and then carve
 * out one member. Mettle's own {@code equipment.json} is one of these (BALANCE.md §5.3).
 */
public final class WeightOverrides {
	public static final String DIRECTORY = "mettle/weight";

	/** One override line and the file it came from. */
	public record Line(String target, float kg, Identifier file) {
	}

	public static final WeightOverrides EMPTY = new WeightOverrides(List.of());

	private final List<Line> lines;

	public WeightOverrides(List<Line> lines) {
		this.lines = List.copyOf(lines);
	}

	public List<Line> lines() {
		return this.lines;
	}

	public static WeightOverrides load(ResourceManager resources) {
		List<Line> lines = new ArrayList<>();
		for (Map.Entry<Identifier, Resource> e : resources.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				lines.addAll(parse(JsonParser.parseReader(reader), e.getKey()));
			} catch (Exception ex) {
				Mettle.LOGGER.warn("Bad weight file {}: {}", e.getKey(), ex.getMessage());
			}
		}
		return new WeightOverrides(lines);
	}

	public static List<Line> parse(JsonElement json, Identifier file) {
		List<Line> lines = new ArrayList<>();
		JsonObject values = json.getAsJsonObject().getAsJsonObject("values");
		if (values == null) {
			throw new IllegalArgumentException("missing \"values\"");
		}
		for (Map.Entry<String, JsonElement> v : values.entrySet()) {
			float kg = v.getValue().getAsFloat();
			if (!(kg >= 0f) || Float.isInfinite(kg)) {
				throw new IllegalArgumentException(v.getKey() + " has weight " + kg);
			}
			lines.add(new Line(v.getKey(), kg, file));
		}
		return lines;
	}

	/** The overrides as deriver rules; unknown items and empty tags are skipped. */
	public Map<Item, Rule> rules(Function<TagKey<Item>, Collection<Item>> tags) {
		Map<Item, Rule> rules = new LinkedHashMap<>();
		for (boolean tagPass : new boolean[] {true, false}) {
			for (Line line : this.lines) {
				if (line.target().startsWith("#") == tagPass) {
					for (Item item : Leaves.targets(line.target(), tags)) {
						rules.put(item, new Rule.Fixed(line.kg(), "override"));
					}
				}
			}
		}
		return rules;
	}
}
