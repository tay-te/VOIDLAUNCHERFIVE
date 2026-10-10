package dev.voidmc.mettle.character;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.voidmc.mettle.api.CharacterAttribute;
import dev.voidmc.mettle.api.MettleRegistries;
import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.core.CharacterState;
import dev.voidmc.mettle.core.CharacterStore;
import dev.voidmc.mettle.core.Curves;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * The skill tree from chat, until the overlay draws it: {@code /mettle tree <skill>} (the deck: what is held and what can
 * be dealt) and {@code /mettle attribute <name>} for everyone; perks come from drafts ({@code /mettle draft}). Learning a
 * perk without a draft, XP, grants and respecs are for ops (and a free respec below character level 5).
 */
public final class TreeCommands {
	private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(id -> Component.translatable("mettle.command.unknown", String.valueOf(id)));

	private TreeCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("mettle")
			.then(Commands.literal("tree")
				.then(Commands.argument("skill", ResourceKeyArgument.key(MettleRegistries.SKILL))
					.executes(c -> tree(c, c.getSource().getPlayerOrException()))))
			.then(Commands.literal("perk")
				.then(Commands.literal("learn")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("perk", ResourceKeyArgument.key(MettleRegistries.PERK))
						.executes(TreeCommands::learn)))
				.then(Commands.literal("grant")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("perk", ResourceKeyArgument.key(MettleRegistries.PERK))
						.then(Commands.argument("players", EntityArgument.players())
							.executes(TreeCommands::grant)))))
			.then(Commands.literal("attribute")
				.then(Commands.argument("attribute", com.mojang.brigadier.arguments.StringArgumentType.word())
					.suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
						java.util.Arrays.stream(CharacterAttribute.values()).map(CharacterAttribute::getSerializedName), b))
					.executes(TreeCommands::attribute)))
			.then(Commands.literal("xp")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("skill", ResourceKeyArgument.key(MettleRegistries.SKILL))
					.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0, 1_000_000))
						.executes(c -> xp(c, List.of(c.getSource().getPlayerOrException())))
						.then(Commands.argument("players", EntityArgument.players())
							.executes(c -> xp(c, EntityArgument.getPlayers(c, "players")))))))
			.then(Commands.literal("respec")
				.executes(c -> respec(c, List.of(c.getSource().getPlayerOrException()), false))
				.then(Commands.argument("players", EntityArgument.players())
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(c -> respec(c, EntityArgument.getPlayers(c, "players"), true)))));
	}

	private static int tree(CommandContext<CommandSourceStack> c, ServerPlayer player) throws CommandSyntaxException {
		ResourceKey<Skill> skillKey = ResourceKeyArgument.getRegistryKey(c, "skill", MettleRegistries.SKILL, UNKNOWN);
		Skill skill = player.level().registryAccess().lookupOrThrow(MettleRegistries.SKILL).getOptional(skillKey).orElseThrow(() -> UNKNOWN.create(skillKey.identifier()));
		CharacterState state = CharacterStore.get(player);
		PerkRules.TreeView view = Perks.tree(player, skillKey);
		int level = state.level(skillKey);
		double next = Curves.skillXpFor(Math.min(level + 1, Curves.SKILL_CAP));
		c.getSource().sendSystemMessage(Component.translatable("mettle.tree.header", skill.name(), level, (int) state.xp(skillKey), (int) next, view.points())
			.withStyle(ChatFormatting.BOLD));
		List<Holder.Reference<Perk>> perks = Perks.registry(player).listElements()
			.filter(r -> r.value().skill().equals(skillKey))
			.sorted(Comparator.comparingInt((Holder.Reference<Perk> r) -> r.value().kind().ordinal()).thenComparingInt(r -> r.value().tier())
				.thenComparing(r -> r.value().path()).thenComparingInt(r -> r.value().order()))
			.toList();
		for (Holder.Reference<Perk> ref : perks) {
			c.getSource().sendSystemMessage(line(ref, view, state));
		}
		return 1;
	}

	private static Component line(Holder.Reference<Perk> ref, PerkRules.TreeView view, CharacterState state) {
		Perk p = ref.value();
		int rank = state.rank(ref.key());
		boolean held = rank > 0;
		MutableComponent out = Component.literal(held ? " ● " : " ○ ").withStyle(held ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY);
		String where = switch (p.kind()) {
			case ROOT -> "I";
			case PATH -> (p.tier() == 2 ? "II" : "III") + ("a".equals(p.path()) ? "·A" : "·B");
			case MASTERY -> "Mastery";
			case FEAT -> "Feat";
			case SYNERGY -> "Synergy";
			case TEACHING -> "Teaching";
			case CURSED -> "Cursed";
		};
		out.append(Component.literal("[" + where + "] ").withStyle(ChatFormatting.GRAY));
		boolean hidden = p.kind() == Perk.Kind.FEAT && !held;
		out.append(hidden ? Component.literal("???").withStyle(ChatFormatting.DARK_GRAY) : p.name().copy().withStyle(held ? ChatFormatting.WHITE : ChatFormatting.GRAY));
		if (p.ranks() > 1) {
			out.append(Component.literal(" " + rank + "/" + p.ranks()).withStyle(ChatFormatting.GRAY));
		}
		Component detail = hidden ? p.hint().orElse(Component.empty()) : PerkText.description(p, Math.max(1, Math.min(rank + 1, p.ranks())));
		out.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY)).append(detail.copy().withStyle(ChatFormatting.GRAY));
		if (p.kind().drafted() && rank < p.ranks()) {
			Optional<PerkRules.Reason> why = PerkRules.eligible(ref.key(), p, view);
			out.append(why.isEmpty()
				? Component.translatable("mettle.tree.in_deck").withStyle(ChatFormatting.GREEN)
				: Component.literal("  (").append(Component.translatable(why.get().translationKey())).append(")").withStyle(ChatFormatting.DARK_GRAY));
		}
		return out;
	}

	private static int learn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ResourceKey<Perk> key = ResourceKeyArgument.getRegistryKey(c, "perk", MettleRegistries.PERK, UNKNOWN);
		Optional<PerkRules.Reason> why = Perks.learn(player, key);
		if (why.isPresent()) {
			c.getSource().sendFailure(Component.translatable(why.get().translationKey()));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.translatable("mettle.command.learned", key.identifier().toString()), false);
		return 1;
	}

	private static int grant(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ResourceKey<Perk> key = ResourceKeyArgument.getRegistryKey(c, "perk", MettleRegistries.PERK, UNKNOWN);
		Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
		int n = 0;
		for (ServerPlayer p : players) {
			CharacterState state = CharacterStore.get(p);
			var map = new java.util.HashMap<>(state.perks());
			Perk perk = Perks.registry(p).getOptional(key).orElseThrow(() -> UNKNOWN.create(key.identifier()));
			map.put(key, Math.min(perk.ranks(), map.getOrDefault(key, 0) + 1));
			CharacterStore.set(p, state.withPerks(map));
			dev.voidmc.mettle.core.EffectsApplier.apply(p);
			n++;
		}
		int count = n;
		c.getSource().sendSuccess(() -> Component.translatable("mettle.command.granted", key.identifier().toString(), count), true);
		return n;
	}

	private static int attribute(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		CharacterAttribute a = CharacterAttribute.CODEC.byName(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "attribute"));
		if (a == null || !Perks.spendAttribute(player, a)) {
			c.getSource().sendFailure(Component.translatable("mettle.command.no_attribute_point"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.translatable("mettle.command.attribute", a.displayName(), Perks.attributePoints(player)), false);
		return 1;
	}

	private static int xp(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players) throws CommandSyntaxException {
		ResourceKey<Skill> skill = ResourceKeyArgument.getRegistryKey(c, "skill", MettleRegistries.SKILL, UNKNOWN);
		double amount = DoubleArgumentType.getDouble(c, "amount");
		for (ServerPlayer p : players) {
			CharacterState s = CharacterStore.get(p);
			// Raw XP, without the favoured and Wits multipliers, so ops get exactly what they typed.
			Progress.award(p, skill, amount / Progress.multiplier(s, CharacterStore.background(p.level().registryAccess(), s), skill));
		}
		c.getSource().sendSuccess(() -> Component.translatable("mettle.command.xp", amount, skill.identifier().toString(), players.size()), true);
		return players.size();
	}

	private static int respec(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, boolean byOp) {
		for (ServerPlayer p : players) {
			if (!byOp && CharacterStore.get(p).characterLevel() >= 5 && !Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(c.getSource())) {
				c.getSource().sendFailure(Component.translatable("mettle.command.respec_costs"));
				return 0;
			}
			Perks.respec(p, true);
		}
		c.getSource().sendSuccess(() -> Component.translatable("mettle.command.respec", players.size()), true);
		return players.size();
	}
}
