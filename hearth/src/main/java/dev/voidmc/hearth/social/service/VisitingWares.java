package dev.voidmc.hearth.social.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The visiting trader of market day: a merchant from a town of another culture (the nearest the atlas knows of, or
 * from far off) whose wares the stallholders sell for the day — limited stock, priced above the town's own market.
 * The wares of each culture are data: {@code data/<ns>/hearth_social/wares/<culture>.json}, {@code {"wares":
 * [{"item": "minecraft:cocoa_beans", "count": 8}, ...]}} (items of mods that are not installed are skipped; later
 * packs replace a culture's list).
 */
public final class VisitingWares {
	static final String DIRECTORY = "hearth_social/wares";
	private static volatile Map<Culture, List<Ware>> wares = new EnumMap<>(Culture.class);

	private VisitingWares() {
	}

	public record Ware(Item item, int count) {
	}

	/** Where today's trader comes from and what they brought. */
	public record Visit(String from, Culture culture, @Nullable Long fromSettlement, List<Ware> wares) {
	}

	public static Map<Culture, List<Ware>> load(ResourceManager manager) {
		Map<Culture, List<Ware>> out = new EnumMap<>(Culture.class);
		Map<Identifier, Resource> files = new TreeMap<>(manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")));
		for (Map.Entry<Identifier, Resource> e : files.entrySet()) {
			String path = e.getKey().getPath();
			String name = path.substring(DIRECTORY.length() + 1, path.length() - ".json".length());
			Culture culture = null;
			for (Culture c : Culture.values()) {
				if (c.getSerializedName().equals(name)) {
					culture = c;
				}
			}
			if (culture == null) {
				Hearth.LOG.warn("social: {} names no culture", e.getKey());
				continue;
			}
			try (Reader reader = e.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				JsonArray list = json.getAsJsonArray("wares");
				List<Ware> ws = new ArrayList<>();
				for (JsonElement el : list) {
					JsonObject o = el.getAsJsonObject();
					Identifier id = Identifier.parse(o.get("item").getAsString());
					int count = o.has("count") ? o.get("count").getAsInt() : 1;
					BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> ws.add(new Ware(item, Math.max(1, count))));
				}
				out.put(culture, List.copyOf(ws));
			} catch (Exception ex) {
				Hearth.LOG.warn("social: could not read {}", e.getKey(), ex);
			}
		}
		return out;
	}

	public static void set(Map<Culture, List<Ware>> loaded) {
		wares = loaded;
	}

	/** Today's visit, or null if no wares are known. Deterministic per settlement and day. */
	public static @Nullable Visit visit(HearthWorld world, Settlement s, long day) {
		Culture from = null;
		String name = null;
		Long fromId = null;
		HearthWorld.SiteInfo best = null;
		for (HearthWorld.SiteInfo site : world.knownSites(s.center(), 2500)) {
			if (site.settlementId() != s.id() && site.culture() != s.culture() && wares.containsKey(site.culture())) {
				best = site;
				break;
			}
		}
		long h = TownCalendar.mix(s.id() * 31 + day);
		if (best != null) {
			from = best.culture();
			name = best.name();
			fromId = best.live() ? best.settlementId() : null;
		} else {
			List<Culture> known = new ArrayList<>(wares.keySet());
			known.remove(s.culture());
			if (known.isEmpty()) {
				return null;
			}
			from = known.get((int) Math.floorMod(h, (long) known.size()));
		}
		List<Ware> list = wares.get(from);
		if (list == null || list.isEmpty()) {
			return null;
		}
		List<Ware> pick = new ArrayList<>();
		List<Ware> pool = new ArrayList<>(list);
		for (int i = 0; i < 3 && !pool.isEmpty(); i++) {
			pick.add(pool.remove((int) Math.floorMod(TownCalendar.mix(h + i), (long) pool.size())));
		}
		return new Visit(name == null ? "" : name, from, fromId, pick);
	}

	/** What a lot of a ware costs here today: the market's price with the visiting markup, at least a bit. */
	public static long price(Settlement s, Ware w, SocialConfig.Market cfg) {
		EconomyService e = dev.voidmc.hearth.social.Hooks.economy();
		if (e == null) {
			return -1;
		}
		long base = e.price(s, new ItemStack(w.item(), w.count()), true);
		return Math.max(1, Math.round(base * cfg.visitingMarkup()));
	}

	/** Lots of a ware still for sale today. */
	public static int stock(SocialLedger ledger, long day, Ware w, SocialConfig.Market cfg) {
		if (ledger.waresDay != day) {
			ledger.waresDay = day;
			ledger.waresSold.clear();
		}
		return Math.max(0, cfg.wareStock() - ledger.waresSold.getOrDefault(key(w), 0));
	}

	public static void sold(SocialLedger ledger, Ware w) {
		ledger.waresSold.merge(key(w), 1, Integer::sum);
	}

	static String key(Ware w) {
		return BuiltInRegistries.ITEM.getKey(w.item()).toString();
	}

	public static boolean enabled() {
		return SocialModule.config().market().visitingWares() && !wares.isEmpty();
	}
}
