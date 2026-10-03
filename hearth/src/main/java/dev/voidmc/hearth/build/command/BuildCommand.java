package dev.voidmc.hearth.build.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * {@code /hearth build <archetype> [style] [seed] [frontage] [depth] [storeys]}: generates a blueprint for the land in
 * front of the player and places it at once, its street front facing the player, so archetypes can be judged on real
 * terrain. The style is a culture name ({@code temperate}) or a style id, or {@code auto} for the culture of the biome
 * there; seed, frontage and depth default to random choices within the archetype's ranges. The chat line repeats the
 * full command so a good result can be rebuilt. {@code /hearth build undo} puts back what the last build replaced.
 *
 * <p>Game masters only (permission level 2). Places blocks directly, without neighbour updates, as a finished building.
 */
public final class BuildCommand {
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
	/** What each player's last build replaced, for one undo. */
	private static final Map<UUID, Undo> UNDO = new ConcurrentHashMap<>();

	private BuildCommand() {
	}

	private record Undo(ServerLevel level, List<BlockPos> positions, List<BlockState> states) {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		ArgumentBuilder<CommandSourceStack, ?> storeys = Commands.argument("storeys", IntegerArgumentType.integer(1, 6))
			.executes(c -> build(c, true, true, true, true, true));
		ArgumentBuilder<CommandSourceStack, ?> depth = Commands.argument("depth", IntegerArgumentType.integer(3, 48))
			.executes(c -> build(c, true, true, true, true, false)).then(storeys);
		ArgumentBuilder<CommandSourceStack, ?> frontage = Commands.argument("frontage", IntegerArgumentType.integer(3, 48))
			.executes(c -> build(c, true, true, true, false, false)).then(depth);
		ArgumentBuilder<CommandSourceStack, ?> seed = Commands.argument("seed", LongArgumentType.longArg())
			.executes(c -> build(c, true, true, false, false, false)).then(frontage);
		ArgumentBuilder<CommandSourceStack, ?> style = Commands.argument("style", StringArgumentType.string())
			.suggests((c, b) -> {
				List<String> names = new ArrayList<>();
				names.add("auto");
				for (Culture culture : Culture.values()) {
					names.add(culture.getSerializedName());
				}
				return SharedSuggestionProvider.suggest(names, b);
			})
			.executes(c -> build(c, true, false, false, false, false)).then(seed);
		ArgumentBuilder<CommandSourceStack, ?> archetype = Commands.argument("archetype", net.minecraft.commands.arguments.IdentifierArgument.id())
			.suggests((c, b) -> SharedSuggestionProvider.suggestResource(BuildModule.service().archetypes().stream().map(ArchetypeInfo::id), b))
			.executes(c -> build(c, false, false, false, false, false)).then(style);
		dispatcher.register(Commands.literal(Hearth.MOD_ID)
			.then(Commands.literal("build")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("undo").executes(BuildCommand::undo))
				.then(archetype)));
	}

	private static int build(CommandContext<CommandSourceStack> c, boolean hasStyle, boolean hasSeed, boolean hasFrontage, boolean hasDepth,
		boolean hasStoreys) throws CommandSyntaxException {
		CommandSourceStack source = c.getSource();
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = source.getLevel();
		BlueprintServiceImpl service = BuildModule.service();

		Identifier id = c.getArgument("archetype", Identifier.class);
		ArchetypeInfo info = service.archetype(id);
		if (info == null && id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
			info = service.archetype(Hearth.id(id.getPath()));
		}
		if (info == null) {
			source.sendFailure(Component.translatable("hearth.build.command.unknown_archetype", id.toString()));
			return 0;
		}
		long seed = hasSeed ? LongArgumentType.getLong(c, "seed") : level.getRandom().nextLong() & 0xFFFFFFFFL;
		java.util.Random r = new java.util.Random(seed * 31 + info.id().hashCode());
		int frontage = hasFrontage ? IntegerArgumentType.getInteger(c, "frontage") : info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = hasDepth ? IntegerArgumentType.getInteger(c, "depth") : info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		CompoundTag params = new CompoundTag();
		if (hasStoreys) {
			params.putInt("storeys", IntegerArgumentType.getInteger(c, "storeys"));
		}

		// the plot: two blocks ahead of the player, its front (and its street row) toward them, centred on them
		Direction ahead = player.getDirection();
		Direction right = ahead.getClockWise();
		Direction facing = ahead.getOpposite();
		BlockPos feet = player.blockPosition();
		BlockPos a = feet.relative(ahead, 3).relative(right, -(frontage / 2));
		BlockPos b = a.relative(ahead, depth - 1).relative(right, frontage - 1);
		int minX = Math.min(a.getX(), b.getX());
		int minZ = Math.min(a.getZ(), b.getZ());
		int sizeX = Math.abs(a.getX() - b.getX()) + 1;
		int sizeZ = Math.abs(a.getZ() - b.getZ()) + 1;
		LiveTerrain probe = new LiveTerrain(level);
		BlockPos street = feet.relative(ahead, 2);
		int floor = probe.groundY(street.getX(), street.getZ());
		if (probe.waterY(street.getX(), street.getZ()) != TerrainProbe.NO_WATER) {
			floor = Math.max(floor, probe.waterY(street.getX(), street.getZ()) + 1);
		}

		Identifier styleId;
		String styleArg = hasStyle ? StringArgumentType.getString(c, "style") : "auto";
		if (styleArg.equals("auto")) {
			Culture culture;
			try {
				culture = HearthServices.culture(probe.biomeAt(street.getX(), street.getZ()));
			} catch (RuntimeException e) {
				culture = Culture.TEMPERATE;
			}
			styleId = StyleRegistry.styleId(culture);
		} else {
			styleId = styleArg.contains(":") ? Identifier.tryParse(styleArg) : Hearth.id(styleArg.toLowerCase(Locale.ROOT));
		}
		Style style = styleId == null ? null : service.styles().get(styleId);
		if (style == null) {
			source.sendFailure(Component.translatable("hearth.build.command.unknown_style", styleArg));
			return 0;
		}

		PlotSpec spec = new PlotSpec(info.id(), styleId, seed, new BlockPos(minX, floor, minZ), sizeX, sizeZ, facing, Math.max(info.maxHeight(), 8), 2, params);
		long t0 = System.nanoTime();
		BlueprintImpl bp = Compiler.compile(service.draw(spec, probe));
		long t1 = System.nanoTime();

		// place stage by stage, remembering what was there
		List<BlockPos> positions = new ArrayList<>();
		List<BlockState> before = new ArrayList<>();
		int[] placed = {0};
		for (int stage = 0; stage < bp.stageCount(); stage++) {
			int n = bp.stepCount(stage);
			for (int i = 0; i < n; i++) {
				bp.step(stage, i, (x, y, z, state, tag) -> {
					BlockPos p = new BlockPos(x, y, z);
					positions.add(p);
					before.add(level.getBlockState(p));
					level.setBlock(p, state, FLAGS);
					if (tag != null) {
						BlockEntity be = level.getBlockEntity(p);
						if (be != null) {
							be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
							be.setChanged();
							level.sendBlockUpdated(p, state, state, Block.UPDATE_CLIENTS);
						}
					}
					placed[0]++;
				});
			}
		}
		UNDO.put(player.getUUID(), new Undo(level, positions, before));
		long t2 = System.nanoTime();

		String again = String.format(Locale.ROOT, "/hearth build %s %s %d %d %d%s", info.id(), styleId.getPath(), seed, frontage, depth,
			hasStoreys ? " " + IntegerArgumentType.getInteger(c, "storeys") : "");
		int steps = placed[0];
		double genMs = (t1 - t0) / 1e6;
		double placeMs = (t2 - t1) / 1e6;
		Component done = Component.translatable("hearth.build.command.built", info.id().toString(), styleId.toString(), steps,
			String.format(Locale.ROOT, "%.1f", genMs), String.format(Locale.ROOT, "%.1f", placeMs));
		source.sendSuccess(() -> done, false);
		source.sendSuccess(() -> Component.literal(again).withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GRAY)
			.withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand(again))), false);
		return steps;
	}

	private static int undo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Undo u = UNDO.remove(player.getUUID());
		if (u == null) {
			c.getSource().sendFailure(Component.translatable("hearth.build.command.nothing_to_undo"));
			return 0;
		}
		// in reverse, so a cell set twice ends as it first was
		for (int i = u.positions().size() - 1; i >= 0; i--) {
			u.level().setBlock(u.positions().get(i), u.states().get(i), FLAGS);
		}
		int n = u.positions().size();
		c.getSource().sendSuccess(() -> Component.translatable("hearth.build.command.undone", n), false);
		return n;
	}
}
