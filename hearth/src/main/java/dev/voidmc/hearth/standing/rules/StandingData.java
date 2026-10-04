package dev.voidmc.hearth.standing.rules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import dev.voidmc.hearth.Hearth;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.world.item.DyeColor;

/**
 * Loads {@link StandingRules} from every data pack:
 *
 * <pre>
 *   data/&lt;ns&gt;/hearth_standing/deeds/*.json    {"deeds": {"law:theft": {"settlement": 1.0, "victim": 1.5, ...}, ...}}
 *   data/&lt;ns&gt;/hearth_standing/gossip/*.json   {"lines": {"law:theft": ["They say {player} ...", ...], ...}}
 *   data/&lt;ns&gt;/hearth_standing/attire/*.json   {"cultures": {"temperate": ["brown", "green", ...], ...}}
 * </pre>
 *
 * Files are merged in pack order, later keys replacing earlier ones (gossip lines are appended unless the file says
 * {@code "replace": true}). A broken entry is logged and left out; the rest load.
 */
public final class StandingData extends SimpleReloadListener<StandingRules> {
	public static final Identifier ID = Hearth.id("standing/rules");
	private static final FileToIdConverter DEEDS = FileToIdConverter.json("hearth_standing/deeds");
	private static final FileToIdConverter GOSSIP = FileToIdConverter.json("hearth_standing/gossip");
	private static final FileToIdConverter ATTIRE = FileToIdConverter.json("hearth_standing/attire");

	@Override
	protected StandingRules prepare(PreparableReloadListener.SharedState state) {
		Builder b = new Builder();
		List<String> errors = new ArrayList<>();
		read(DEEDS, state, errors, b::deeds);
		read(GOSSIP, state, errors, b::gossip);
		read(ATTIRE, state, errors, b::attire);
		errors.addAll(b.errors);
		for (String e : errors) {
			Hearth.LOG.error("Standing data: {}", e);
		}
		StandingRules rules = b.build();
		Hearth.LOG.info("Loaded standing data: {} deed kinds, {} gossip keys, {} cultures' dress", rules.deeds().size(), rules.lines().size(),
			rules.attire().size());
		return rules;
	}

	@Override
	protected void apply(StandingRules prepared, PreparableReloadListener.SharedState state) {
		StandingRules.set(prepared);
	}

	private static void read(FileToIdConverter lister, PreparableReloadListener.SharedState state, List<String> errors,
		java.util.function.BiConsumer<Identifier, JsonObject> into) {
		for (Map.Entry<Identifier, Resource> e : lister.listMatchingResources(state.resourceManager()).entrySet()) {
			Identifier id = lister.fileToId(e.getKey());
			try (Reader reader = e.getValue().openAsReader()) {
				JsonElement json = StrictJsonParser.parse(reader);
				if (!json.isJsonObject()) {
					errors.add(id + ": not a JSON object");
					continue;
				}
				into.accept(id, json.getAsJsonObject());
			} catch (IOException | JsonParseException | IllegalArgumentException | IllegalStateException ex) {
				errors.add(id + ": " + ex.getMessage());
			}
		}
	}

	/** Accumulates files into rules; public so tests and tools can build rules from JSON without a server. */
	public static final class Builder {
		private final Map<String, DeedRule> deeds = new LinkedHashMap<>();
		private final Map<String, List<String>> lines = new LinkedHashMap<>();
		private final Map<String, Set<DyeColor>> attire = new HashMap<>();
		final List<String> errors = new ArrayList<>();

		public Builder deeds(Identifier file, JsonObject json) {
			JsonObject table = json.has("deeds") && json.get("deeds").isJsonObject() ? json.getAsJsonObject("deeds") : null;
			if (table == null) {
				this.errors.add(file + ": no \"deeds\" object");
				return this;
			}
			for (Map.Entry<String, JsonElement> e : table.entrySet()) {
				DeedRule.CODEC.parse(JsonOps.INSTANCE, e.getValue())
					.resultOrPartial(err -> this.errors.add(file + ": deed " + e.getKey() + ": " + err))
					.ifPresent(r -> this.deeds.put(e.getKey(), r));
			}
			return this;
		}

		public Builder gossip(Identifier file, JsonObject json) {
			JsonObject table = json.has("lines") && json.get("lines").isJsonObject() ? json.getAsJsonObject("lines") : null;
			if (table == null) {
				this.errors.add(file + ": no \"lines\" object");
				return this;
			}
			boolean replace = json.has("replace") && json.get("replace").getAsBoolean();
			for (Map.Entry<String, JsonElement> e : table.entrySet()) {
				if (!e.getValue().isJsonArray()) {
					this.errors.add(file + ": lines for " + e.getKey() + " are not a list");
					continue;
				}
				List<String> l = replace ? new ArrayList<>() : new ArrayList<>(this.lines.getOrDefault(e.getKey(), List.of()));
				for (JsonElement s : (JsonArray) e.getValue()) {
					if (s.isJsonPrimitive() && !s.getAsString().isBlank()) {
						l.add(s.getAsString());
					}
				}
				this.lines.put(e.getKey(), List.copyOf(l));
			}
			return this;
		}

		public Builder attire(Identifier file, JsonObject json) {
			JsonObject table = json.has("cultures") && json.get("cultures").isJsonObject() ? json.getAsJsonObject("cultures") : null;
			if (table == null) {
				this.errors.add(file + ": no \"cultures\" object");
				return this;
			}
			for (Map.Entry<String, JsonElement> e : table.entrySet()) {
				if (!e.getValue().isJsonArray()) {
					this.errors.add(file + ": dress of " + e.getKey() + " is not a list");
					continue;
				}
				Set<DyeColor> colours = EnumSet.noneOf(DyeColor.class);
				for (JsonElement s : (JsonArray) e.getValue()) {
					DyeColor c = DyeColor.byName(s.getAsString(), null);
					if (c == null) {
						this.errors.add(file + ": " + e.getKey() + ": unknown dye colour " + s.getAsString());
					} else {
						colours.add(c);
					}
				}
				this.attire.put(e.getKey(), Set.copyOf(colours));
			}
			return this;
		}

		public List<String> errors() {
			return List.copyOf(this.errors);
		}

		public StandingRules build() {
			return new StandingRules(Map.copyOf(this.deeds), Map.copyOf(this.lines), Map.copyOf(this.attire));
		}
	}
}
