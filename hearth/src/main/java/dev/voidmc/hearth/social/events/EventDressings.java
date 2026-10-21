package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.calendar.TownEvent;
import dev.voidmc.hearth.social.life.Dressings;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The town events' dressings (docs/LIVING_WORLD_2.md §3.3), through the social ledger like the market's and the
 * festivals' (only air is ever replaced, every block is recorded first, and only blocks still as they were put up are
 * taken down): the fair's extra stalls, the archery targets at 20, 30 and 40 blocks, the melee's ring of fences, a fresh
 * grave with candles kept seven days, black cloth on the house door seven days, the bough on a finished ridge, a cart's
 * spilled load at the gate, the horse fair's pen. Plus the particles a scene shows (smoke and flame, the juggler's
 * apples, the rotten vegetables).
 */
public final class EventDressings {
	/** Kept past their scene: a grave and the mourning cloth seven days, the bough a day. */
	static final Set<String> LASTING = Set.of("grave", "mourning", "bough");

	private EventDressings() {
	}

	static void dress(SceneRun run, Settlement s, String kind, BlockPos at) {
		ServerLevel level = run.level();
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		String key = run.key() + ":" + kind;
		if (Dressings.isUp(ledger, key) || !level.isLoaded(at)) {
			return;
		}
		long end = run.event.end();
		boolean up = switch (kind) {
			case "fair_stalls" -> {
				int count = Math.max(4, run.event.args().getIntOr("stalls", 6));
				TownEvent market = new TownEvent(key, TownEvent.Kind.MARKET, run.event.start(), end, null, List.of(), List.of(), List.of());
				List<Dressings.Stall> stalls = Dressings.setUpMarket(level, s, ledger, market, at, Math.min(16, count));
				run.put("stalls", stalls.size());
				yield !stalls.isEmpty();
			}
			case "targets" -> targets(run, s, ledger, key, at, end);
			case "ring" -> ring(level, s, ledger, key, at, end, 5) > 0;
			case "grave" -> grave(level, s, ledger, key, at, end + 7L * SocialTime.DAY_TICKS, run.event.seed());
			case "mourning" -> {
				PlotView home = SceneVenues.home(s, run.event.principals());
				yield home != null && Dressings.dressPlots(level, s, ledger, key, end + 7L * SocialTime.DAY_TICKS, "mourning", List.of(home)) > 0;
			}
			case "bough" -> bough(level, s, ledger, key, run.event, end + SocialTime.DAY_TICKS);
			case "cart_spill" -> spill(level, s, ledger, key, at, end, run.event.seed());
			case "pen" -> ring(level, s, ledger, key, at, end, 3) > 0;
			default -> false;
		};
		if (up) {
			run.dressings.add(key);
			s.markDirty();
		}
	}

	static void undress(SceneRun run, Settlement s, String kind) {
		String key = run.key() + ":" + kind;
		if (Dressings.takeDownKey(run.level(), s.data(SocialModule.LEDGER), key) > 0) {
			s.markDirty();
		}
		run.dressings.remove(key);
	}

	/** The scene is over: what it put up comes down, but for the grave, the cloth and the bough, which keep their days. */
	static void endOf(SceneRun run, Settlement s) {
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		int n = 0;
		for (String key : List.copyOf(run.dressings)) {
			String kind = key.substring(key.lastIndexOf(':') + 1);
			if (!LASTING.contains(kind)) {
				n += Dressings.takeDownKey(run.level(), ledger, key);
			}
		}
		run.dressings.clear();
		if (n > 0) {
			s.markDirty();
		}
	}

	// ---------------------------------------------------------------- the pieces

	/** Three target blocks on posts, 20, 30 and 40 blocks down the clearest line from the mark; their places kept for scoring. */
	private static boolean targets(SceneRun run, Settlement s, SocialLedger ledger, String key, BlockPos mark, long until) {
		ServerLevel level = run.level();
		Direction best = null;
		List<BlockPos> bestFeet = null;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			List<BlockPos> feet = new ArrayList<>();
			for (int dist : new int[]{20, 30, 40}) {
				BlockPos p = ground(level, mark.relative(d, dist).offset(d.getClockWise().getStepX() * (dist / 10 - 2), 0,
					d.getClockWise().getStepZ() * (dist / 10 - 2)));
				if (p == null || Math.abs(p.getY() - mark.getY()) > 6) {
					feet = null;
					break;
				}
				feet.add(p);
			}
			if (feet != null) {
				best = d;
				bestFeet = feet;
				break;
			}
		}
		if (best == null) {
			return false;
		}
		List<BlockPos> faces = new ArrayList<>();
		int up = 0;
		for (int i = 0; i < bestFeet.size(); i++) {
			BlockPos f = bestFeet.get(i);
			List<BlockPos> pos = List.of(f, f.above());
			List<BlockState> states = List.of(Blocks.OAK_FENCE.defaultBlockState(), Blocks.TARGET.defaultBlockState());
			if (Dressings.setUp(level, s, ledger, key, until, pos, states, false)) {
				faces.add(f.above());
				up++;
			}
		}
		run.put("targets", faces);
		run.put("range_dir", best);
		return up > 0;
	}

	/** A ring of fence posts round a point, one every other block, with a gap to come in by. */
	static int ring(ServerLevel level, Settlement s, SocialLedger ledger, String key, BlockPos at, long until, int radius) {
		int up = 0;
		int steps = Math.max(8, radius * 6);
		for (int k = 0; k < steps; k++) {
			if (k == 0 || k == 1) {
				continue; // the way in
			}
			double a = Math.PI * 2 * k / steps;
			BlockPos p = ground(level, at.offset((int) Math.round(Math.cos(a) * radius), 0, (int) Math.round(Math.sin(a) * radius)));
			if (p != null && Math.abs(p.getY() - at.getY()) <= 3
				&& Dressings.setUp(level, s, ledger, key, until, List.of(p), List.of(Blocks.SPRUCE_FENCE.defaultBlockState()), false)) {
				up++;
			}
		}
		return up;
	}

	/** A fresh grave: a mound of earth two long and a candle at its head and foot, lit. */
	private static boolean grave(ServerLevel level, Settlement s, SocialLedger ledger, String key, BlockPos at, long until, long seed) {
		for (int attempt = 0; attempt < 10; attempt++) {
			long h = TownCalendar.mix(seed + attempt);
			BlockPos c = at.offset((int) Math.floorMod(h, 7L) - 3, 0, (int) Math.floorMod(h >>> 8, 7L) - 3);
			BlockPos g = ground(level, c);
			if (g == null) {
				continue;
			}
			Direction along = (h & 1) == 0 ? Direction.EAST : Direction.SOUTH;
			BlockState candle = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true);
			List<BlockPos> pos = List.of(g, g.relative(along), g.relative(along.getOpposite()), g.relative(along, 2));
			List<BlockState> states = List.of(Blocks.COARSE_DIRT.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(), candle, candle);
			if (Dressings.setUp(level, s, ledger, key, until, pos, states, false)) {
				return true;
			}
		}
		return false;
	}

	/** A green bough on the ridge of the finished building: on the highest block over its middle. */
	private static boolean bough(ServerLevel level, Settlement s, SocialLedger ledger, String key, Scheduled e, long until) {
		PlotView p = e.plot() < 0 ? null : s.plot(e.plot());
		if (p == null) {
			return false;
		}
		BlockPos mid = Venues.middle(p.spec());
		if (!level.isLoaded(mid)) {
			return false;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mid);
		if (top.getY() <= p.spec().origin().getY() + 2) {
			return false;
		}
		return Dressings.setUp(level, s, ledger, key, until, List.of(top, top.above()),
			List.of(Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true),
				Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true)), false);
	}

	/** A cart's load spilled in the road: hay, a barrel on its side, pumpkins rolled away. */
	private static boolean spill(ServerLevel level, Settlement s, SocialLedger ledger, String key, BlockPos at, long until, long seed) {
		BlockState[] load = {Blocks.HAY_BLOCK.defaultBlockState(), Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING,
			Direction.EAST), Blocks.PUMPKIN.defaultBlockState(), Blocks.HAY_BLOCK.defaultBlockState(), Blocks.MELON.defaultBlockState()};
		int up = 0;
		for (int i = 0; i < load.length; i++) {
			long h = TownCalendar.mix(seed + i * 31L);
			BlockPos g = ground(level, at.offset((int) Math.floorMod(h, 7L) - 3, 0, (int) Math.floorMod(h >>> 9, 7L) - 3));
			if (g != null && Dressings.setUp(level, s, ledger, key, until, List.of(g), List.of(load[i]), false)) {
				up++;
			}
		}
		return up > 0;
	}

	/** The open ground at a column: the first air above the surface, in a loaded chunk, on sturdy footing; or null. */
	static BlockPos ground(ServerLevel level, BlockPos at) {
		if (!level.isLoaded(at)) {
			return null;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
		BlockPos ok = Venues.standable(level, top);
		return ok;
	}

	// ---------------------------------------------------------------- particles

	static void particles(ServerLevel level, String kind, BlockPos at, int count) {
		if (!level.isLoaded(at)) {
			return;
		}
		double x = at.getX() + 0.5;
		double y = at.getY() + 1.2;
		double z = at.getZ() + 0.5;
		ParticleOptions p = switch (kind) {
			case "smoke" -> ParticleTypes.CAMPFIRE_COSY_SMOKE;
			case "flame", "fire_eater" -> ParticleTypes.FLAME;
			case "notes" -> ParticleTypes.NOTE;
			case "hearts" -> ParticleTypes.HEART;
			case "happy" -> ParticleTypes.HAPPY_VILLAGER;
			case "veg" -> new ItemParticleOption(ParticleTypes.ITEM, Items.BEETROOT);
			case "juggle" -> new ItemParticleOption(ParticleTypes.ITEM, Items.APPLE);
			case "dust" -> ParticleTypes.POOF;
			default -> ParticleTypes.CLOUD;
		};
		level.sendParticles(p, x, y, z, count, 0.4, 0.4, 0.4, kind.equals("fire_eater") ? 0.08 : 0.02);
		if (kind.equals("fire_eater")) {
			level.sendParticles(ParticleTypes.LAVA, x, y + 0.6, z, 2, 0.1, 0.1, 0.1, 0.0);
		}
	}
}
