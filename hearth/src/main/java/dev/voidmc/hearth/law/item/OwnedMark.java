package dev.voidmc.hearth.law.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.LawService.Owner;
import dev.voidmc.hearth.law.state.Codecs;
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
 * {@code hearth:owned}: someone else's item inside their container, while a player has it open. The law module puts it
 * on the container's items when an owned container opens (so the screen shows KCD's red hand on each) and takes it off
 * when the container closes; an item that leaves the container with it is taken, and becomes {@link StolenMark stolen}.
 * Following the item rather than the click is what catches every way of taking: clicks, shift-clicks, drags, number
 * keys and items thrown out of the screen.
 */
public record OwnedMark(Owner owner, String ownerName) implements TooltipProvider {
	public static final Codec<OwnedMark> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codecs.OWNER.fieldOf("owner").forGetter(OwnedMark::owner),
		Codec.STRING.optionalFieldOf("owner_name", "").forGetter(OwnedMark::ownerName)
	).apply(i, OwnedMark::new));

	public static final StreamCodec<ByteBuf, OwnedMark> STREAM_CODEC = StreamCodec.composite(
		LawStreamCodecs.OWNER, OwnedMark::owner,
		ByteBufCodecs.STRING_UTF8, OwnedMark::ownerName,
		OwnedMark::new);

	@Override
	public void addToTooltip(Item.TooltipContext context, Consumer<Component> out, TooltipFlag flag, DataComponentGetter components) {
		out.accept(Component.translatable("hearth.law.tooltip.owned", this.ownerName).withStyle(ChatFormatting.DARK_RED));
	}
}
