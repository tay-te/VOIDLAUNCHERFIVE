package dev.voidmc.hearth.sim.wild;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The posts' commands (operator level 2), under {@code /hearth wild}:
 * <pre>
 *   /hearth wild stats              sited regions, posts founded, their cost
 *   /hearth wild near [regions]     the posts sited round you (the overlay, worked out off the server thread)
 *   /hearth wild tp &lt;type&gt; [regions] teleports you to the nearest post of a type (searched off the server thread)
 *   /hearth wild found              founds the nearest post sited round you now, wherever you are
 *   /hearth wild move               the nearest nomad camp packs up and moves to its other ground now
 * </pre>
 */
public final class WildCommands {
	private static final SimpleCommandExceptionType NO_POSTS = new SimpleCommandExceptionType(Component.literal("No posts in this dimension"));

	private WildCommands() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> register(dispatcher));
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hearth").then(Commands.literal("wild").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("stats").executes(WildCommands::stats))
			.then(Commands.literal("near").executes(c -> near(c, 1))
				.then(Commands.argument("regions", IntegerArgumentType.integer(0, 6)).executes(c -> near(c, IntegerArgumentType.getInteger(c, "regions")))))
			.then(Commands.literal("tp").then(Commands.argument("type", StringArgumentType.word()).executes(c -> tp(c, 4))
				.then(Commands.argument("regions", IntegerArgumentType.integer(0, 16)).executes(c -> tp(c, IntegerArgumentType.getInteger(c, "regions"))))))
			.then(Commands.literal("found").executes(WildCommands::found))
			.then(Commands.literal("move").executes(WildCommands::move))));
	}

	private static PostWorld posts(CommandSourceStack source) throws CommandSyntaxException {
		PostWorld p = Wild.posts(SimServer.world(source.getLevel()));
		if (p == null || !p.enabled()) {
			throw NO_POSTS.create();
		}
		return p;
	}

	private static void say(CommandSourceStack source, String text) {
		source.sendSuccess(() -> Component.literal(text), false);
	}

	private static int stats(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		PostWorld p = posts(c.getSource());
		SimWorld w = SimServer.world(c.getSource().getLevel());
		PostBook book = w.data(PostBook.KEY);
		int posts = 0;
		int embodied = 0;
		int folk = 0;
		for (SimSettlement s : w.settlements()) {
			if (s.kind() == dev.voidmc.hearth.api.SettlementKind.POST) {
				posts++;
				folk += s.population();
				embodied += s.embodied() ? 1 : 0;
			}
		}
		say(c.getSource(), "posts: " + posts + " live (" + folk + " souls, " + embodied + " embodied), " + book.foundedCount() + " founded, "
			+ book.skippedCount() + " passed over");
		say(c.getSource(), p.describe());
		for (String line : LivingWorldStats.lines()) {
			if (line.contains(" wild:")) {
				say(c.getSource(), line);
			}
		}
		return posts;
	}

	/** The sites round a point, nearest first, the overlay worked out on a worker. */
	private static CompletableFuture<List<PostSite>> around(PostWorld p, BlockPos at, int rings) {
		return CompletableFuture.supplyAsync(() -> {
			List<PostSite> out = new ArrayList<>();
			int rx0 = Atlas.region(at.getX());
			int rz0 = Atlas.region(at.getZ());
			for (int r = 0; r <= rings; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
							out.addAll(p.region(rx0 + dx, rz0 + dz));
						}
					}
				}
			}
			out.sort(java.util.Comparator.comparingDouble(s -> s.center().distSqr(at)));
			return out;
		});
	}

	private static int near(CommandContext<CommandSourceStack> c, int rings) throws CommandSyntaxException {
		PostWorld p = posts(c.getSource());
		CommandSourceStack src = c.getSource();
		BlockPos at = BlockPos.containing(src.getPosition());
		say(src, "Siting the posts of " + (2 * rings + 1) * (2 * rings + 1) + " regions round you...");
		around(p, at, rings).whenComplete((sites, t) -> src.getServer().execute(() -> {
			if (t != null) {
				src.sendFailure(Component.literal("Siting failed: " + t));
				return;
			}
			SimWorld w = SimServer.world(src.getLevel());
			PostBook book = w == null ? null : w.data(PostBook.KEY);
			for (PostSite s : sites) {
				long id = book == null ? 0 : book.settlement(s.key());
				SimSettlement live = id == 0 ? null : w.settlement(id);
				say(src, String.format(java.util.Locale.ROOT, "%s, %d blocks: %s%s", s.type().getSerializedName(), (int) Math.sqrt(s.center().distSqr(at)),
					s, live != null ? " - " + live.name() + " (" + live.population() + " souls)" : book != null && book.known(s.key()) ? " - passed over" : ""));
			}
			if (sites.isEmpty()) {
				say(src, "No posts sited round you.");
			}
		}));
		return 1;
	}

	private static int tp(CommandContext<CommandSourceStack> c, int rings) throws CommandSyntaxException {
		PostWorld p = posts(c.getSource());
		CommandSourceStack src = c.getSource();
		ServerPlayer player = src.getPlayerOrException();
		PostType type = PostType.byId(StringArgumentType.getString(c, "type"));
		if (type == null) {
			src.sendFailure(Component.literal("No such post type; one of: " + String.join(", ",
				java.util.Arrays.stream(PostType.values()).map(PostType::getSerializedName).toList())));
			return 0;
		}
		BlockPos at = player.blockPosition();
		say(src, "Looking for the nearest " + type.getSerializedName() + " within " + rings + " regions...");
		around(p, at, rings).whenComplete((sites, t) -> src.getServer().execute(() -> {
			PostSite best = null;
			if (sites != null) {
				for (PostSite s : sites) {
					if (s.type() == type) {
						best = s;
						break;
					}
				}
			}
			if (best == null) {
				src.sendFailure(Component.literal("No " + type.getSerializedName() + " within " + rings + " regions" + (t != null ? ": " + t : "")));
				return;
			}
			// stand a little way out from its square, on its front, looking at it
			int out = best.type().radius + 10;
			int x = best.x() + best.facing().getStepX() * out;
			int z = best.z() + best.facing().getStepZ() * out;
			if (best.type() == PostType.TOLL_GATE) {
				x = best.x() + best.facing().getStepX() * 24;
				z = best.z() + best.facing().getStepZ() * 24;
			}
			int y = src.getLevel().getChunk(x >> 4, z >> 4).getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
			float yaw = best.facing().getOpposite().toYRot();
			player.teleportTo(src.getLevel(), x + 0.5, y, z + 0.5, java.util.Set.of(), yaw, 10, false);
			p.found(best);
			say(src, "At the " + best.type().getSerializedName() + " " + best);
		}));
		return 1;
	}

	private static int found(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		PostWorld p = posts(c.getSource());
		CommandSourceStack src = c.getSource();
		BlockPos at = BlockPos.containing(src.getPosition());
		around(p, at, 1).whenComplete((sites, t) -> src.getServer().execute(() -> {
			SimWorld w = SimServer.world(src.getLevel());
			if (sites == null || sites.isEmpty() || w == null) {
				src.sendFailure(Component.literal("No post sited round you"));
				return;
			}
			PostSite s = sites.get(0);
			w.data(PostBook.KEY).forget(s.key());
			say(src, p.found(s) ? "Founding the " + s.type().getSerializedName() + " at " + s.center().toShortString()
				: "The " + s.type().getSerializedName() + " at " + s.center().toShortString() + " stands already or cannot be founded");
		}));
		return 1;
	}

	private static int move(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		SimWorld w = SimServer.world(c.getSource().getLevel());
		BlockPos at = BlockPos.containing(c.getSource().getPosition());
		SimSettlement best = null;
		if (w != null) {
			for (SimSettlement s : w.settlements()) {
				if (PostType.NOMAD_CAMP.getSerializedName().equals(s.postType()) && (best == null || s.center().distSqr(at) < best.center().distSqr(at))) {
					best = s;
				}
			}
		}
		if (best == null) {
			throw NO_POSTS.create();
		}
		PostData d = PostData.of(best);
		PostRules.move(best, d, w.day());
		say(c.getSource(), best.name() + " packs up for ground " + d.ground);
		return 1;
	}
}
