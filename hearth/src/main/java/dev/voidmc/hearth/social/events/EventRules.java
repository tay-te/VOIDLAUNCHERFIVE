package dev.voidmc.hearth.social.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.Hearth;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

/**
 * The event rules in force, one per {@link EventKind} ({@code data/<ns>/hearth_social/events/*.json}). Before a data pack
 * has loaded (tests, the dev sandbox) the mod's own files are read from the jar.
 */
public final class EventRules {
	public static final String FOLDER = "hearth_social/events";
	private static volatile @Nullable Map<EventKind, EventRule> rules;
	private static volatile Map<String, EventRule> scenes = Map.of();
	/** The staged scenes in the mod's own data, read when no data pack has loaded (tests). */
	public static final String[] BUILTIN_SCENES = {"oath", "dubbing"};

	private EventRules() {
	}

	/** The rule for a kind, or null if no data names it (the kind is then never scheduled). */
	public static @Nullable EventRule of(EventKind kind) {
		return all().get(kind);
	}

	public static Map<EventKind, EventRule> all() {
		Map<EventKind, EventRule> r = rules;
		if (r == null) {
			r = builtin();
			rules = r;
			scenes = builtinScenes();
		}
		return r;
	}

	/** A staged scene by name ({@code oath}), or the rule of the event kind of that name, or null. */
	public static @Nullable EventRule scene(String name) {
		all();
		EventRule r = scenes.get(name);
		if (r != null) {
			return r;
		}
		EventKind k = EventKind.byId(name);
		return k == null ? null : all().get(k);
	}

	public static Map<String, EventRule> scenes() {
		all();
		return scenes;
	}

	public static void install(Loaded loaded) {
		rules = loaded.rules().isEmpty() ? builtin() : Collections.unmodifiableMap(new EnumMap<>(loaded.rules()));
		scenes = loaded.scenes().isEmpty() ? builtinScenes() : Map.copyOf(loaded.scenes());
	}

	public static void clear() {
		rules = null;
		scenes = Map.of();
	}

	/** What a reload read: the kinds' rules and the staged scenes by name. */
	public record Loaded(Map<EventKind, EventRule> rules, Map<String, EventRule> scenes) {
	}

	/** Reads every rule of the data packs (a bad file is logged and skipped). */
	public static Loaded load(ResourceManager manager) {
		Map<EventKind, EventRule> out = new EnumMap<>(EventKind.class);
		Map<String, EventRule> staged = new java.util.HashMap<>();
		for (Map.Entry<Identifier, Resource> e : manager.listResources(FOLDER, id -> id.getPath().endsWith(".json")).entrySet()) {
			try (Reader r = e.getValue().openAsReader()) {
				EventRule rule = parse(r);
				if (rule != null && rule.kind() == EventKind.SCENE) {
					staged.put(rule.scene().isEmpty() ? e.getKey().getPath().replaceAll(".*/|[.]json$", "") : rule.scene(), rule);
				} else if (rule != null) {
					out.put(rule.kind(), rule);
				}
			} catch (IOException | RuntimeException ex) {
				Hearth.LOG.warn("events: could not read {}", e.getKey(), ex);
			}
		}
		return new Loaded(out, staged);
	}

	public static @Nullable EventRule parse(Reader reader) {
		JsonElement json = JsonParser.parseReader(reader);
		return EventRule.CODEC.parse(JsonOps.INSTANCE, json).resultOrPartial(err -> Hearth.LOG.warn("events: {}", err)).orElse(null);
	}

	/** The staged scenes in the mod's own data, read from the jar. */
	public static Map<String, EventRule> builtinScenes() {
		Map<String, EventRule> out = new java.util.HashMap<>();
		for (String name : BUILTIN_SCENES) {
			String path = "/data/hearth/" + FOLDER + "/" + name + ".json";
			try (InputStream in = EventRules.class.getResourceAsStream(path)) {
				if (in != null) {
					EventRule rule = parse(new InputStreamReader(in, StandardCharsets.UTF_8));
					if (rule != null) {
						out.put(name, rule);
					}
				}
			} catch (IOException | RuntimeException ex) {
				Hearth.LOG.warn("events: could not read {}", path, ex);
			}
		}
		return Map.copyOf(out);
	}

	/** The rules in the mod's own data, read from the jar (one file per kind). */
	public static Map<EventKind, EventRule> builtin() {
		Map<EventKind, EventRule> out = new EnumMap<>(EventKind.class);
		for (EventKind k : EventKind.VALUES) {
			if (k == EventKind.SCENE) {
				continue;
			}
			String path = "/data/hearth/" + FOLDER + "/" + k.id() + ".json";
			try (InputStream in = EventRules.class.getResourceAsStream(path)) {
				if (in == null) {
					continue;
				}
				EventRule rule = parse(new InputStreamReader(in, StandardCharsets.UTF_8));
				if (rule != null) {
					out.put(rule.kind(), rule);
				}
			} catch (IOException | RuntimeException ex) {
				Hearth.LOG.warn("events: could not read {}", path, ex);
			}
		}
		return Collections.unmodifiableMap(out);
	}
}
