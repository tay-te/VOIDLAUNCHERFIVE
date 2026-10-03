package dev.voidmc.hearth.quest.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * What finishing a quest pays: coin in bits (from the settlement's treasury; emeralds when the economy module is
 * absent), items, and standing with the settlement. {@code text} is the same, written out by the server for the
 * client ("2 marks, 4 bits · 3 × Bread · +4 standing"), since only the server knows how coin is named.
 */
public record QuestReward(long coin, List<ItemReward> items, int reputation, String text) {
	public static final QuestReward NONE = new QuestReward(0, List.of(), 0, "");

	public static final Codec<QuestReward> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.optionalFieldOf("coin", 0L).forGetter(QuestReward::coin),
		ItemReward.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(QuestReward::items),
		Codec.INT.optionalFieldOf("reputation", 0).forGetter(QuestReward::reputation),
		Codec.STRING.optionalFieldOf("text", "").forGetter(QuestReward::text)
	).apply(i, QuestReward::new));

	public QuestReward {
		items = List.copyOf(items);
		coin = Math.max(0, coin);
	}

	public QuestReward withCoin(long bits) {
		return new QuestReward(bits, this.items, this.reputation, this.text);
	}

	public QuestReward withText(String text) {
		return new QuestReward(this.coin, this.items, this.reputation, text);
	}

	/** An item reward, by id so the record needs no registry access to load. */
	public record ItemReward(Identifier item, int count) {
		public static final Codec<ItemReward> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("item").forGetter(ItemReward::item),
			Codec.INT.optionalFieldOf("count", 1).forGetter(ItemReward::count)
		).apply(i, ItemReward::new));
	}
}
