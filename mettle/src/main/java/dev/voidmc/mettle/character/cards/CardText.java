package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.api.Keyword;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Trait;
import dev.voidmc.mettle.core.CardData.TraitRoll;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Plain words for cards: names with rank, trait lines with values, keyword lists. Both sides. */
public final class CardText {
	private CardText() {
	}

	public static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			default -> String.valueOf(n);
		};
	}

	/** "Quarryman III" for a root, the plain name otherwise. */
	public static MutableComponent name(Perk perk, int rank) {
		MutableComponent n = perk.name().copy();
		return perk.kind() == Perk.Kind.ROOT ? n.append(" " + roman(rank)) : n;
	}

	public static Component keyword(RegistryAccess registries, Identifier id) {
		return registries.lookupOrThrow(MettleRegistries.KEYWORD).getOptional(id).map(Keyword::name).orElse(Component.literal(id.getPath()));
	}

	/** "Brutal · Echo". */
	public static MutableComponent keywords(RegistryAccess registries, List<Identifier> ids) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < ids.size(); i++) {
			if (i > 0) {
				out.append(" · ");
			}
			out.append(keyword(registries, ids.get(i)));
		}
		return out;
	}

	/** The trait's sentence with its value: "Dig 9 % faster below y 0." */
	public static Component sentence(Trait trait, double value) {
		String v = trait.format(value);
		if (trait.description().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
			return Component.translatable(t.getKey(), v, v, v);
		}
		return trait.description();
	}

	/** "Deepborn 9 %", with the boosted value when it differs: "Deepborn 9 % → 11 %". */
	public static MutableComponent trait(RegistryAccess registries, TraitRoll roll, Optional<Double> boosted) {
		Optional<Trait> t = registries.lookupOrThrow(MettleRegistries.TRAIT).getOptional(roll.trait());
		if (t.isEmpty()) {
			return Component.literal(roll.trait().getPath());
		}
		MutableComponent out = t.get().name().copy().append(" " + t.get().format(roll.value()));
		if (boosted.isPresent() && Math.abs(boosted.get() - roll.value()) > 1e-6) {
			out.append(Component.literal(" → " + t.get().format(boosted.get())).withStyle(ChatFormatting.AQUA));
		}
		return out;
	}

	public static MutableComponent traits(RegistryAccess registries, List<TraitRoll> rolls) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < rolls.size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(trait(registries, rolls.get(i), Optional.empty()));
		}
		return out;
	}

	public static Optional<Perk> perk(RegistryAccess registries, ResourceKey<Perk> key) {
		return registries.lookupOrThrow(MettleRegistries.PERK).getOptional(key);
	}
}
