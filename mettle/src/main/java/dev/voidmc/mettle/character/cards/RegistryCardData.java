package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.api.CardSkill;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.api.Trait;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** {@link CardRules.Data} read from the loaded registries (either side). Cached per registry access. */
public final class RegistryCardData implements CardRules.Data {
	private static WeakReference<RegistryAccess> lastAccess = new WeakReference<>(null);
	private static RegistryCardData last;

	private final Map<ResourceKey<Skill>, List<CardRules.TraitEntry>> traits = new HashMap<>();
	private final Map<Identifier, Trait> byId = new HashMap<>();
	private final Map<ResourceKey<Skill>, CardSkill> skills = new HashMap<>();
	private final Map<ResourceKey<Perk>, Identifier> natives = new HashMap<>();

	private RegistryCardData(RegistryAccess registries) {
		registries.lookupOrThrow(MettleRegistries.TRAIT).listElements()
			.sorted(Comparator.comparing(r -> r.key().identifier().toString()))
			.forEach(r -> {
				this.traits.computeIfAbsent(r.value().skill(), s -> new ArrayList<>()).add(new CardRules.TraitEntry(r.key().identifier(), r.value()));
				this.byId.put(r.key().identifier(), r.value());
			});
		registries.lookupOrThrow(MettleRegistries.CARD_SKILL).listElements().forEach(r -> {
			this.skills.put(ResourceKey.create(MettleRegistries.SKILL, r.key().identifier()), r.value());
			this.natives.putAll(r.value().natives());
		});
	}

	public static synchronized RegistryCardData of(RegistryAccess registries) {
		if (lastAccess.get() != registries || last == null) {
			last = new RegistryCardData(registries);
			lastAccess = new WeakReference<>(registries);
		}
		return last;
	}

	@Override
	public List<CardRules.TraitEntry> traits(ResourceKey<Skill> skill) {
		return this.traits.getOrDefault(skill, List.of());
	}

	@Override
	public Optional<Trait> trait(Identifier id) {
		return Optional.ofNullable(this.byId.get(id));
	}

	@Override
	public Optional<Identifier> nativeKeyword(ResourceKey<Perk> perk) {
		return Optional.ofNullable(this.natives.get(perk));
	}

	@Override
	public List<Identifier> pool(ResourceKey<Skill> skill) {
		CardSkill s = this.skills.get(skill);
		return s == null ? List.of() : s.pool();
	}

	@Override
	public String prefix(ResourceKey<Skill> skill) {
		CardSkill s = this.skills.get(skill);
		return s == null ? skill.identifier().getPath().substring(0, Math.min(2, skill.identifier().getPath().length())).toUpperCase(java.util.Locale.ROOT) : s.prefix();
	}
}
