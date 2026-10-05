package dev.voidmc.mettle.burden;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.mettle.burden.WeightDeriver.Edge;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Vanilla's recipes and item tags read straight from the game jar on the test classpath, turned into deriver edges the
 * way {@link RecipeEdges} does from a live server's recipe manager. No datapack loading needed.
 */
final class VanillaData {
	private static final Map<Identifier, Collection<Item>> TAGS = new HashMap<>();

	private VanillaData() {
	}

	static Collection<Item> tag(TagKey<Item> key) {
		return TAGS.computeIfAbsent(key.location(), id -> resolve(id, new LinkedHashSet<>(), new java.util.HashSet<>()));
	}

	private static Collection<Item> resolve(Identifier id, Set<Item> into, Set<Identifier> seen) {
		if (!seen.add(id)) {
			return into;
		}
		String path = "data/" + id.getNamespace() + "/tags/item/" + id.getPath() + ".json";
		try {
			for (URL url : Collections.list(VanillaData.class.getClassLoader().getResources(path))) {
				try (Reader r = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
					for (JsonElement v : JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("values")) {
						String entry = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
						if (entry.startsWith("#")) {
							resolve(Identifier.parse(entry.substring(1)), into, seen);
						} else {
							BuiltInRegistries.ITEM.getOptional(Identifier.parse(entry)).ifPresent(into::add);
						}
					}
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return into;
	}

	static List<Edge> recipes() throws Exception {
		URL probe = VanillaData.class.getClassLoader().getResource("data/minecraft/recipe/iron_block.json");
		if (probe == null) {
			throw new IllegalStateException("vanilla recipes are not on the test classpath");
		}
		URI uri = probe.toURI();
		Path dir;
		if (uri.getScheme().equals("jar")) {
			FileSystem fs;
			try {
				fs = FileSystems.newFileSystem(uri, Map.of());
			} catch (FileSystemAlreadyExistsException e) {
				fs = FileSystems.getFileSystem(uri);
			}
			dir = fs.getPath("data/minecraft/recipe");
		} else {
			dir = Path.of(uri).getParent();
		}
		List<Edge> edges = new ArrayList<>();
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				String name = file.getFileName().toString().replace(".json", "");
				JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				Edge e = edge(Identifier.withDefaultNamespace(name), json);
				if (e != null) {
					edges.add(e);
				}
			}
		}
		edges.addAll(RecipeEdges.equivalences());
		return edges;
	}

	private static Edge edge(Identifier id, JsonObject json) {
		String type = json.get("type").getAsString().replace("minecraft:", "");
		List<List<Item>> slots = new ArrayList<>();
		boolean cooking = false;
		switch (type) {
			case "crafting_shaped" -> {
				JsonObject key = json.getAsJsonObject("key");
				for (JsonElement row : json.getAsJsonArray("pattern")) {
					for (char ch : row.getAsString().toCharArray()) {
						if (ch != ' ') {
							slots.add(ingredient(key.get(String.valueOf(ch))));
						}
					}
				}
			}
			case "crafting_shapeless" -> json.getAsJsonArray("ingredients").forEach(i -> slots.add(ingredient(i)));
			case "crafting_transmute" -> {
				slots.add(ingredient(json.get("input")));
				slots.add(ingredient(json.get("material")));
			}
			case "crafting_imbue" -> {
				for (int i = 0; i < 8; i++) {
					slots.add(ingredient(json.get("material")));
				}
				slots.add(ingredient(json.get("source")));
			}
			case "stonecutting" -> slots.add(ingredient(json.get("ingredient")));
			case "smithing_transform" -> {
				for (String part : new String[] {"template", "base", "addition"}) {
					if (json.has(part)) {
						slots.add(ingredient(json.get(part)));
					}
				}
			}
			case "smelting", "blasting", "smoking", "campfire_cooking" -> {
				slots.add(ingredient(json.get("ingredient")));
				cooking = true;
			}
			default -> {
				return null;
			}
		}
		JsonElement result = json.get("result");
		if (result.isJsonObject() && !result.getAsJsonObject().has("id")) {
			// The result is the input itself (map cloning): nothing to derive.
			return null;
		}
		String out = result.isJsonObject() ? result.getAsJsonObject().get("id").getAsString() : result.getAsString();
		int count = result.isJsonObject() && result.getAsJsonObject().has("count") ? result.getAsJsonObject().get("count").getAsInt() : 1;
		Item output = BuiltInRegistries.ITEM.getOptional(Identifier.parse(out)).orElse(null);
		if (output == null || slots.stream().anyMatch(List::isEmpty)) {
			return null;
		}
		return new Edge(id, output, count, slots, cooking);
	}

	private static List<Item> ingredient(JsonElement json) {
		List<Item> items = new ArrayList<>();
		if (json.isJsonArray()) {
			for (JsonElement e : (JsonArray) json) {
				items.addAll(ingredient(e));
			}
			return items;
		}
		String s = json.getAsString();
		if (s.startsWith("#")) {
			return List.copyOf(tag(TagKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.parse(s.substring(1)))));
		}
		BuiltInRegistries.ITEM.getOptional(Identifier.parse(s)).ifPresent(items::add);
		return items;
	}
}
