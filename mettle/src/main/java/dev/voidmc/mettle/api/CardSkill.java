package dev.voidmc.mettle.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * A skill's card data (docs/CARDS.md §2.1, §6.2): the short prefix its card ids print with ({@code MN}), each drafted
 * perk's native keyword, and the pool a card's second keyword is rolled from. Data, synced, loaded from
 * {@code data/<ns>/mettle/card_skill/<skill>.json} (the file's id is the skill's).
 *
 * <p>CARDS.md puts these on the perk and skill JSON; they live here because {@link Perk}'s codec is at
 * {@code RecordCodecBuilder}'s sixteen-field limit, and one file per skill reads better for keyword balance.
 */
public record CardSkill(String prefix, List<Identifier> pool, Map<ResourceKey<Perk>, Identifier> natives) {
	public static final Codec<CardSkill> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("prefix").forGetter(CardSkill::prefix),
		Identifier.CODEC.listOf().fieldOf("keyword_pool").forGetter(CardSkill::pool),
		Codec.unboundedMap(ResourceKey.codec(MettleRegistries.PERK), Identifier.CODEC).fieldOf("natives").forGetter(CardSkill::natives)
	).apply(i, CardSkill::new));
}
