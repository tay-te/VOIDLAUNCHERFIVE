package dev.voidmc.mettle.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * One table of byname words (docs/BUILD_FEEL.md §2.1), data at {@code data/<ns>/mettle/byname/<table>.json}: the
 * {@code keywords} table (a keyword id: its prefix for compounds and its word), the {@code skills} table (a skill id: its
 * suffix for compounds, its noun and a line saying what it means) and the {@code pairs} table (two skill ids joined by
 * {@code +}: the pair's noun and line). Every word is a Component, so lang (or a server's own data) names them.
 */
public record BynameWords(Map<String, Word> words) {
	/** The words of one keyword, skill or pair; each table uses the fields it needs. */
	public record Word(Optional<Component> prefix, Optional<Component> word, Optional<Component> suffix, Optional<Component> noun,
		Optional<Component> description) {
		public static final Codec<Word> CODEC = RecordCodecBuilder.create(i -> i.group(
			ComponentSerialization.CODEC.optionalFieldOf("prefix").forGetter(Word::prefix),
			ComponentSerialization.CODEC.optionalFieldOf("word").forGetter(Word::word),
			ComponentSerialization.CODEC.optionalFieldOf("suffix").forGetter(Word::suffix),
			ComponentSerialization.CODEC.optionalFieldOf("noun").forGetter(Word::noun),
			ComponentSerialization.CODEC.optionalFieldOf("description").forGetter(Word::description)
		).apply(i, Word::new));
	}

	public static final Codec<BynameWords> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.unboundedMap(Codec.STRING, Word.CODEC).fieldOf("words").forGetter(BynameWords::words)
	).apply(i, BynameWords::new));

	public BynameWords {
		words = Map.copyOf(words);
	}
}
