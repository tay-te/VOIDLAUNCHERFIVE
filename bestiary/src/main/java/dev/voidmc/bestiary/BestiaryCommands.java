package dev.voidmc.bestiary;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.voidmc.bestiary.ai.Brain;
import dev.voidmc.bestiary.ai.CreatureData;
import dev.voidmc.bestiary.ai.profile.CreatureProfile;
import dev.voidmc.bestiary.ai.profile.CreatureProfiles;
import dev.voidmc.bestiary.codex.Codex;
import dev.voidmc.bestiary.danger.Danger;
import dev.voidmc.bestiary.elite.Affix;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bestiary's AI commands (operators):
 * <pre>
 * /bestiary spawn &lt;creature&gt; [danger]                 a creature by profile id (zombie, revenant_soldier, ...) at a danger
 * /bestiary elite &lt;creature&gt; &lt;danger&gt; &lt;affixes&gt; [pack]  an elite with affixes ("ironbound,swift", "random") and a champion pack
 * /bestiary danger                                  the danger where you stand
 * /bestiary codex [player]                          what a player's codex holds
 * /bestiary brain &lt;targets&gt;                         the AI state of creatures
 * </pre>
 */
final class BestiaryCommands {
	private BestiaryCommands() {
	}

	static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("bestiary")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("spawn")
				.then(Commands.argument("creature", StringArgumentType.word())
					.executes(c -> spawn(c, -1, false, List.of(), 0))
					.then(Commands.argument("danger", FloatArgumentType.floatArg(1, 5))
						.executes(c -> spawn(c, FloatArgumentType.getFloat(c, "danger"), false, List.of(), 0)))))
			.then(Commands.literal("elite")
				.then(Commands.argument("creature", StringArgumentType.word())
					.then(Commands.argument("danger", FloatArgumentType.floatArg(1, 5))
						.then(Commands.argument("affixes", StringArgumentType.word())
							.executes(c -> spawn(c, FloatArgumentType.getFloat(c, "danger"), true, affixes(c), 0))
							.then(Commands.argument("pack", IntegerArgumentType.integer(0, 6))
								.executes(c -> spawn(c, FloatArgumentType.getFloat(c, "danger"), true, affixes(c), IntegerArgumentType.getInteger(c, "pack"))))))))
			.then(Commands.literal("danger").executes(c -> {
				ServerLevel level = c.getSource().getLevel();
				float d = Danger.at(level, net.minecraft.core.BlockPos.containing(c.getSource().getPosition()));
				c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "danger %.2f", d)), false);
				return (int) d;
			}))
			.then(Commands.literal("codex")
				.executes(c -> codex(c, c.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player()).executes(c -> codex(c, EntityArgument.getPlayer(c, "player")))))
			.then(Commands.literal("brain")
				.then(Commands.argument("targets", EntityArgument.entities()).executes(c -> {
					int n = 0;
					for (Entity e : EntityArgument.getEntities(c, "targets")) {
						if (e instanceof Mob m && Brain.get(m) != null) {
							Brain b = Brain.get(m);
							CreatureData d = CreatureData.of(m);
							String line = String.format(Locale.ROOT, "%s #%d %s role %s target %s move %s pos %s danger %.1f elite %s %s", b.profile.id().getPath(),
								m.getId(), b.awareness, b.role, b.target == null ? "-" : b.target.getName().getString(), b.move == null ? "-" : b.move.id(),
								b.position, d.danger(), d.elite(), d.affixes());
							c.getSource().sendSuccess(() -> Component.literal(line), false);
							n++;
						}
					}
					return n;
				})))));
	}

	private static List<String> affixes(CommandContext<CommandSourceStack> c) {
		String s = StringArgumentType.getString(c, "affixes");
		List<String> out = new ArrayList<>();
		if (s.equals("none")) {
			return out;
		}
		if (s.equals("random")) {
			return null;
		}
		for (String a : s.split(",")) {
			if (Affix.byId(a) != null) {
				out.add(a);
			}
		}
		return out;
	}

	static @Nullable CreatureProfile profile(String name) {
		CreatureProfile p = CreatureProfiles.get(name.contains(":") ? Identifier.parse(name) : Bestiary.id(name));
		if (p == null) {
			for (CreatureProfile q : CreatureProfiles.all().values()) {
				if (q.entity().getPath().equals(name)) {
					return q;
				}
			}
		}
		return p;
	}

	private static int spawn(CommandContext<CommandSourceStack> c, float danger, boolean elite, @Nullable List<String> affixes, int pack)
		throws CommandSyntaxException {
		CreatureProfile p = profile(StringArgumentType.getString(c, "creature"));
		if (p == null) {
			c.getSource().sendFailure(Component.literal("no creature profile " + StringArgumentType.getString(c, "creature") + " (" + CreatureProfiles.all().keySet() + ")"));
			return 0;
		}
		ServerLevel level = c.getSource().getLevel();
		Vec3 at = c.getSource().getPosition();
		Mob m = Bestiary.create(level, p, at, danger, elite, affixes, pack);
		if (m == null) {
			c.getSource().sendFailure(Component.literal("couldn't create " + p.entity()));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("spawned " + m.getName().getString() + " #" + m.getId()), false);
		return m.getId();
	}

	private static int codex(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		Map<String, Codex.Entry> entries = Codex.book(p).entries();
		c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + "'s codex: " + entries.size() + " creatures"), false);
		entries.forEach((k, e) -> c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  %s: %s, fought %d, felled %d, moves %s", k,
			e.knowledge(), e.fought(), e.felled(), e.moves())), false));
		return entries.size();
	}

}
