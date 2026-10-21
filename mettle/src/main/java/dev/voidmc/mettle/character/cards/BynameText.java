package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.api.BynameWords;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.core.BuildState.Byname;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** {@link Bynames.Words} from the synced byname tables and Mythic data (either side), with plain fallbacks. */
public final class BynameText implements Bynames.Words {
	private final Map<String, BynameWords.Word> keywords;
	private final Map<String, BynameWords.Word> skills;
	private final Map<String, BynameWords.Word> pairs;
	private final RegistryAccess registries;

	private BynameText(RegistryAccess registries) {
		this.registries = registries;
		var table = registries.lookupOrThrow(MettleRegistries.BYNAME);
		this.keywords = table.getOptional(Identifier.fromNamespaceAndPath("mettle", "keywords")).map(BynameWords::words).orElse(Map.of());
		this.skills = table.getOptional(Identifier.fromNamespaceAndPath("mettle", "skills")).map(BynameWords::words).orElse(Map.of());
		this.pairs = table.getOptional(Identifier.fromNamespaceAndPath("mettle", "pairs")).map(BynameWords::words).orElse(Map.of());
	}

	public static BynameText of(RegistryAccess registries) {
		return new BynameText(registries);
	}

	/** A byname as text. */
	public static Component render(RegistryAccess registries, Byname b) {
		return Bynames.render(b, of(registries));
	}

	private static Component pick(Map<String, BynameWords.Word> table, String key, Function<BynameWords.Word, Optional<Component>> field, String fallback) {
		BynameWords.Word w = table.get(key);
		return w == null ? Component.literal(fallback) : field.apply(w).orElse(Component.literal(fallback));
	}

	private static String title(String path) {
		return path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
	}

	@Override
	public Component prefix(Identifier keyword) {
		return pick(this.keywords, keyword.toString(), BynameWords.Word::prefix, title(keyword.getPath()));
	}

	@Override
	public Component word(Identifier keyword) {
		return pick(this.keywords, keyword.toString(), BynameWords.Word::word, title(keyword.getPath()));
	}

	@Override
	public Component suffix(ResourceKey<Skill> skill) {
		return pick(this.skills, skill.identifier().toString(), BynameWords.Word::suffix, skill.identifier().getPath());
	}

	@Override
	public Component noun(ResourceKey<Skill> skill) {
		return pick(this.skills, skill.identifier().toString(), BynameWords.Word::noun, title(skill.identifier().getPath()));
	}

	@Override
	public Optional<Component> pair(ResourceKey<Skill> a, ResourceKey<Skill> b) {
		BynameWords.Word w = this.pairs.get(Bynames.pairKey(a, b));
		if (w == null) {
			w = this.pairs.get(Bynames.pairKey(b, a));
		}
		return w == null ? Optional.empty() : w.noun();
	}

	@Override
	public Component description(ResourceKey<Skill> a, Optional<ResourceKey<Skill>> b) {
		if (b.isPresent()) {
			BynameWords.Word w = this.pairs.get(Bynames.pairKey(a, b.get()));
			if (w == null) {
				w = this.pairs.get(Bynames.pairKey(b.get(), a));
			}
			if (w != null && w.description().isPresent()) {
				return w.description().get();
			}
		}
		return pick(this.skills, a.identifier().toString(), BynameWords.Word::description, "");
	}

	@Override
	public Component epithet(Identifier mythic) {
		return this.registries.lookupOrThrow(MettleRegistries.MYTHIC).getOptional(mythic).map(m -> m.epithet()).filter(c -> !c.getString().isEmpty())
			.orElse(Component.literal(""));
	}
}
