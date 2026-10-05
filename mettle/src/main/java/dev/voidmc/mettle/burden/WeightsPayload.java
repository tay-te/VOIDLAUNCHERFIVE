package dev.voidmc.mettle.burden;

import dev.voidmc.mettle.Mettle;
import it.unimi.dsi.fastutil.objects.Reference2FloatMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

/**
 * Server → client: every item's own weight ({@code mettle:burden/weights}), on join and after {@code /reload}. Recipes
 * are server data, so only the server can derive the table; tooltips and the gear panel read this copy. About 1,500
 * entries of a var-int and a float.
 */
public record WeightsPayload(Map<Item, Float> weights) implements CustomPacketPayload {
	public static final Type<WeightsPayload> TYPE = new Type<>(Mettle.id("burden/weights"));
	public static final StreamCodec<RegistryFriendlyByteBuf, WeightsPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.registry(Registries.ITEM), ByteBufCodecs.FLOAT), WeightsPayload::weights,
		WeightsPayload::new);

	public static WeightsPayload of(WeightTable table) {
		Map<Item, Float> map = new LinkedHashMap<>();
		for (Reference2FloatMap.Entry<Item> e : table.entries().reference2FloatEntrySet()) {
			map.put(e.getKey(), e.getFloatValue());
		}
		return new WeightsPayload(map);
	}

	@Override
	public Type<WeightsPayload> type() {
		return TYPE;
	}
}
