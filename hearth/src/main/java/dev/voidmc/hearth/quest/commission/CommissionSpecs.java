package dev.voidmc.hearth.quest.commission;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.Hearth;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import org.jspecify.annotations.Nullable;

/**
 * What makes a quest template a lord's commission (docs/LIVING_WORLD_2.md §8.2): the {@code "commission"} block of a
 * {@code hearth_quest/commission/*.json} template — which piece of the domain's real state binds it ({@link Binder.Kind}),
 * the least rank it is given to, and whether doing it is the feat a knighthood asks. The quest module loads the same files
 * as templates (weight 0, so no board ever posts them); this reads only the block.
 */
public final class CommissionSpecs extends SimpleReloadListener<Map<Identifier, CommissionSpecs.Spec>> {
	public static final Identifier ID = Hearth.id("quest/commissions");
	static final String FOLDER = "hearth_quest/commission";
	private static final FileToIdConverter LISTER = FileToIdConverter.json(FOLDER);
	private static volatile Map<Identifier, Spec> specs = Map.of();

	/**
	 * @param template the quest template's id ({@code hearth:commission/clear_the_band})
	 * @param least    the least rank given it
	 * @param feat     doing it counts as a tournament won or a raid repelled
	 */
	public record Spec(Identifier template, Binder.Kind kind, Rank least, boolean feat) {
	}

	public static Collection<Spec> all() {
		return specs.values();
	}

	public static @Nullable Spec get(Identifier template) {
		return specs.get(template);
	}

	/** For tests: replaces the loaded set. */
	public static void set(Map<Identifier, Spec> loaded) {
		specs = Map.copyOf(loaded);
	}

	/** The block of one template file, or null if it has none (or a kind nobody binds). */
	public static @Nullable Spec parse(Identifier template, JsonObject json) {
		if (!json.has("commission") || !json.get("commission").isJsonObject()) {
			return null;
		}
		JsonObject c = json.getAsJsonObject("commission");
		Binder.Kind kind = Binder.Kind.byId(c.has("binds") ? c.get("binds").getAsString() : "");
		if (kind == null) {
			return null;
		}
		Rank least = Rank.byId(c.has("rank") ? c.get("rank").getAsString() : "petitioner");
		boolean feat = c.has("feat") && c.get("feat").getAsBoolean();
		return new Spec(template, kind, least, feat);
	}

	@Override
	protected Map<Identifier, Spec> prepare(PreparableReloadListener.SharedState state) {
		Map<Identifier, Spec> out = new LinkedHashMap<>();
		for (Map.Entry<Identifier, Resource> e : LISTER.listMatchingResources(state.resourceManager()).entrySet()) {
			Identifier file = LISTER.fileToId(e.getKey());
			Identifier template = Identifier.fromNamespaceAndPath(file.getNamespace(), "commission/" + file.getPath());
			try (Reader r = e.getValue().openAsReader()) {
				JsonElement json = JsonParser.parseReader(r);
				Spec s = json.isJsonObject() ? parse(template, json.getAsJsonObject()) : null;
				if (s != null) {
					out.put(template, s);
				} else {
					Hearth.LOG.warn("Commission {} has no usable \"commission\" block", template);
				}
			} catch (IOException | RuntimeException ex) {
				Hearth.LOG.warn("could not read commission {}", e.getKey(), ex);
			}
		}
		Hearth.LOG.info("Loaded {} lord's commissions", out.size());
		return out;
	}

	@Override
	protected void apply(Map<Identifier, Spec> prepared, PreparableReloadListener.SharedState state) {
		set(prepared);
	}
}
