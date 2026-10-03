package dev.voidmc.hearth.economy.price;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything under {@code data/<namespace>/hearth_prices/}, parsed: what items are worth, how goods are measured, and
 * what each culture's markets deal in. Immutable once built; swapped whole on {@code /reload}.
 *
 * <pre>
 *   hearth_prices/goods.json            unit values, defaults per goods and rarity, the default wares
 *   hearth_prices/cultures/&lt;c&gt;.json    a culture's specialities and wares
 *   hearth_prices/**.json (any other)   base values: {"values": {"minecraft:bread": 1.0, "#minecraft:logs": 0.5}}
 * </pre>
 *
 * Item ids that do not exist (Expanse absent) are simply never matched; a ware written {@code "a|b"} uses the first of
 * its alternatives that exists.
 */
public record PriceData(Map<Identifier, Double> items, List<TagValue> tags, double[] unitValues, double[] goodsDefaults,
	double untaggedDefault, Map<Rarity, Double> rarity, Map<Goods, List<String>> defaultWares, Map<Culture, CultureEntry> cultures) {

	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	public static final String DIRECTORY = "hearth_prices";

	public static final PriceData EMPTY = new PriceData(Map.of(), List.of(), filled(1.0), filled(1.0), 0.25, Map.of(), Map.of(), Map.of());

	/** A tag's value, in load order (a later entry for the same tag replaces the earlier). */
	public record TagValue(Identifier tag, double value) {
	}

	/** A culture's market: price factor per goods (below 1 = plentiful, cheap here) and the items it sells per goods. */
	public record CultureEntry(double[] speciality, Map<Goods, List<String>> wares) {
		public double speciality(Goods goods) {
			return this.speciality[goods.ordinal()];
		}
	}

	public double unitValue(Goods goods) {
		return this.unitValues[goods.ordinal()];
	}

	public double goodsDefault(Goods goods) {
		return this.goodsDefaults[goods.ordinal()];
	}

	public double rarity(Rarity r) {
		return this.rarity.getOrDefault(r, switch (r) {
			case COMMON -> 1.0;
			case UNCOMMON -> 4.0;
			case RARE -> 12.0;
			case EPIC -> 40.0;
		});
	}

	public static PriceData load(ResourceManager manager) {
		Map<Identifier, Double> items = new HashMap<>();
		Map<Identifier, Double> tags = new LinkedHashMap<>();
		double[] units = filled(1.0);
		double[] defaults = filled(1.0);
		double untagged = 0.25;
		Map<Rarity, Double> rarity = new EnumMap<>(Rarity.class);
		Map<Goods, List<String>> defaultWares = new EnumMap<>(Goods.class);
		Map<Culture, CultureEntry> cultures = new EnumMap<>(Culture.class);

		// Sorted so the result does not depend on hash order: namespaces and files alphabetically, later wins.
		Map<Identifier, Resource> files = new java.util.TreeMap<>(manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")));
		for (Map.Entry<Identifier, Resource> e : files.entrySet()) {
			String rel = e.getKey().getPath().substring(DIRECTORY.length() + 1, e.getKey().getPath().length() - ".json".length());
			JsonObject json;
			try (Reader reader = e.getValue().openAsReader()) {
				json = JsonParser.parseReader(reader).getAsJsonObject();
			} catch (Exception ex) {
				LOG.warn("Hearth prices: could not read {}: {}", e.getKey(), ex.toString());
				continue;
			}
			try {
				if (rel.equals("goods")) {
					readGoods(json.getAsJsonObject("units"), units);
					readGoods(json.getAsJsonObject("defaults"), defaults);
					if (json.has("untagged")) {
						untagged = json.get("untagged").getAsDouble();
					}
					if (json.has("rarity")) {
						for (Map.Entry<String, JsonElement> r : json.getAsJsonObject("rarity").entrySet()) {
							for (Rarity value : Rarity.values()) {
								if (value.getSerializedName().equals(r.getKey())) {
									rarity.put(value, r.getValue().getAsDouble());
								}
							}
						}
					}
					readWares(json.getAsJsonObject("wares"), defaultWares);
				} else if (rel.startsWith("cultures/")) {
					Culture culture = Culture.CODEC.byName(rel.substring("cultures/".length()));
					if (culture == null) {
						LOG.warn("Hearth prices: {} names no culture", e.getKey());
						continue;
					}
					double[] speciality = filled(1.0);
					readGoods(json.getAsJsonObject("speciality"), speciality);
					Map<Goods, List<String>> wares = new EnumMap<>(Goods.class);
					readWares(json.getAsJsonObject("wares"), wares);
					cultures.put(culture, new CultureEntry(speciality, wares));
				} else {
					JsonObject values = json.getAsJsonObject("values");
					if (values == null) {
						continue;
					}
					for (Map.Entry<String, JsonElement> v : values.entrySet()) {
						String key = v.getKey();
						double value = v.getValue().getAsDouble();
						if (key.startsWith("#")) {
							Identifier tag = Identifier.tryParse(key.substring(1));
							if (tag != null) {
								tags.remove(tag);
								tags.put(tag, value);
							}
						} else {
							Identifier id = Identifier.tryParse(key);
							if (id != null) {
								items.put(id, value);
							}
						}
					}
				}
			} catch (RuntimeException ex) {
				LOG.warn("Hearth prices: bad entry in {}: {}", e.getKey(), ex.toString());
			}
		}
		List<TagValue> tagList = new ArrayList<>();
		tags.forEach((k, v) -> tagList.add(new TagValue(k, v)));
		return new PriceData(Map.copyOf(items), List.copyOf(tagList), units, defaults, untagged, Map.copyOf(rarity), Map.copyOf(defaultWares),
			Map.copyOf(cultures));
	}

	private static void readGoods(JsonObject json, double[] into) {
		if (json == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> g : json.entrySet()) {
			Goods goods = Goods.CODEC.byName(g.getKey());
			if (goods != null) {
				into[goods.ordinal()] = g.getValue().getAsDouble();
			}
		}
	}

	private static void readWares(JsonObject json, Map<Goods, List<String>> into) {
		if (json == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> g : json.entrySet()) {
			Goods goods = Goods.CODEC.byName(g.getKey());
			if (goods != null) {
				List<String> list = new ArrayList<>();
				g.getValue().getAsJsonArray().forEach(el -> list.add(el.getAsString()));
				into.put(goods, List.copyOf(list));
			}
		}
	}

	private static double[] filled(double v) {
		double[] a = new double[Goods.VALUES.length];
		Arrays.fill(a, v);
		return a;
	}
}
