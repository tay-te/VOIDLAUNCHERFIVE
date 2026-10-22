package dev.voidmc.fray.combat;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.Rank;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.api.TellColour;
import dev.voidmc.fray.combat.action.Arts;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Studio commands for combat (operators):
 * <pre>
 * /fray status &lt;targets&gt; &lt;status&gt; [ticks]        apply a status
 * /fray buildup &lt;targets&gt; &lt;status&gt; &lt;amount&gt;     add build-up
 * /fray rank &lt;targets&gt; common|elite|boss [subtitle]
 * /fray encounter start &lt;boss&gt; &lt;name&gt; [phase2]   /fray encounter end &lt;boss&gt;
 * /fray art &lt;player&gt; whirlwind|earthshaker|none
 * /fray tell &lt;attacker&gt; &lt;target&gt; white|orange|red|purple &lt;ticks&gt;
 * </pre>
 */
public final class CombatCommands {
	private CombatCommands() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("fray")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("status").then(Commands.argument("targets", EntityArgument.entities()).then(Commands.argument("status", StringArgumentType.word())
				.executes(c -> status(c.getSource(), EntityArgument.getEntities(c, "targets"), StringArgumentType.getString(c, "status"), 0))
				.then(Commands.argument("ticks", IntegerArgumentType.integer(1, 72000))
					.executes(c -> status(c.getSource(), EntityArgument.getEntities(c, "targets"), StringArgumentType.getString(c, "status"),
						IntegerArgumentType.getInteger(c, "ticks")))))))
			.then(Commands.literal("buildup").then(Commands.argument("targets", EntityArgument.entities()).then(Commands.argument("status", StringArgumentType.word())
				.then(Commands.argument("amount", FloatArgumentType.floatArg(0, 10000)).executes(c -> {
					StatusType s = StatusType.byId(StringArgumentType.getString(c, "status"));
					int n = 0;
					for (Entity e : EntityArgument.getEntities(c, "targets")) {
						if (e instanceof LivingEntity le) {
							FrayApi.statuses().addBuildUp(le, s, FloatArgumentType.getFloat(c, "amount"), c.getSource().getEntity());
							n++;
						}
					}
					return n;
				})))))
			.then(Commands.literal("rank").then(Commands.argument("targets", EntityArgument.entities()).then(Commands.argument("rank", StringArgumentType.word())
				.executes(c -> rank(EntityArgument.getEntities(c, "targets"), StringArgumentType.getString(c, "rank"), null))
				.then(Commands.argument("subtitle", StringArgumentType.greedyString())
					.executes(c -> rank(EntityArgument.getEntities(c, "targets"), StringArgumentType.getString(c, "rank"), StringArgumentType.getString(c, "subtitle")))))))
			.then(Commands.literal("encounter")
				.then(Commands.literal("start").then(Commands.argument("boss", EntityArgument.entity()).then(Commands.argument("name", StringArgumentType.string())
					.executes(c -> encounter(EntityArgument.getEntity(c, "boss"), StringArgumentType.getString(c, "name"), 0.55F))
					.then(Commands.argument("phase2", FloatArgumentType.floatArg(0, 1))
						.executes(c -> encounter(EntityArgument.getEntity(c, "boss"), StringArgumentType.getString(c, "name"), FloatArgumentType.getFloat(c, "phase2")))))))
				.then(Commands.literal("end").then(Commands.argument("boss", EntityArgument.entity()).executes(c -> {
					if (EntityArgument.getEntity(c, "boss") instanceof Mob m) {
						FrayApi.encounters().end(m, dev.voidmc.fray.api.Encounter.Outcome.RESET);
					}
					return 1;
				}))))
			.then(Commands.literal("art").then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("art", StringArgumentType.word()).executes(c -> {
				ServerPlayer p = EntityArgument.getPlayer(c, "player");
				String a = StringArgumentType.getString(c, "art");
				FrayApi.actions().setArt(p, a.equals("none") ? null : a.equals("earthshaker") ? Arts.EARTHSHAKER : Arts.WHIRLWIND);
				c.getSource().sendSuccess(() -> Component.literal("art " + a), false);
				return 1;
			}))))
			.then(Commands.literal("tell").then(Commands.argument("attacker", EntityArgument.entity()).then(Commands.argument("target", EntityArgument.entity())
				.then(Commands.argument("colour", StringArgumentType.word()).then(Commands.argument("ticks", IntegerArgumentType.integer(1, 200)).executes(c -> {
					if (EntityArgument.getEntity(c, "attacker") instanceof LivingEntity a && EntityArgument.getEntity(c, "target") instanceof LivingEntity t) {
						FrayApi.telegraphs().tell(a, t, TellColour.byId(StringArgumentType.getString(c, "colour")), IntegerArgumentType.getInteger(c, "ticks"),
							Component.literal(a.getName().getString() + " attacks"));
					}
					return 1;
				}))))))));
	}

	private static int status(net.minecraft.commands.CommandSourceStack src, java.util.Collection<? extends Entity> targets, String id, int ticks) {
		StatusType s = StatusType.byId(id);
		int n = 0;
		for (Entity e : targets) {
			if (e instanceof LivingEntity le) {
				FrayApi.statuses().apply(le, s, ticks, src.getEntity());
				n++;
			}
		}
		return n;
	}

	private static int rank(java.util.Collection<? extends Entity> targets, String rank, String subtitle) {
		Rank r = Rank.valueOf(rank.toUpperCase(Locale.ROOT));
		int n = 0;
		for (Entity e : targets) {
			if (e instanceof LivingEntity le) {
				FrayApi.profiles().set(le, r, subtitle == null ? null : Component.literal(subtitle), List.of());
				n++;
			}
		}
		return n;
	}

	private static int encounter(Entity boss, String name, float phase2) {
		if (boss instanceof Mob m) {
			FrayApi.encounters().start(m, Component.literal(name), null, new float[] {phase2});
			return 1;
		}
		return 0;
	}
}
