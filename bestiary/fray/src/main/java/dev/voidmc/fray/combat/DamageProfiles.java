package dev.voidmc.fray.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.voidmc.fray.Fray;
import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.FrayAttributes;
import java.io.Reader;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jspecify.annotations.Nullable;

/**
 * Foreign damage (lava, falls, vanilla skeletons, other mods) keeps vanilla's path; only the resistance step runs for
 * it, by the damage type's profile ({@code data/<ns>/fray/damage_profile/*.json}): {@code minecraft:lava → fire 1.0}.
 * A type without a profile counts as blunt only if tagged {@code #fray:physical}, else it passes untouched.
 * {@code "weapon": true} splits by the attacker's held weapon (vanilla melee).
 */
public final class DamageProfiles {
	public static final TagKey<DamageType> PHYSICAL = TagKey.create(Registries.DAMAGE_TYPE, Fray.id("physical"));

	public record Profile(Map<DamageKind, Float> split, boolean ignoreResist, boolean weapon) {
	}

	private static Map<Identifier, Profile> profiles = Map.of();

	private DamageProfiles() {
	}

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Fray.id("damage_profiles"), (ResourceManagerReloadListener) manager -> {
			Map<Identifier, Profile> out = new HashMap<>();
			for (Map.Entry<Identifier, Resource> e : manager.listResources("fray/damage_profile", id -> id.getPath().endsWith(".json")).entrySet()) {
				try (Reader r = e.getValue().openAsReader()) {
					JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
					for (Map.Entry<String, JsonElement> p : root.getAsJsonObject("profiles").entrySet()) {
						JsonObject o = p.getValue().getAsJsonObject();
						Map<DamageKind, Float> split = new EnumMap<>(DamageKind.class);
						if (o.has("split")) {
							for (Map.Entry<String, JsonElement> k : o.getAsJsonObject("split").entrySet()) {
								split.put(DamageKind.byId(k.getKey()), k.getValue().getAsFloat());
							}
						}
						out.put(Identifier.parse(p.getKey()), new Profile(split, o.has("ignore_resist") && o.get("ignore_resist").getAsBoolean(),
							o.has("weapon") && o.get("weapon").getAsBoolean()));
					}
				} catch (Exception ex) {
					Fray.LOGGER.warn("bad damage profile file {}: {}", e.getKey(), ex.toString());
				}
			}
			profiles = Map.copyOf(out);
			Fray.LOGGER.info("Fray: {} damage profiles", out.size());
		});
	}

	public static @Nullable Profile of(DamageSource source) {
		return source.typeHolder().unwrapKey().map(k -> profiles.get(k.identifier())).orElse(null);
	}

	public static int count() {
		return profiles.size();
	}

	/** Stage 5's resistance step for a foreign hit: the profile's split through the target's resistances. */
	public static float apply(LivingEntity target, DamageSource source, float damage) {
		if (damage <= 0 || source instanceof FrayDamageSource) {
			return damage;
		}
		Profile p = of(source);
		Map<DamageKind, Float> split;
		if (p == null) {
			if (!source.is(PHYSICAL)) {
				return damage;
			}
			split = Map.of(DamageKind.BLUNT, 1F);
		} else if (p.ignoreResist()) {
			return damage;
		} else if (p.weapon() && source.getEntity() instanceof LivingEntity attacker) {
			split = Weapons.split(attacker.getMainHandItem()).shares();
		} else {
			split = p.split();
		}
		float sum = 0;
		for (float v : split.values()) {
			sum += v;
		}
		if (sum <= 0) {
			return damage;
		}
		float out = 0;
		for (Map.Entry<DamageKind, Float> e : split.entrySet()) {
			out += damage * e.getValue() / sum * (1 - Pipeline.resistance(target, e.getKey(), true));
		}
		return out;
	}

	/** The main kind of a foreign hit (for guard rules): the profile's largest share, blunt if none. */
	public static DamageKind mainKind(DamageSource source) {
		Profile p = of(source);
		DamageKind best = DamageKind.BLUNT;
		float v = -1;
		if (p != null) {
			for (Map.Entry<DamageKind, Float> e : p.split().entrySet()) {
				if (e.getValue() > v) {
					v = e.getValue();
					best = e.getKey();
				}
			}
		}
		return best;
	}

	static Holder<Attribute> resistAttr(DamageKind k) {
		return FrayAttributes.RESIST.get(k);
	}
}
