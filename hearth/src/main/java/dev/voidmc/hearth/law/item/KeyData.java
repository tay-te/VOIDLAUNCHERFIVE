package dev.voidmc.hearth.law.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * {@code hearth:key}: which locks a key opens: every lock of one plot (a household's doors and chests, a shop, the
 * guardhouse cells). Keys belong to households; whoever holds one opens what it opens, so a key lifted from a pocket is
 * a way in.
 *
 * @param settlement the settlement's id
 * @param plot       the plot within it
 * @param label      what it opens, for the tooltip ("the Miller house")
 * @param town       the town's name
 */
public record KeyData(long settlement, int plot, String label, String town) implements TooltipProvider {
	public static final Codec<KeyData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.fieldOf("settlement").forGetter(KeyData::settlement),
		Codec.INT.fieldOf("plot").forGetter(KeyData::plot),
		Codec.STRING.optionalFieldOf("label", "").forGetter(KeyData::label),
		Codec.STRING.optionalFieldOf("town", "").forGetter(KeyData::town)
	).apply(i, KeyData::new));

	public static final StreamCodec<ByteBuf, KeyData> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_LONG, KeyData::settlement,
		ByteBufCodecs.VAR_INT, KeyData::plot,
		ByteBufCodecs.STRING_UTF8, KeyData::label,
		ByteBufCodecs.STRING_UTF8, KeyData::town,
		KeyData::new);

	public boolean opens(long settlementId, int plotId) {
		return this.settlement == settlementId && this.plot == plotId;
	}

	@Override
	public void addToTooltip(Item.TooltipContext context, Consumer<Component> out, TooltipFlag flag, DataComponentGetter components) {
		out.accept(Component.translatable("hearth.law.tooltip.key", this.label, this.town).withStyle(ChatFormatting.GRAY));
	}
}
