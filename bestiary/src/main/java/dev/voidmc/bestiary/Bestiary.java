package dev.voidmc.bestiary;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.voidmc.fray.motion.MotionServer;
import dev.voidmc.bestiary.ai.Behaviours;
import dev.voidmc.bestiary.ai.CombatHooks;
import dev.voidmc.bestiary.ai.CreatureData;
import dev.voidmc.bestiary.ai.Grabs;
import dev.voidmc.bestiary.ai.Tokens;
import dev.voidmc.bestiary.ai.VanillaTakeover;
import dev.voidmc.bestiary.ai.profile.CreatureProfile;
import dev.voidmc.bestiary.ai.profile.CreatureProfiles;
import dev.voidmc.bestiary.danger.Danger;
import dev.voidmc.bestiary.danger.DangerConfig;
import dev.voidmc.bestiary.elite.EliteRules;
import dev.voidmc.bestiary.elite.Elites;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VOID Bestiary. Stage 0 carries the animation prototype's content (the clips in {@code src/main/motion}) and the
 * studio commands:
 * <pre>
 * /bestiary motion play &lt;targets&gt; &lt;clip&gt; [speed]   start a clip on the server (timeline, hit volumes, broadcast)
 * /bestiary motion stop &lt;targets&gt;
 * </pre>
 */
public final class Bestiary implements ModInitializer {
	public static final String ID = "bestiary";
	public static final Logger LOGGER = LoggerFactory.getLogger("VOID Bestiary");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(ID, path);
	}

	@Override
	public void onInitialize() {
		DangerConfig.load();
		dev.voidmc.bestiary.creature.BestiaryEntities.init();
		CreatureProfiles.init();
		Behaviours.init();
		Grabs.init();
		VanillaTakeover.init();
		CombatHooks.init();
		Elites.init();
		EliteRules.init();
		BestiaryCommands.init();
		dev.voidmc.bestiary.boss.BossEncounter.init();
		dev.voidmc.bestiary.boss.Lairs.init();
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Tokens.clear();
			dev.voidmc.bestiary.ai.Squads.clear();
			Grabs.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal(ID)
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("motion")
				.then(Commands.literal("play")
					.then(Commands.argument("targets", EntityArgument.entities())
						.then(Commands.argument("clip", IdentifierArgument.id())
							.executes(c -> play(c, 1))
							.then(Commands.argument("speed", FloatArgumentType.floatArg(0.1F, 4F))
								.executes(c -> play(c, FloatArgumentType.getFloat(c, "speed")))))))
				.then(Commands.literal("stop")
					.then(Commands.argument("targets", EntityArgument.entities())
						.executes(c -> {
							int n = 0;
							for (Entity e : EntityArgument.getEntities(c, "targets")) {
								if (e instanceof LivingEntity le) {
									MotionServer.stop(le, 4);
									n++;
								}
							}
							int stopped = n;
							c.getSource().sendSuccess(() -> Component.literal("stopped " + stopped), false);
							return n;
						}))))));
	}

	/**
	 * Creates a profiled creature at {@code at}: danger (or the place's when below 1), elite with these affixes (null:
	 * rolled for its danger) and a champion pack of {@code pack} members. Used by commands, tests and the showcase.
	 */
	public static @Nullable Mob create(ServerLevel level, CreatureProfile p, Vec3 at, float danger, boolean elite, @Nullable List<String> affixes, int pack) {
		Entity e = BuiltInRegistries.ENTITY_TYPE.getValue(p.entity()).create(level, EntitySpawnReason.COMMAND);
		if (!(e instanceof Mob m)) {
			return null;
		}
		m.snapTo(at.x, at.y, at.z, m.getRandom().nextFloat() * 360, 0);
		float d = danger >= 1 ? danger : Danger.at(level, m.blockPosition());
		List<String> af = affixes != null ? affixes : Elites.pick(p, d, m.getRandom());
		CreatureData.set(m, CreatureData.FRESH.withReason("command").withDanger(d).rolled(elite, elite ? af : List.of(), elite ? pack : 0));
		m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), EntitySpawnReason.COMMAND, null);
		m.setPersistenceRequired();
		level.addFreshEntity(m);
		return m;
	}

	private static int play(CommandContext<CommandSourceStack> c, float speed) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		Identifier clip = IdentifierArgument.getId(c, "clip");
		if (MotionServer.timeline(clip) == null) {
			c.getSource().sendFailure(Component.literal("no clip " + clip));
			return 0;
		}
		int n = 0;
		for (Entity e : EntityArgument.getEntities(c, "targets")) {
			if (e instanceof LivingEntity le && MotionServer.play(le, clip, speed)) {
				n++;
			}
		}
		int played = n;
		c.getSource().sendSuccess(() -> Component.literal("playing " + clip + " on " + played), false);
		return n;
	}
}
