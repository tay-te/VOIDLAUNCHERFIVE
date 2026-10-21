package dev.voidmc.hearth.npc.work.field;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcTime;
import dev.voidmc.hearth.npc.embody.SettlementSources;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The charcoal burner's clamp through its burn (docs/LIVING_WORLD_2.md §6.1, §6.3), at every charcoal kiln of an embodied
 * town: while it smoulders a column of smoke stands from its crown (seen from the road), and the clearing shows the
 * burn's day — billets stood ready round the clamp while the stack goes up, charcoal heaped and the ground black with
 * cinders when it is raked out. The burner's work follows the stage ({@link #work}).
 *
 * <p>The clearing's dressing is a handful of ordinary blocks inside the kiln's plot, placed only on air, each recorded
 * before it is placed ({@code hearth:npc/clamps}) and taken down when the stage moves on — only if it is still as it was
 * put — or the next time the town is embodied; nothing of a player's is ever touched.
 */
public final class Clamps {
	/** No neighbour updates, no {@code onPlace}: a dressing never reacts to what is next to it. */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;
	/** Server ticks between passes. */
	static final int PASS_TICKS = 40;
	/** How far a clamp's smoke is sent (it is meant to be seen from the road). */
	static final int SMOKE_RANGE = 192;

	/** One block of a clearing's dressing. */
	record Placed(BlockPos pos, BlockState state, int plot) {
		static final Codec<Placed> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Placed::pos),
			BlockState.CODEC.fieldOf("state").forGetter(Placed::state),
			Codec.INT.fieldOf("plot").forGetter(Placed::plot)
		).apply(i, Placed::new));
	}

	/** What stands of each kiln's dressing, and for which stage it was put up. */
	public static final class Ledger {
		static final Codec<Ledger> CODEC = RecordCodecBuilder.create(i -> i.group(
			Placed.CODEC.listOf().optionalFieldOf("placed", List.of()).forGetter(l -> l.placed),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("stages", java.util.Map.of()).forGetter(l -> l.stages)
		).apply(i, (placed, stages) -> {
			Ledger l = new Ledger();
			l.placed.addAll(placed);
			l.stages.putAll(stages);
			return l;
		}));

		final List<Placed> placed = new ArrayList<>();
		/** Plot id → the stage its dressing is for. */
		final java.util.Map<String, String> stages = new java.util.HashMap<>();
	}

	public static final HearthDataKey<Ledger> KEY = HearthDataKey.register(Hearth.id("npc/clamps"), HearthDataKey.Scope.SETTLEMENT, Ledger.CODEC, Ledger::new);

	private static long ticks;

	private Clamps() {
	}

	static void touch() {
	}

	/** The stage of a kiln's clamp today. */
	public static ClampCycle.Stage stage(Settlement s, int plot) {
		return ClampCycle.stage(ClampCycle.active(s, plot), NpcTime.today(), plot, FieldWork.config().clamps().cycleDays());
	}

	/** From the field work tick: smoke and dressings at the embodied towns' kilns. */
	static void tick(MinecraftServer server) {
		if (++ticks % PASS_TICKS != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			List<ServerPlayer> players = level.players();
			if (players.isEmpty()) {
				continue;
			}
			for (Settlement s : SettlementSources.embodied(level)) {
				for (PlotView p : s.plots()) {
					if (p.state() != PlotState.BUILT) {
						continue;
					}
					if (p.spec().archetype().equals(RemoteSites.CHARCOAL_KILN)) {
						kiln(level, s, p, players);
					} else if (p.spec().archetype().equals(RemoteSites.LOGGING_CAMP)) {
						camp(level, s, p);
					}
				}
			}
		}
	}

	private static void kiln(ServerLevel level, Settlement s, PlotView p, List<ServerPlayer> players) {
		BlockPos crown = crown(level, s, p.spec());
		if (crown == null) {
			return;
		}
		ClampCycle.Stage stage = stage(s, p.id());
		if (stage == ClampCycle.Stage.BURNING && FieldWork.config().clamps().smoke()) {
			boolean seen = false;
			for (ServerPlayer pl : players) {
				if (pl.blockPosition().distSqr(crown) < (long) SMOKE_RANGE * SMOKE_RANGE) {
					seen = true;
					break;
				}
			}
			if (seen) {
				// a tall signal-smoke column, sent far (the limiter overridden), and the crown's own smoulder
				level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, crown.getX() + 0.5, crown.getY() + 0.6, crown.getZ() + 0.5, 0, 0, 1, 0,
					0.06);
				level.sendParticles(ParticleTypes.SMOKE, crown.getX() + 0.5, crown.getY() + 0.3, crown.getZ() + 0.5, 4, 0.8, 0.1, 0.8, 0.01);
			}
		}
		Ledger ledger = s.data(KEY);
		String key = Integer.toString(p.id());
		String now = stage.name();
		if (!now.equals(ledger.stages.get(key)) && level.isLoaded(crown)) {
			takeDown(level, ledger, p.id());
			dress(level, ledger, p.spec(), p.id(), crown, stage);
			ledger.stages.put(key, now);
			s.markDirty();
		}
	}

	/**
	 * The clamp's crown: the top of the mound in front of the burner's first place at the clamp (the kiln's job sites
	 * face their clamps), or null if the kiln has no burner's place or its chunk is not in.
	 */
	public static @Nullable BlockPos crown(ServerLevel level, Settlement s, PlotSpec spec) {
		for (Poi poi : s.pois(PoiType.JOB_SITE)) {
			if (poi.job() != JobType.CHARCOAL_BURNER || !inside(spec, poi.pos())) {
				continue;
			}
			BlockPos best = null;
			for (int r = 2; r <= 4; r++) {
				BlockPos c = poi.pos().relative(poi.facing(), r);
				if (!level.isLoaded(c)) {
					return null;
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
				if (best == null || top > best.getY()) {
					best = new BlockPos(c.getX(), top, c.getZ());
				}
			}
			return best;
		}
		return null;
	}

	/** The clearing dressed for a stage: billets ready for the stack, or charcoal heaped and cinders when it is raked. */
	private static void dress(ServerLevel level, Ledger ledger, PlotSpec spec, int plot, BlockPos crown, ClampCycle.Stage stage) {
		if (stage == ClampCycle.Stage.COLD || stage == ClampCycle.Stage.BURNING) {
			return;
		}
		List<BlockPos> spots = new ArrayList<>();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int r = 3; r <= 4; r++) {
				BlockPos c = crown.relative(d, r).relative(d.getClockWise(), r == 3 ? 1 : -1);
				if (!level.isLoaded(c)) {
					continue;
				}
				BlockPos g = FieldPlacement.ground(level, c);
				if (inside(spec, g) && level.getBlockState(g).isAir() && level.getBlockState(g.below()).isSolidRender()
					&& level.getBlockState(g.above()).isAir()) {
					spots.add(g);
				}
			}
		}
		int n = 0;
		for (BlockPos g : spots) {
			if (n >= 5) {
				break;
			}
			BlockState state = switch (stage) {
				// billets stood on end, waiting to go on the stack
				case BUILDING -> Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
				// the charcoal heaped, the ground about it black with cinders
				default -> n % 2 == 0 ? Blocks.COAL_BLOCK.defaultBlockState() : Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState();
			};
			ledger.placed.add(new Placed(g, state, plot));
			QuietBlocks.set(level, g, state, FLAGS);
			n++;
		}
	}

	// ---------------------------------------------------------------- the logging camp's timber

	/** Logs in store at which the camp's stack of seasoned timber stands one more course (rows of four). */
	static final int[] TIMBER_STEPS = {1, 24, 80, 200, 400};

	/** How many courses of seasoned timber a camp shows for the town's logs in store (0 to 4). */
	public static int timberCourses(int logs) {
		int n = 0;
		for (int i = 1; i < TIMBER_STEPS.length; i++) {
			if (logs >= TIMBER_STEPS[i]) {
				n = i;
			}
		}
		return logs >= TIMBER_STEPS[0] ? Math.max(1, n) : 0;
	}

	/**
	 * The logging camp's stack of seasoned timber (docs/LIVING_WORLD_2.md §6.1): logs laid in courses beside the foresters'
	 * place, more of them as the town's logs in store grow, fewer as they are drawn down; on air in the camp's yard only.
	 */
	private static void camp(ServerLevel level, Settlement s, PlotView p) {
		int logs = s.stockpile().get(dev.voidmc.hearth.api.Goods.LOGS);
		int courses = timberCourses(logs);
		Ledger ledger = s.data(KEY);
		String key = "camp" + p.id();
		String now = Integer.toString(courses);
		if (now.equals(ledger.stages.get(key))) {
			return;
		}
		if (courses == 0) {
			takeDown(level, ledger, -p.id() - 1);
			ledger.stages.put(key, now);
			s.markDirty();
			return;
		}
		BlockPos base = null;
		Direction run = null;
		for (Poi poi : s.pois(PoiType.JOB_SITE)) {
			if (poi.job() != JobType.FORESTER || !inside(p.spec(), poi.pos()) || !level.isLoaded(poi.pos())) {
				continue;
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos start = poi.pos().relative(d, 2);
				Direction along = d.getClockWise();
				boolean ok = true;
				for (int i = 0; i < 4 && ok; i++) {
					BlockPos c = start.relative(along, i);
					// (never a block read in a chunk not loaded: that would load it on the server thread)
					ok = inside(p.spec(), c) && level.isLoaded(c) && (level.getBlockState(c).isAir() || placedHere(ledger, c, p.id())) && level.getBlockState(c.below()).isSolidRender();
				}
				if (ok) {
					base = start;
					run = along;
					break;
				}
			}
			if (base != null) {
				break;
			}
		}
		if (base == null) {
			return;
		}
		if (!now.equals(BUILDING.get(key(s, p.id())))) {
			// a new height: the old stack comes down, the new one goes up a few logs a pass
			takeDown(level, ledger, -p.id() - 1);
			BUILDING.put(key(s, p.id()), now);
		}
		net.minecraft.world.item.ItemStack log = dev.voidmc.hearth.api.HearthServices.goodsItems().sample(dev.voidmc.hearth.api.Goods.LOGS, s.culture(),
			s.id() + p.id());

		Block logBlock = log.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof RotatedPillarBlock ? bi.getBlock()
			: Blocks.OAK_LOG;
		BlockState state = logBlock.defaultBlockState().setValue(RotatedPillarBlock.AXIS, run.getAxis());
		// the first block set in a pass has cost the server 7-15 ms in the work tour (the rest a few µs each:
		// requests/lwA4.md), so the stack goes up in one pass, not spread over several
		int budget = PLACE_PER_PASS;
		boolean done = true;
		for (int y = 0; y < Math.min(3, courses); y++) {
			// each course a block shorter at each end above the first, as a woodpile is stacked
			int width = y == 0 ? 4 : courses >= 4 ? 4 : 3 - (y - 1);
			for (int i = 0; i < width; i++) {
				BlockPos c = base.relative(run, i + (4 - width) / 2).above(y);
				if (!level.getBlockState(c).isAir()) {
					continue;
				}
				if (budget-- <= 0) {
					done = false;
					continue;
				}
				ledger.placed.add(new Placed(c, state, -p.id() - 1));
				QuietBlocks.set(level, c, state, FLAGS);
			}
		}
		if (done) {
			ledger.stages.put(key, now);
			BUILDING.remove(key(s, p.id()));
		}
		s.markDirty();
	}

	/** Logs laid on a timber stack in one pass, at most (a whole stack is 12). */
	static final int PLACE_PER_PASS = 16;
	/** Stacks being laid (town and plot to the height being built), so a stack is not torn down between its passes. */
	private static final Map<Long, String> BUILDING = new HashMap<>();

	static void clear() {
		BUILDING.clear();
	}

	private static long key(Settlement s, int plot) {
		return s.id() * 4096L + plot;
	}

	private static boolean placedHere(Ledger ledger, BlockPos pos, int plot) {
		for (Placed pl : ledger.placed) {
			if (pl.pos().equals(pos) && pl.plot() == -plot - 1) {
				return true;
			}
		}
		return false;
	}

	/** Takes down a kiln's dressing: only blocks still as they were put up. */
	private static void takeDown(ServerLevel level, Ledger ledger, int plot) {
		ledger.placed.removeIf(pl -> {
			if (pl.plot() != plot) {
				return false;
			}
			if (!level.isLoaded(pl.pos())) {
				return false;
			}
			if (level.getBlockState(pl.pos()).equals(pl.state())) {
				QuietBlocks.set(level, pl.pos(), Blocks.AIR.defaultBlockState(), FLAGS);
			}
			return true;
		});
	}

	private static boolean inside(PlotSpec spec, BlockPos p) {
		BlockPos o = spec.origin();
		return p.getX() >= o.getX() && p.getX() < o.getX() + spec.sizeX() && p.getZ() >= o.getZ() && p.getZ() < o.getZ() + spec.sizeZ();
	}

	// ---------------------------------------------------------------- the burner

	/**
	 * A charcoal burner's next stretch at the clamp by its stage, or null for the bench work of {@code CraftWork} (a cold
	 * clamp): stacking billets, tending the vents of the smouldering mound, raking out the charcoal.
	 */
	public static @Nullable Task work(FolkBrain brain, ServerLevel level, @Nullable BlockPos stand, @Nullable BlockPos facing) {
		PlotView plot = brain.resident.work == dev.voidmc.hearth.api.Resident.NO_PLOT ? null : brain.settlement.plot(brain.resident.work);
		if (plot == null || !plot.spec().archetype().equals(RemoteSites.CHARCOAL_KILN)) {
			return null;
		}
		ClampCycle.Stage stage = stage(brain.settlement, plot.id());
		WorkBarks.maybe(brain, level, "clamp", 6, null);
		return switch (stage) {
			case COLD -> null;
			case BUILDING -> new Tasks.Linger(stand, facing, FolkPose.CRAFT, 300 + brain.random.nextInt(200), new ItemStack(Items.SPRUCE_LOG), ItemStack.EMPTY,
				Tasks.every(40, SoundEvents.WOOD_PLACE, 0.6F, null, true), "stacking billets for the clamp");
			case BURNING -> new Tasks.Linger(stand, facing, FolkPose.TEND, 400 + brain.random.nextInt(300), new ItemStack(Items.IRON_SHOVEL), ItemStack.EMPTY,
				Tasks.every(60, SoundEvents.CAMPFIRE_CRACKLE, 0.7F, (b, lvl, e, at) -> {
					if (at != null) {
						lvl.sendParticles(ParticleTypes.SMOKE, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.01);
					}
				}, false), "tending the clamp's vents");
			case RAKING -> new Tasks.Linger(stand, facing, FolkPose.HAMMER, 300 + brain.random.nextInt(200), new ItemStack(Items.IRON_HOE),
				new ItemStack(Items.CHARCOAL), Tasks.every(30, SoundEvents.GRAVEL_HIT, 0.6F, null, true), "raking out the charcoal");
		};
	}
}
