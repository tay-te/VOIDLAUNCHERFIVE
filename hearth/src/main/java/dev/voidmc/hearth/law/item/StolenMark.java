package dev.voidmc.hearth.law.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.LawService.Owner;
import dev.voidmc.hearth.law.state.Codecs;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * {@code hearth:stolen}: an item taken from someone (docs/LIVING_WORLD.md §4). That town's merchants will not buy it, a
 * fence will, and guards who search the thief take it back. The mark fades {@code stolen_fade_days} after {@link #day}.
 *
 * @param owner          whom it was taken from
 * @param ownerName      their name when it was taken (for the tooltip, without asking the server)
 * @param settlementName the town's name
 * @param day            the sim day it was taken
 * @param thief          who took it: guards hold only the thief to it, so nobody can be framed by planted goods
 */
public record StolenMark(Owner owner, String ownerName, String settlementName, long day, UUID thief) implements TooltipProvider {
	public static final Codec<StolenMark> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codecs.OWNER.fieldOf("owner").forGetter(StolenMark::owner),
		Codec.STRING.optionalFieldOf("owner_name", "").forGetter(StolenMark::ownerName),
		Codec.STRING.optionalFieldOf("settlement_name", "").forGetter(StolenMark::settlementName),
		Codec.LONG.fieldOf("day").forGetter(StolenMark::day),
		UUIDUtil.CODEC.fieldOf("thief").forGetter(StolenMark::thief)
	).apply(i, StolenMark::new));

	public static final StreamCodec<ByteBuf, StolenMark> STREAM_CODEC = StreamCodec.composite(
		LawStreamCodecs.OWNER, StolenMark::owner,
		ByteBufCodecs.STRING_UTF8, StolenMark::ownerName,
		ByteBufCodecs.STRING_UTF8, StolenMark::settlementName,
		ByteBufCodecs.VAR_LONG, StolenMark::day,
		UUIDUtil.STREAM_CODEC, StolenMark::thief,
		StolenMark::new);

	@Override
	public void addToTooltip(Item.TooltipContext context, Consumer<Component> out, TooltipFlag flag, DataComponentGetter components) {
		out.accept(Component.translatable("hearth.law.tooltip.stolen", this.ownerName, this.settlementName).withStyle(ChatFormatting.RED));
	}
}
