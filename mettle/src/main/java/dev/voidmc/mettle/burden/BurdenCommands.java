package dev.voidmc.mettle.burden;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /mettle weight [item]}: how an item's weight was reached ("iron_block = 9 × iron_ingot (leaf 0.5) = 4.5 kg"),
 * for the held item without an argument; with a player, also what it weighs for them after perks. {@code /mettle load
 * [player]}: load, capacity, band and the heaviest stack.
 */
public final class BurdenCommands {
	private BurdenCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
		dispatcher.register(Commands.literal("mettle")
			.then(Commands.literal("weight")
				.executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					ItemStack held = player.getMainHandItem();
					return weight(c, held.isEmpty() ? player.getOffhandItem() : held);
				})
				.then(Commands.argument("item", ItemArgument.item(context))
					.executes(c -> weight(c, ItemArgument.getItem(c, "item").createItemStack(1)))))
			.then(Commands.literal("load")
				.executes(c -> load(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player())
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(c -> load(c, EntityArgument.getPlayer(c, "player"))))));
	}

	private static int weight(CommandContext<CommandSourceStack> c, ItemStack stack) {
		if (stack.isEmpty()) {
			c.getSource().sendFailure(Component.translatable("mettle.burden.command.nothing_held"));
			return 0;
		}
		WeightTable table = Weights.server();
		Item item = stack.getItem();
		String line = Weights.explain(table, item);
		c.getSource().sendSuccess(() -> Component.literal(line), false);
		if (c.getSource().getEntity() instanceof ServerPlayer player) {
			float base = table.get(item);
			float mine = base * Weights.multiplier(player, stack);
			if (Math.abs(mine - base) > 1e-4f) {
				c.getSource().sendSuccess(() -> Component.translatable("mettle.burden.command.for_you", Weights.kg(mine))
					.withStyle(ChatFormatting.GRAY), false);
			}
		}
		return Math.round(table.get(item) * 1000f);
	}

	private static int load(CommandContext<CommandSourceStack> c, ServerPlayer player) throws CommandSyntaxException {
		LoadState s = LoadTracker.state(player);
		Component band = Component.translatable("mettle.burden.band." + s.band().name().toLowerCase(Locale.ROOT));
		if (s.exempt()) {
			c.getSource().sendSuccess(() -> Component.translatable("mettle.burden.command.load_exempt", player.getDisplayName()), false);
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.translatable("mettle.burden.command.load", player.getDisplayName(), Weights.total(s.load()),
			Weights.total(s.capacity()), band), false);
		if (s.hasHeaviest()) {
			c.getSource().sendSuccess(() -> Component.translatable("mettle.burden.heaviest_line", LoadTracker.heaviest(s)).withStyle(ChatFormatting.GRAY), false);
		}
		return Math.round(s.load());
	}
}
