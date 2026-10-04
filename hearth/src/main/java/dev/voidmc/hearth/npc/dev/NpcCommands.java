package dev.voidmc.hearth.npc.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.embody.SettlementSources;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.person.NpcData;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Schedule;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /hearth npc ...}: dev and admin tools.
 *
 * <pre>
 *   /hearth npc spawn [job] [culture] [child|adult|elder]   a folk here, living in a camp of that culture
 *   /hearth npc sandbox [radius] [culture]                  a village from the beds and workstations built round you
 *   /hearth npc sandbox build cottage|tower|quarry          stake out a building (or a quarry pit) for the sandbox, in front of you
 *   /hearth npc sandbox clear                               remove every sandbox settlement in this dimension
 *   /hearth npc info                                        who the folk you are looking at is and what they are doing
 *   /hearth npc stats                                       embodied folk and settlements
 *   /hearth npc behaviours                                  the behaviours other modules have taught folk, by priority
 *   /hearth npc escort &lt;pos&gt;                                the folk you look at follows you there (NpcService.follow)
 *   /hearth npc station &lt;station&gt;                           sets the station of the folk you look at (to see the dress)
 *   /hearth npc caravan                                     the caravans on the roads
 *   /hearth npc caravan send                                sends a caravan from the nearest town to the next nearest
 * </pre>
 */
public final class NpcCommands {
	private static final SuggestionProvider<CommandSourceStack> JOBS = (c, b) -> SharedSuggestionProvider.suggest(
		java.util.Arrays.stream(JobType.values()).map(JobType::getSerializedName), b);
	private static final SuggestionProvider<CommandSourceStack> CULTURES = (c, b) -> SharedSuggestionProvider.suggest(
		java.util.Arrays.stream(Culture.values()).map(Culture::getSerializedName), b);
	private static final SuggestionProvider<CommandSourceStack> STATIONS = (c, b) -> SharedSuggestionProvider.suggest(
		java.util.Arrays.stream(dev.voidmc.hearth.api.Station.values()).map(dev.voidmc.hearth.api.Station::getSerializedName), b);
	private static final SuggestionProvider<CommandSourceStack> AGES = (c, b) -> SharedSuggestionProvider.suggest(new String[]{"child", "adult", "elder"}, b);

	private NpcCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hearth").then(Commands.literal("npc")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("spawn")
				.executes(c -> spawn(c, JobType.NONE, null, null))
				.then(Commands.argument("job", StringArgumentType.word()).suggests(JOBS)
					.executes(c -> spawn(c, job(c), null, null))
					.then(Commands.argument("culture", StringArgumentType.word()).suggests(CULTURES)
						.executes(c -> spawn(c, job(c), culture(c), null))
						.then(Commands.argument("age", StringArgumentType.word()).suggests(AGES)
							.executes(c -> spawn(c, job(c), culture(c), AgeStage.valueOf(StringArgumentType.getString(c, "age").toUpperCase(Locale.ROOT))))))))
			.then(Commands.literal("sandbox")
				.executes(c -> sandbox(c, 24, Culture.TEMPERATE))
				.then(Commands.literal("clear").executes(NpcCommands::clear))
				.then(Commands.literal("build")
					.then(Commands.literal("cottage").executes(c -> build(c, "cottage")))
					.then(Commands.literal("tower").executes(c -> build(c, "tower")))
					.then(Commands.literal("quarry").executes(c -> build(c, "quarry"))))
				.then(Commands.argument("radius", IntegerArgumentType.integer(4, 48))
					.executes(c -> sandbox(c, IntegerArgumentType.getInteger(c, "radius"), Culture.TEMPERATE))
					.then(Commands.argument("culture", StringArgumentType.word()).suggests(CULTURES)
						.executes(c -> sandbox(c, IntegerArgumentType.getInteger(c, "radius"), culture(c))))))
			.then(Commands.literal("info").executes(NpcCommands::info))
			.then(Commands.literal("stats").executes(NpcCommands::stats))
			.then(Commands.literal("behaviours").executes(NpcCommands::behaviours))
			.then(Commands.literal("escort")
				.then(Commands.argument("pos", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
					.executes(c -> escort(c, net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(c, "pos")))))
			.then(Commands.literal("station")
				.then(Commands.argument("station", StringArgumentType.word()).suggests(STATIONS).executes(NpcCommands::station)))
			.then(Commands.literal("caravan")
				.executes(NpcCommands::caravans)
				.then(Commands.literal("send").executes(NpcCommands::sendCaravan)))));
	}

	private static JobType job(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		String s = StringArgumentType.getString(c, "job");
		for (JobType j : JobType.values()) {
			if (j.getSerializedName().equals(s)) {
				return j;
			}
		}
		throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("No such trade: " + s)).create();
	}

	private static Culture culture(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		String s = StringArgumentType.getString(c, "culture");
		for (Culture cu : Culture.values()) {
			if (cu.getSerializedName().equals(s)) {
				return cu;
			}
		}
		throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("No such culture: " + s)).create();
	}

	private static int spawn(CommandContext<CommandSourceStack> c, JobType job, Culture culture, AgeStage age) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition().relative(player.getDirection(), 2);
		SandboxSettlement camp = NpcSandbox.camp(level, player.blockPosition(), culture != null ? culture : Culture.TEMPERATE);
		Resident r = NpcSandbox.addFolk(camp, at, job, age, level.getRandom());
		Folk folk = NpcModule.embodiment().spawn(level, camp, r);
		if (folk == null) {
			c.getSource().sendFailure(Component.literal("No room to stand there; " + r.fullName() + " will appear when there is."));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Spawned " + r.fullName() + ", " + r.job.getSerializedName() + " of " + camp.name()), false);
		return 1;
	}

	private static int sandbox(CommandContext<CommandSourceStack> c, int radius, Culture culture) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		SandboxSettlement s = NpcSandbox.village(level, player.blockPosition(), radius, culture, level.getRandom());
		long beds = s.residents().stream().filter(r -> NpcData.of(r).bed().isPresent()).count();
		c.getSource().sendSuccess(() -> Component.literal("Sandbox " + s.name() + ": " + s.population() + " folk, " + beds + " with beds. "
			+ "Their day follows the clock: try /time set to see them sleep, work and gather."), false);
		return s.population();
	}

	private static int build(CommandContext<CommandSourceStack> c, String kind) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		SandboxSettlement s = NpcSandbox.nearest(level, player.blockPosition(), 128);
		if (s == null) {
			s = NpcSandbox.camp(level, player.blockPosition(), Culture.TEMPERATE);
		}
		Direction d = player.getDirection();
		BlockPos front = player.blockPosition().relative(d, kind.equals("tower") ? 5 : 6);
		BlockPos origin = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, front)
			.offset(kind.equals("tower") ? -2 : -3, 0, kind.equals("tower") ? -2 : -3);
		var plot = NpcSandbox.build(level, s, origin, kind, level.getRandom());
		String name = s.name();
		c.getSource().sendSuccess(() -> Component.literal("Staked out a " + kind + " (plot " + plot.id() + ") for the builders of " + name + " at "
			+ origin.toShortString()), false);
		return 1;
	}

	private static int clear(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		int n = NpcSandbox.settlements(level).size();
		NpcSandbox.clear(level);
		c.getSource().sendSuccess(() -> Component.literal("Removed " + n + " sandbox settlements."), false);
		return n;
	}

	/** The folk the player is looking at, within 24 blocks, or null. */
	private static Folk lookedAt(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(24));
		Folk target = null;
		double best = Double.MAX_VALUE;
		for (Folk f : player.level().getEntitiesOfClass(Folk.class, new AABB(eye, end).inflate(1))) {
			if (f.getBoundingBox().inflate(0.3).clip(eye, end).isPresent() && f.distanceToSqr(player) < best) {
				target = f;
				best = f.distanceToSqr(player);
			}
		}
		return target;
	}

	private static int behaviours(CommandContext<CommandSourceStack> c) {
		StringBuilder sb = new StringBuilder("Folk behaviours (routine: sleep 90, work 50, meals 40, leisure 20; fleeing 250):");
		for (dev.voidmc.hearth.api.NpcService.FolkBehaviour b : dev.voidmc.hearth.npc.service.Behaviours.all()) {
			sb.append("\n ").append(b.priority()).append("  ").append(b.id());
		}
		int running = 0;
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() != null && f.brain().behaviour() != null) {
				running++;
			}
		}
		int n = running;
		c.getSource().sendSuccess(() -> Component.literal(sb + "\n" + n + " folk are in a behaviour now; "
			+ dev.voidmc.hearth.npc.service.Escorts.active() + " escorts active."), false);
		return dev.voidmc.hearth.npc.service.Behaviours.all().size();
	}

	private static int escort(CommandContext<CommandSourceStack> c, BlockPos destination) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Folk f = lookedAt(player);
		if (f == null || f.brain() == null) {
			c.getSource().sendFailure(Component.literal("Look at a folk."));
			return 0;
		}
		java.util.UUID id = NpcModule.service().follow(player, f.brain().settlement, f.brain().resident, destination);
		if (id == null) {
			c.getSource().sendFailure(Component.literal("They cannot follow you now."));
			return 0;
		}
		String name = f.brain().resident.fullName();
		c.getSource().sendSuccess(() -> Component.literal(name + " follows you to " + destination.toShortString() + "."), false);
		return 1;
	}

	private static int station(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Folk f = lookedAt(player);
		if (f == null || f.brain() == null) {
			c.getSource().sendFailure(Component.literal("Look at a folk."));
			return 0;
		}
		String s = StringArgumentType.getString(c, "station");
		for (dev.voidmc.hearth.api.Station st : dev.voidmc.hearth.api.Station.values()) {
			if (st.getSerializedName().equals(s)) {
				f.brain().resident.station = st;
				f.brain().settlement.markDirty();
				f.setStanding(st, f.office());
				c.getSource().sendSuccess(() -> Component.literal(f.folkName() + " is now " + st.getSerializedName() + "."), false);
				return 1;
			}
		}
		c.getSource().sendFailure(Component.literal("No such station: " + s));
		return 0;
	}

	private static int caravans(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		java.util.List<String> lines = NpcModule.caravans().describe(level.getGameTime());
		c.getSource().sendSuccess(() -> Component.literal(NpcModule.caravans().journeys() + " caravans on the roads, " + NpcModule.caravans().parties()
			+ " met by players" + (lines.isEmpty() ? "." : ":\n " + String.join("\n ", lines))), false);
		return lines.size();
	}

	private static int sendCaravan(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		dev.voidmc.hearth.api.HearthWorld world = dev.voidmc.hearth.api.HearthWorld.of(level);
		if (world == null) {
			c.getSource().sendFailure(Component.literal("No Hearth world here."));
			return 0;
		}
		Settlement from = null;
		Settlement to = null;
		double best = Double.MAX_VALUE;
		double second = Double.MAX_VALUE;
		for (Settlement s : world.settlements()) {
			double d = s.center().distSqr(player.blockPosition());
			if (d < best) {
				to = from;
				second = best;
				from = s;
				best = d;
			} else if (d < second) {
				to = s;
				second = d;
			}
		}
		if (from == null || to == null) {
			c.getSource().sendFailure(Component.literal("Two settlements are needed for a caravan."));
			return 0;
		}
		int[] cargo = new int[dev.voidmc.hearth.api.Goods.VALUES.length];
		cargo[dev.voidmc.hearth.api.Goods.WOOD.ordinal()] = 96;
		cargo[dev.voidmc.hearth.api.Goods.SALT.ordinal()] = 40;
		NpcModule.caravans().depart(world, from, to, cargo);
		Settlement f0 = from;
		Settlement t0 = to;
		c.getSource().sendSuccess(() -> Component.literal("A caravan sets out from " + f0.name() + " for " + t0.name() + "."), false);
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(24));
		Folk target = null;
		double best = Double.MAX_VALUE;
		for (Folk f : player.level().getEntitiesOfClass(Folk.class, new AABB(eye, end).inflate(1))) {
			if (f.getBoundingBox().inflate(0.3).clip(eye, end).isPresent() && f.distanceToSqr(player) < best) {
				target = f;
				best = f.distanceToSqr(player);
			}
		}
		if (target == null || target.brain() == null) {
			c.getSource().sendFailure(Component.literal("Look at a folk."));
			return 0;
		}
		FolkBrain b = target.brain();
		Resident r = b.resident;
		NpcData d = b.data;
		ResidentPlaces p = b.places(player.level());
		Schedule sch = b.schedule();
		String text = r.fullName() + " (" + r.age.getSerializedName() + ", " + (r.female ? "f" : "m") + ") " + r.job.getSerializedName() + " of "
			+ b.settlement.name() + ", " + r.station.getSerializedName() + (target.office() != dev.voidmc.hearth.npc.person.Office.NONE ? ", " + target.office() : "")
			+ "\n traits " + d.traits() + ", mood " + d.mood() + ", friends " + d.friends().size()
			+ "\n now: " + b.describe()
			+ "\n bed " + (p.bed != null ? p.bed.toShortString() : "-") + ", home " + p.home.toShortString() + ", work "
			+ (p.workSite != null ? p.workSite.pos().toShortString() : p.work.toShortString())
			+ "\n day: " + (sch != null ? sch : "-");
		c.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int stats(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		int settlements = SettlementSources.all(level).size();
		int embodied = SettlementSources.embodied(level).size();
		c.getSource().sendSuccess(() -> Component.literal("Hearthfolk: " + NpcModule.embodiment().count() + " folk embodied; " + embodied + " of "
			+ settlements + " settlements embodied here."), false);
		return NpcModule.embodiment().count();
	}
}
