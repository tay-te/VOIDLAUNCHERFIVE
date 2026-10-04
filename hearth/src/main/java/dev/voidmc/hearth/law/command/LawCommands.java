package dev.voidmc.hearth.law.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService.Crime;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.LawConfig;
import dev.voidmc.hearth.law.LawModule;
import dev.voidmc.hearth.law.LawServiceImpl;
import dev.voidmc.hearth.law.crime.CrimeDesk;
import dev.voidmc.hearth.law.crime.LawStatusSender;
import dev.voidmc.hearth.law.item.LawItems;
import dev.voidmc.hearth.law.justice.Jail;
import dev.voidmc.hearth.law.justice.Justice;
import dev.voidmc.hearth.law.justice.PlayerLaw;
import dev.voidmc.hearth.law.justice.Warrant;
import dev.voidmc.hearth.law.lock.Locks;
import dev.voidmc.hearth.law.own.Ownership;
import dev.voidmc.hearth.law.state.LawState;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * {@code /hearth law ...}: everyone may see their own standing with the law ({@code status}) and the wanted posters of
 * the town they stand in ({@code wanted}); game masters may pardon, set bounties, jail and release, inspect locks, hand
 * themselves keys and picks, report a crime against themselves to try the guards, and reload the settings.
 */
public final class LawCommands {
	private static final SimpleCommandExceptionType NO_SETTLEMENT = new SimpleCommandExceptionType(Component.translatable("hearth.law.command.no_settlement"));

	private LawCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hearth").then(Commands.literal("law")
			.then(Commands.literal("status")
				.executes(c -> status(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player()).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(c -> status(c, EntityArgument.getPlayer(c, "player")))))
			.then(Commands.literal("wanted").executes(LawCommands::wanted))
			.then(Commands.literal("pardon").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.executes(c -> pardon(c, false))
					.then(Commands.literal("everywhere").executes(c -> pardon(c, true)))))
			.then(Commands.literal("bounty").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("bits", LongArgumentType.longArg(0, 1_000_000)).executes(LawCommands::bounty))))
			.then(Commands.literal("crime").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("offence", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Offence.values()).map(Offence::getSerializedName), b))
					.executes(c -> crime(c, 0))
					.then(Commands.argument("value", LongArgumentType.longArg(0, 100_000)).executes(c -> crime(c, LongArgumentType.getLong(c, "value"))))))
			.then(Commands.literal("jail").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440)).executes(c -> jail(c, false)))))
			.then(Commands.literal("pillory").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("minutes", IntegerArgumentType.integer(1, 60)).executes(c -> jail(c, true)))))
			.then(Commands.literal("release").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player()).executes(LawCommands::release)))
			.then(Commands.literal("lock").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(LawCommands::lock))
			.then(Commands.literal("key").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(LawCommands::key))
			.then(Commands.literal("picks").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(c -> {
					c.getSource().getPlayerOrException().getInventory().placeItemBackInInventory(new net.minecraft.world.item.ItemStack(LawItems.LOCKPICK, 16), net.minecraft.util.Prediction.SERVER_ONLY);
					return 1;
				}))
			.then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
				LawModule.reloadConfig();
				LawConfig cfg = Law.rawConfig();
				c.getSource().sendSuccess(() -> Component.translatable("hearth.law.command.reloaded", cfg.mode().getSerializedName()), true);
				return 1;
			}))));
	}

	private static Settlement here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		HearthWorld world = HearthWorld.of(c.getSource().getLevel());
		Settlement s = world == null ? null : world.settlementAt(BlockPos.containing(c.getSource().getPosition()));
		if (s == null) {
			throw NO_SETTLEMENT.create();
		}
		return s;
	}

	private static int status(CommandContext<CommandSourceStack> c, ServerPlayer player) {
		HearthWorld world = HearthWorld.of(player.level());
		int lines = 0;
		PlayerLaw law = PlayerLaw.of(player);
		if (law.sentence().isPresent()) {
			PlayerLaw.Sentence s = law.sentence().get();
			c.getSource().sendSuccess(() -> Component.translatable("hearth.law.command.serving", s.ticksLeft() / 1200 + 1, s.pillory() ? "pillory" : "cell"), false);
			lines++;
		}
		if (world != null) {
			for (Settlement s : world.settlements()) {
				LawState state = Law.state(s);
				Warrant w = state.warrant(player.getUUID());
				int suspicion = state.suspicion(player.getUUID());
				if (w != null && w.active() || suspicion > 0) {
					long bounty = w == null ? 0 : w.bounty();
					String worst = w == null || !w.active() ? "-" : w.worst().getSerializedName();
					boolean outlaw = w != null && w.outlaw();
					c.getSource().sendSuccess(() -> Component.translatable("hearth.law.command.warrant", s.name(), bounty, worst, suspicion,
						outlaw ? Component.translatable("hearth.law.hud.outlaw").withStyle(ChatFormatting.DARK_RED) : Component.empty()), false);
					lines++;
				}
			}
		}
		c.getSource().sendSuccess(() -> Component.translatable("hearth.law.command.skills", law.lockpickSkill(), law.pickpocketSkill()), false);
		if (lines == 0) {
			c.getSource().sendSuccess(() -> Component.translatable("hearth.law.command.clean"), false);
		}
		return lines;
	}

	private static int wanted(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = here(c);
		var posters = LawServiceImpl.posters(s);
		if (posters.isEmpty()) {
			c.getSource().sendSuccess(() -> Component.translatable("hearth.law.command.nobody_wanted", s.name()), false);
			return 0;
		}
		for (LawServiceImpl.Poster p : posters) {
			MutableComponent line = Component.translatable("hearth.law.command.poster", p.name(), Component.translatable(p.worst().translationKey()), p.bounty());
			if (p.outlaw()) {
				line.append(" ").append(Component.translatable("hearth.law.hud.outlaw").withStyle(ChatFormatting.DARK_RED));
			}
			c.getSource().sendSuccess(() -> line, false);
		}
		return posters.size();
	}

	private static int pardon(CommandContext<CommandSourceStack> c, boolean everywhere) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		int n = 0;
		if (everywhere) {
			HearthWorld world = HearthWorld.of(player.level());
			if (world != null) {
				for (Settlement s : world.settlements()) {
					if (Law.state(s).warrant(player.getUUID()) != null || Law.state(s).suspicion(player.getUUID()) > 0) {
						Justice.pardon(s, player.getUUID());
						n++;
					}
				}
			}
		} else {
			Justice.pardon(here(c), player.getUUID());
			n = 1;
		}
		int count = n;
		LawStatusSender.refresh(player);
		c.getSource().sendSuccess(() -> Component.translatable("hearth.law.command.pardoned", player.getName(), count), true);
		return n;
	}

	private static int bounty(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		Settlement s = here(c);
		long bits = LongArgumentType.getLong(c, "bits");
		Warrant w = Law.state(s).warrantFor(player.getUUID(), Law.day(player.level()));
		w.reduce(w.bounty(), Law.day(player.level()));
		if (bits > 0) {
			w.charge(Offence.THEFT, bits, Law.day(player.level()));
		}
		Law.state(s).prune();
		s.markDirty();
		LawStatusSender.refresh(player);
		c.getSource().sendSuccess(() -> Component.translatable("hearth.law.command.bounty_set", player.getName(), bits, s.name()), true);
		return 1;
	}

	private static int crime(CommandContext<CommandSourceStack> c, long value) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Settlement s = here(c);
		String name = StringArgumentType.getString(c, "offence").toLowerCase(Locale.ROOT);
		Offence offence = Arrays.stream(Offence.values()).filter(o -> o.getSerializedName().equals(name)).findFirst().orElse(Offence.THEFT);
		CrimeDesk.file(player.level(), s, new Crime(offence, player.getUUID(), s.id(), player.blockPosition(), null, value, true,
			player.level().getGameTime()), null);
		return 1;
	}

	private static int jail(CommandContext<CommandSourceStack> c, boolean pillory) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		Settlement s = here(c);
		int minutes = IntegerArgumentType.getInteger(c, "minutes");
		if (!Jail.sentence(player, s, minutes, pillory, 0)) {
			c.getSource().sendFailure(Component.translatable(pillory ? "hearth.law.command.no_pillory" : "hearth.law.command.no_cell", s.name()));
			return 0;
		}
		return 1;
	}

	private static int release(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		PlayerLaw law = PlayerLaw.of(player);
		law.sentence().ifPresent(s -> player.teleportTo(s.release().getX() + 0.5, s.release().getY(), s.release().getZ() + 0.5));
		PlayerLaw.set(player, law.withSentence(null));
		LawStatusSender.refresh(player);
		return 1;
	}

	private static int lock(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		HitResult hit = player.pick(6, 1, false);
		if (!(hit instanceof BlockHitResult b) || hit.getType() != HitResult.Type.BLOCK) {
			c.getSource().sendFailure(Component.translatable("hearth.law.command.look_at_lock"));
			return 0;
		}
		Locks.Lock lock = Locks.at(player.level(), b.getBlockPos(), player.level().getBlockState(b.getBlockPos()));
		Ownership.Place place = Ownership.place(player.level(), b.getBlockPos());
		Component owner = place == null || place.owner() == null ? Component.translatable("hearth.law.owner.public")
			: Ownership.name(place.owner(), player.level().getServer());
		String plot = place == null || place.plot() == null ? "-" : place.plot().id() + " " + place.plot().spec().archetype();
		if (lock == null) {
			c.getSource().sendSuccess(() -> Component.translatable("hearth.law.command.no_lock", owner, plot), false);
		} else {
			c.getSource().sendSuccess(() -> Component.translatable("hearth.law.command.lock_info", owner, plot, lock.tier(), lock.locked()), false);
		}
		return 1;
	}

	private static int key(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Ownership.Place place = Ownership.place(player.level(), player.blockPosition());
		if (place == null || place.plot() == null) {
			throw NO_SETTLEMENT.create();
		}
		PlotView plot = place.plot();
		Resident holder = Locks.keyHolder(place.settlement(), plot.id());
		String label = holder != null ? Locks.keyData(place.settlement(), plot.id(), holder).label() : plot.spec().archetype().getPath();
		player.getInventory().placeItemBackInInventory(LawItems.key(new dev.voidmc.hearth.law.item.KeyData(place.settlement().id(), plot.id(), label,
			place.settlement().name())), net.minecraft.util.Prediction.SERVER_ONLY);
		return 1;
	}
}
