package dev.voidmc.bestiary.ai.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.bestiary.Bestiary;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * Creature profiles from datapacks ({@code data/<ns>/bestiary/creature/*.json}), reloaded with {@code /reload}. A
 * datapack may override a shipped profile by id or add a profile for any entity type (another mod's mob only gets the
 * Bestiary AI if someone writes a profile for it: mobs from other mods are untouched by default).
 */
public final class CreatureProfiles {
	private static volatile Map<Identifier, CreatureProfile> byId = Map.of();
	private static volatile Map<Identifier, CreatureProfile> byEntity = Map.of();

	private CreatureProfiles() {
	}

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Bestiary.id("creatures"), (ResourceManagerReloadListener) manager -> {
			Map<Identifier, Resource> files = manager.listResources("bestiary/creature", id -> id.getPath().endsWith(".json"));
			Map<Identifier, CreatureProfile> ids = new LinkedHashMap<>();
			Map<Identifier, CreatureProfile> types = new LinkedHashMap<>();
			files.keySet().stream().sorted().forEach(file -> {
				String path = file.getPath().substring("bestiary/creature/".length(), file.getPath().length() - ".json".length());
				Identifier id = Identifier.fromNamespaceAndPath(file.getNamespace(), path);
				try (Reader r = files.get(file).openAsReader()) {
					JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
					CreatureProfile p = CreatureProfile.parse(id, o);
					ids.put(id, p);
					types.put(p.entity(), p);
				} catch (Exception ex) {
					Bestiary.LOGGER.warn("bad creature profile {}: {}", file, ex.toString());
				}
			});
			byId = Map.copyOf(ids);
			byEntity = Map.copyOf(types);
			Bestiary.LOGGER.info("Bestiary: {} creature profiles ({})", ids.size(), ids.keySet());
		});
	}

	public static @Nullable CreatureProfile get(Identifier id) {
		return byId.get(id);
	}

	public static @Nullable CreatureProfile forType(EntityType<?> type) {
		return byEntity.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
	}

	public static Map<Identifier, CreatureProfile> all() {
		return byId;
	}

	/** Tests: install profiles without a resource reload. */
	public static void set(Map<Identifier, CreatureProfile> profiles) {
		Map<Identifier, CreatureProfile> types = new LinkedHashMap<>();
		profiles.values().forEach(p -> types.put(p.entity(), p));
		byId = Map.copyOf(profiles);
		byEntity = Map.copyOf(types);
	}
}
