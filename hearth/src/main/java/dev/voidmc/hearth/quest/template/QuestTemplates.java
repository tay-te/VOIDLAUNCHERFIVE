package dev.voidmc.hearth.quest.template;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import dev.voidmc.hearth.Hearth;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.StrictJsonParser;
import org.jspecify.annotations.Nullable;

/**
 * The loaded quest templates: every {@code data/<namespace>/hearth_quest/*.json} in the server's data packs, parsed and
 * validated on each reload. A broken file is logged with the field at fault and left out; the rest load. Templates for
 * a mod that is not loaded (Expanse gathering, say) are quietly left out.
 */
public final class QuestTemplates extends SimpleReloadListener<Map<Identifier, QuestTemplate>> {
	public static final Identifier ID = Hearth.id("quest/templates");
	private static final FileToIdConverter LISTER = FileToIdConverter.json("hearth_quest");
	private static volatile Map<Identifier, QuestTemplate> templates = Map.of();

	/** Every enabled template. */
	public static Collection<QuestTemplate> all() {
		return templates.values();
	}

	public static @Nullable QuestTemplate get(Identifier id) {
		return templates.get(id);
	}

	/** For tests and the offline tools: replaces the loaded set. */
	public static void set(Map<Identifier, QuestTemplate> loaded) {
		templates = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
	}

	/** Registry lookups for the validator, against the game's built-in registries. */
	public static final TemplateValidator.Lookup GAME = new TemplateValidator.Lookup() {
		@Override
		public boolean item(Identifier id) {
			return BuiltInRegistries.ITEM.containsKey(id);
		}

		@Override
		public boolean entity(Identifier id) {
			return BuiltInRegistries.ENTITY_TYPE.containsKey(id);
		}

		@Override
		public boolean modLoaded(String modId) {
			return FabricLoader.getInstance().isModLoaded(modId);
		}
	};

	@Override
	protected Map<Identifier, QuestTemplate> prepare(PreparableReloadListener.SharedState state) {
		Map<Identifier, QuestTemplate> loaded = new LinkedHashMap<>();
		int disabled = 0;
		List<String> errors = new ArrayList<>();
		for (Map.Entry<Identifier, Resource> entry : LISTER.listMatchingResources(state.resourceManager()).entrySet()) {
			Identifier id = LISTER.fileToId(entry.getKey());
			try (Reader reader = entry.getValue().openAsReader()) {
				JsonElement json = StrictJsonParser.parse(reader);
				if (!json.isJsonObject()) {
					errors.add(id + ": not a JSON object");
					continue;
				}
				QuestTemplate t = TemplateParser.parse(id, json.getAsJsonObject());
				if (!TemplateValidator.enabled(t, GAME)) {
					disabled++;
					continue;
				}
				List<String> problems = TemplateValidator.validate(t, GAME);
				if (!problems.isEmpty()) {
					errors.add(id + ": " + String.join("; ", problems));
					continue;
				}
				loaded.put(id, t);
			} catch (TemplateParser.TemplateException e) {
				errors.add(id + ": " + e.getMessage());
			} catch (JsonParseException | IOException | IllegalArgumentException e) {
				errors.add(id + ": " + e.getMessage());
			}
		}
		for (QuestTemplate t : loaded.values()) {
			for (Identifier pre : t.when().prerequisites()) {
				if (!loaded.containsKey(pre)) {
					Hearth.LOG.warn("Quest template {} requires {}, which is not loaded; it will never be offered", t.id(), pre);
				}
			}
		}
		for (String e : errors) {
			Hearth.LOG.error("Quest template left out: {}", e);
		}
		Hearth.LOG.info("Loaded {} quest templates ({} waiting for mods not present, {} with errors)", loaded.size(), disabled, errors.size());
		return loaded;
	}

	@Override
	protected void apply(Map<Identifier, QuestTemplate> prepared, PreparableReloadListener.SharedState state) {
		set(prepared);
	}
}
