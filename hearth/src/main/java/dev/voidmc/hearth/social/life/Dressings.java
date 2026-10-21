package dev.voidmc.hearth.social.life;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.calendar.Festival;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.calendar.TownEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Market-day stalls and festival dressings: small structures of ordinary blocks set up on free ground round the square
 * while an embodied settlement holds a market or a festival, and taken down when it ends (or the next time the town is
 * embodied, if the chunks were unloaded in between). Only air is ever replaced, and only blocks still as they were put
 * up are taken down, so nothing of a player's is touched. Every block is recorded in the ledger before it is placed.
 */
public final class Dressings {
	/** No neighbour updates and no {@code onPlace} (a dressing never reacts to what is next to it, nor reads the next chunk). */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;

	/**
	 * Told when market stalls go up and come down (docs/LIVING_WORLD_2.md §6.3: lane A4's stall wares lay goods on the
	 * counters). Called on the server thread, with the chunks loaded; keep it cheap. Register during mod init.
	 */
	public interface StallListener {
		/** The stalls of a market just set up (only those with room went up). */
		void up(ServerLevel level, Settlement s, TownEvent market, List<Stall> stalls);

		/**
		 * Blocks set up for an event came down: {@code positions} are their packed positions (a stall's counter among them
		 * when its stall came down), so what was laid on a counter told in {@link #up} can go with it.
		 */
		void down(ServerLevel level, String eventKey, Set<Long> positions);
	}

	private static volatile StallListener[] listeners = new StallListener[0];

	public static synchronized void listen(StallListener listener) {
		StallListener[] next = java.util.Arrays.copyOf(listeners, listeners.length + 1);
		next[next.length - 1] = listener;
		listeners = next;
	}

	/** Events of the daily rhythm (docs/LIVING_WORLD_2.md §2): taken down at their {@code until}, or with everything. */
	public static final String RHYTHM = "rhythm:";

	private Dressings() {
	}

	/**
	 * Puts up laundry lines in households' yards for the wash day (docs/LIVING_WORLD_2.md §2.3): two posts, a string between
	 * them and white and grey linen hanging from it, inside the plot and clear of its doors. Recorded under {@code key}
	 * (which starts with {@link #RHYTHM}) until {@code until}; returns how many lines went up.
	 */
	public static int setUpLaundry(ServerLevel level, Settlement s, SocialLedger ledger, String key, long until, List<PlotView> homes, int most) {
		Palette pal = Palette.of(s.culture());
		Set<Long> taken = new HashSet<>();
		List<BlockPos> doors = Venues.positions(s.pois(PoiType.DOOR));
		List<Part> all = new ArrayList<>();
		int lines = 0;
		int tried = 0;
		for (PlotView home : homes) {
			// a few yards each, and only so many yards: a bounded search, once a wash day
			if (lines >= most || tried++ >= Math.max(3, most * 3)) {
				break;
			}
			dev.voidmc.hearth.api.PlotSpec spec = home.spec();
			BlockPos o = spec.origin();
			long seed = TownCalendar.mix(s.id() * 31 + home.id() ^ key.hashCode());
			// the plots round this one (a part may not stand in them): a short list, not the whole town
			List<PlotView> round = near(s, centre(home), Math.max(spec.sizeX(), spec.sizeZ()) + 12, 16);
			List<BlockPos> nearDoors = new ArrayList<>();
			for (BlockPos d : doors) {
				if (d.getX() >= o.getX() - 8 && d.getX() <= o.getX() + spec.sizeX() + 8 && d.getZ() >= o.getZ() - 8 && d.getZ() <= o.getZ() + spec.sizeZ() + 8) {
					nearDoors.add(d);
				}
			}
			int tries = 0;
			for (int k = 0; k < 32 && tries < 32; k++, tries++) {
				long h = TownCalendar.mix(seed + k);
				Direction along = (h & 1) == 0 ? Direction.EAST : Direction.SOUTH;
				int span = 3;
				// in the yard: the plot and a few blocks round it, on open ground of earth or grass (not a street, not another plot)
				BlockPos c = o.offset(-3 + (int) Math.floorMod(h >>> 8, (long) (spec.sizeX() + 6)), 0, -3 + (int) Math.floorMod(h >>> 24, (long) (spec.sizeZ() + 6)));
				if (!level.isLoaded(c)) {
					continue;
				}
				BlockPos ground = Venues.standable(level, c.atY(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					c.getX(), c.getZ())));
				if (ground == null || !level.canSeeSky(ground)) {
					LAUNDRY_MISSES.merge("no open ground", 1, Integer::sum);
					continue;
				}
				if (!level.isLoaded(ground.relative(along, span))) {
					continue; // (never reads into the next chunk unless it is there)
				}
				// the ground along the line is level earth or grass with air over it (a yard, not a street or a drop)
				boolean level0 = true;
				for (int i = 0; i <= span; i++) {
					BlockPos under = ground.relative(along, i);
					BlockState soil = level.getBlockState(under.below());
					BlockState cell = level.getBlockState(under);
					boolean open = cell.isAir() || cell.is(Blocks.SNOW) || cell.is(Blocks.SHORT_GRASS) || cell.is(Blocks.FERN);
					if (!open || !soil.is(net.minecraft.tags.BlockTags.DIRT) && !soil.is(Blocks.SAND)) {
						level0 = false;
						break;
					}
				}
				if (!level0) {
					LAUNDRY_MISSES.merge("not earth", 1, Integer::sum);
					continue;
				}
				List<Part> parts = laundry(ground, along, span, pal);
				boolean yard = true;
				for (Part p : parts) {
					for (PlotView other : round) {
						if (other.id() != home.id() && !other.spec().archetype().getPath().startsWith("infra/") && Venues.inside(other.spec(), p.pos)) {
							yard = false;
							break;
						}
					}
				}
				if (!yard) {
					LAUNDRY_MISSES.merge("in another plot", 1, Integer::sum);
					continue;
				}
				if (!clear(level, parts, taken, nearDoors, Venues.square(s), ground, true)) {
					LAUNDRY_MISSES.merge("not clear", 1, Integer::sum);
					continue;
				}
				reserve(taken, parts);
				all.addAll(parts);
				lines++;
				break;
			}
		}
		place(level, ledger, key, until, all);
		return lines;
	}

	/** Why yards had no room for a line (counts, for the rhythm's status). */
	public static final java.util.Map<String, Integer> LAUNDRY_MISSES = new java.util.concurrent.ConcurrentHashMap<>();

	/** A line {@code span} long: posts three high at both ends, string along the top, linen hanging under it. */
	static List<Part> laundry(BlockPos g, Direction along, int span, Palette pal) {
		List<Part> parts = new ArrayList<>();
		BlockPos end = g.relative(along, span);
		for (int dy = 0; dy < 3; dy++) {
			parts.add(new Part(g.above(dy), pal.fence()));
			parts.add(new Part(end.above(dy), pal.fence()));
		}
		boolean x = along.getAxis() == Direction.Axis.X;
		for (int i = 1; i < span; i++) {
			BlockPos p = g.relative(along, i);
			parts.add(new Part(p.above(2), Blocks.TRIPWIRE.defaultBlockState()
				.setValue(net.minecraft.world.level.block.TripWireBlock.EAST, x).setValue(net.minecraft.world.level.block.TripWireBlock.WEST, x)
				.setValue(net.minecraft.world.level.block.TripWireBlock.NORTH, !x).setValue(net.minecraft.world.level.block.TripWireBlock.SOUTH, !x)));
			// sheets and shirts hung over the line: white and undyed (grey) linen
			parts.add(new Part(p.above(), Blocks.WOOL.pick(i % 2 == 1 ? net.minecraft.world.item.DyeColor.WHITE : net.minecraft.world.item.DyeColor.LIGHT_GRAY)
				.defaultBlockState()));
		}
		return parts;
	}

	/**
	 * Dresses plots' fronts with one of the build module's dressings ({@code winter}: snow on the ledges, in the cold
	 * cultures) for the rhythm: recorded under {@code key} until {@code until}.
	 */
	public static int dressPlots(ServerLevel level, Settlement s, SocialLedger ledger, String key, long until, String kind, List<PlotView> plots) {
		TownEvent e = new TownEvent(key, TownEvent.Kind.FESTIVAL, until - 1, until, null, List.of(), List.of(), List.of());
		List<Part> parts = fronts(level, s, new HashSet<>(), e, kind, plots);
		place(level, ledger, key, until, parts);
		return parts.size();
	}

	/** The plots nearest a point (built, not roads), for the rhythm's dressings. */
	public static List<PlotView> plotsNear(Settlement s, BlockPos at, int radius, int most) {
		return near(s, at, radius, most);
	}

	/** A stall as set up: where its keeper stands and where customers stand. */
	public record Stall(BlockPos keeper, BlockPos counter, BlockPos customer, int index) {
	}

	/** One block of a dressing, in placing order; {@code tag} is block-entity data (a banner's pattern) or null. */
	record Part(BlockPos pos, BlockState state, net.minecraft.nbt.@Nullable CompoundTag tag) {
		Part(BlockPos pos, BlockState state) {
			this(pos, state, null);
		}
	}

	/** Whether anything is standing for this event. */
	public static boolean isUp(SocialLedger ledger, String event) {
		for (SocialLedger.Dressing d : ledger.dressings) {
			if (d.event().equals(event)) {
				return true;
			}
		}
		return false;
	}

	/** Sets up market stalls round the square; returns the stalls (empty if there was no room or the chunks are not loaded). */
	public static List<Stall> setUpMarket(ServerLevel level, Settlement s, SocialLedger ledger, TownEvent e, BlockPos anchor, int count) {
		List<Stall> stalls = new ArrayList<>();
		if (count <= 0 || !level.isLoaded(anchor)) {
			return stalls;
		}
		Palette pal = Palette.of(s.culture());
		Set<Long> taken = new HashSet<>();
		List<BlockPos> doors = Venues.positions(s.pois(PoiType.DOOR));
		List<Part> all = new ArrayList<>();
		for (BlockPos c : candidates(anchor, 4, 11, TownCalendar.mix(s.id() ^ e.key().hashCode()))) {
			if (stalls.size() >= count) {
				break;
			}
			BlockPos ground = Venues.standable(level, c);
			if (ground == null) {
				continue;
			}
			Direction f = facing(ground, anchor);
			Direction r = f.getClockWise();
			List<Part> parts = stall(ground, f, r, pal, stalls.size());
			BlockPos keeper = ground.relative(f.getOpposite());
			BlockPos customer = ground.relative(f);
			if (!clear(level, parts, taken, doors, anchor, ground) || !open(level, keeper) || Venues.standable(level, customer) == null) {
				continue;
			}
			reserve(taken, parts);
			all.addAll(parts);
			stalls.add(new Stall(keeper, ground, customer, stalls.size()));
		}
		// the houses and shops round the square put out their awnings and wares too
		all.addAll(fronts(level, s, taken, e, "market", near(s, anchor, 32, 14)));
		place(level, ledger, e, all);
		if (!stalls.isEmpty()) {
			for (StallListener l : listeners) {
				l.up(level, s, e, List.copyOf(stalls));
			}
		}
		return stalls;
	}

	/**
	 * Dresses households' houses for an event: the couple's and their families' for a wedding ({@code wedding}), the
	 * bereaved house and its neighbours for a funeral ({@code mourning}). Recorded and taken down like the square's
	 * dressings.
	 */
	public static void dressHomes(ServerLevel level, Settlement s, SocialLedger ledger, TownEvent e, String kind, boolean neighbours) {
		java.util.Set<Integer> homes = new java.util.LinkedHashSet<>();
		java.util.Set<UUID> who = new java.util.LinkedHashSet<>(e.principals());
		who.addAll(e.family());
		for (dev.voidmc.hearth.api.Resident r : s.residents()) {
			if (who.contains(r.id) && r.home != dev.voidmc.hearth.api.Resident.NO_PLOT) {
				homes.add(r.home);
			}
		}
		List<PlotView> plots = new ArrayList<>();
		for (int id : homes) {
			PlotView p = s.plot(id);
			if (p != null && p.state() == dev.voidmc.hearth.api.PlotState.BUILT) {
				plots.add(p);
				if (neighbours) {
					BlockPos c = centre(p);
					for (PlotView q : near(s, c, 14, 3)) {
						if (!plots.contains(q)) {
							plots.add(q);
						}
					}
				}
			}
		}
		place(level, ledger, e, fronts(level, s, new HashSet<>(), e, kind, plots));
	}

	/**
	 * What the build module puts up on these plots' fronts for the day (BlueprintService#dressing: awnings and wares,
	 * garlands, banners, ribbons, black cloth), each plot's whole dressing or none of it: every block in a loaded chunk, in
	 * air, clear of what else is going up.
	 */
	static List<Part> fronts(ServerLevel level, Settlement s, Set<Long> taken, TownEvent e, String kind, List<PlotView> plots) {
		List<Part> out = new ArrayList<>();
		if (plots.isEmpty() || !dev.voidmc.hearth.api.HearthServices.hasBlueprints()) {
			return out;
		}
		dev.voidmc.hearth.api.HearthWorld world = dev.voidmc.hearth.api.HearthWorld.of(level);
		if (world == null) {
			return out;
		}
		dev.voidmc.hearth.api.BlueprintService bs = dev.voidmc.hearth.api.HearthServices.blueprints();
		dev.voidmc.hearth.api.TerrainProbe terrain = world.terrain();
		long day = Math.floorDiv(e.start(), 24000L);
		for (PlotView p : plots) {
			List<dev.voidmc.hearth.api.BlueprintService.Dressing> steps;
			try {
				steps = bs.dressing(p.spec(), terrain, kind, day);
			} catch (RuntimeException ex) {
				continue;
			}
			List<Part> parts = new ArrayList<>(steps.size());
			boolean ok = !steps.isEmpty();
			for (var st : steps) {
				BlockPos pos = st.pos();
				if (!level.isLoaded(pos) || taken.contains(pos.asLong()) || !level.getBlockState(pos).isAir()) {
					ok = false;
					break;
				}
				parts.add(new Part(pos, st.state(), st.tag()));
			}
			if (!ok) {
				continue;
			}
			for (Part part : parts) {
				taken.add(part.pos().asLong());
			}
			out.addAll(parts);
		}
		return out;
	}

	/** Built plots (not roads) nearest a point, within a radius, the nearest first. */
	static List<PlotView> near(Settlement s, BlockPos at, int radius, int most) {
		List<PlotView> out = new ArrayList<>();
		double r2 = (double) radius * radius;
		for (PlotView p : s.plots()) {
			if (p.state() == dev.voidmc.hearth.api.PlotState.BUILT && !p.spec().archetype().getPath().startsWith("infra/")
				&& centre(p).distSqr(at) <= r2) {
				out.add(p);
			}
		}
		out.sort(java.util.Comparator.comparingDouble(p -> centre(p).distSqr(at)));
		return out.size() > most ? new ArrayList<>(out.subList(0, most)) : out;
	}

	private static BlockPos centre(PlotView p) {
		var o = p.spec().origin();
		return new BlockPos(o.getX() + p.spec().sizeX() / 2, o.getY(), o.getZ() + p.spec().sizeZ() / 2);
	}

	/** Sets up a festival's dressings round the square. */
	public static void setUpFestival(ServerLevel level, Settlement s, SocialLedger ledger, TownEvent e, BlockPos anchor, int poles) {
		Festival f = e.festival();
		if (f == null || !level.isLoaded(anchor)) {
			return;
		}
		Palette pal = Palette.of(s.culture());
		Set<Long> taken = new HashSet<>();
		List<BlockPos> doors = Venues.positions(s.pois(PoiType.DOOR));
		List<Part> all = new ArrayList<>();
		int placed = 0;
		int tables = 0;
		boolean fire = f.dressing != Festival.Dressing.BONFIRE;
		for (BlockPos c : candidates(anchor, 3, 10, TownCalendar.mix(s.id() * 7 + e.key().hashCode()))) {
			if (placed >= poles && tables >= 2 && fire) {
				break;
			}
			BlockPos ground = Venues.standable(level, c);
			if (ground == null) {
				continue;
			}
			List<Part> parts;
			Direction face = facing(ground, anchor);
			if (!fire && ground.distSqr(anchor) <= 25) {
				parts = List.of(new Part(ground, Blocks.CAMPFIRE.defaultBlockState()));
			} else if (tables < 2 && ground.distSqr(anchor) >= 25) {
				parts = table(ground, face.getClockWise(), pal, tables);
			} else if (placed < poles) {
				parts = switch (f.dressing) {
					case HAY -> placed % 2 == 0 ? hay(ground) : pole(ground, pal, placed);
					case LANTERNS -> lanternPost(ground, pal);
					default -> pole(ground, pal, placed);
				};
			} else {
				continue;
			}
			if (!clear(level, parts, taken, doors, anchor, ground)) {
				continue;
			}
			reserve(taken, parts);
			all.addAll(parts);
			if (parts.size() == 1 && parts.get(0).state().is(Blocks.CAMPFIRE)) {
				fire = true;
			} else if (parts.size() == 6) {
				tables++;
			} else {
				placed++;
			}
		}
		// and the town dresses its fronts: garlands over the doors, banners, lanterns under the eaves
		all.addAll(fronts(level, s, taken, e, "festival", near(s, anchor, 96, 40)));
		place(level, ledger, e, all);
	}

	/** Takes down what was set up for events that have ended (or every dressing, with {@code all}), where loaded. */
	public static int takeDown(ServerLevel level, SocialLedger ledger, long now, boolean all, @Nullable Set<String> keep) {
		int removed = 0;
		java.util.Map<String, Set<Long>> gone = listeners.length == 0 ? null : new java.util.HashMap<>();
		for (int i = ledger.dressings.size() - 1; i >= 0; i--) {
			SocialLedger.Dressing d = ledger.dressings.get(i);
			// the rhythm's dressings (laundry lines, winter) are kept to their own hour, not to the day's agenda
			boolean due = all || d.until() <= now || keep != null && !keep.contains(d.event()) && !d.event().startsWith(RHYTHM);
			if (!due) {
				continue;
			}
			BlockPos pos = BlockPos.of(d.pos());
			if (!level.isLoaded(pos)) {
				continue; // taken down the next time the town is embodied
			}
			BlockState state = level.getBlockState(pos);
			if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(d.block())) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
			}
			ledger.dressings.remove(i);
			removed++;
			if (gone != null) {
				gone.computeIfAbsent(d.event(), k -> new HashSet<>()).add(d.pos());
			}
		}
		if (gone != null) {
			for (java.util.Map.Entry<String, Set<Long>> e : gone.entrySet()) {
				for (StallListener l : listeners) {
					l.down(level, e.getKey(), e.getValue());
				}
			}
		}
		return removed;
	}

	/** Takes down everything set up for one event, where loaded. */
	public static int takeDownEvent(ServerLevel level, SocialLedger ledger, String event) {
		Set<String> others = new HashSet<>();
		for (SocialLedger.Dressing d : ledger.dressings) {
			if (!d.event().equals(event)) {
				others.add(d.event());
			}
		}
		return takeDown(level, ledger, Long.MIN_VALUE, false, others);
	}

	private static void place(ServerLevel level, SocialLedger ledger, TownEvent e, List<Part> parts) {
		place(level, ledger, e.key(), e.end(), parts);
	}

	private static void place(ServerLevel level, SocialLedger ledger, String key, long until, List<Part> parts) {
		for (Part p : parts) {
			ledger.dressings.add(new SocialLedger.Dressing(p.pos.asLong(), BuiltInRegistries.BLOCK.getKey(p.state.getBlock()).toString(), key, until));
		}
		for (Part p : parts) {
			level.setBlock(p.pos, p.state, FLAGS);
			if (p.tag != null) {
				net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(p.pos);
				if (be != null) {
					be.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
						level.registryAccess(), p.tag));
					// (not be.setChanged(): its comparator update reads the neighbours, which may be in a chunk not loaded)
					level.blockEntityChanged(p.pos);
				}
			}
		}
	}

	/** Every cell of the parts is air with nothing standing in it, the ground under each column is sturdy and level. */
	private static boolean clear(ServerLevel level, List<Part> parts, Set<Long> taken, List<BlockPos> doors, BlockPos anchor, BlockPos ground) {
		return clear(level, parts, taken, doors, anchor, ground, false);
	}

	/**
	 * As above; {@code soft}: a yard's thin snow and short grass at the foot of a post may give way to it (laundry; gone
	 * with the post when it comes down, as trodden snow and grass would be).
	 */
	private static boolean clear(ServerLevel level, List<Part> parts, Set<Long> taken, List<BlockPos> doors, BlockPos anchor, BlockPos ground, boolean soft) {
		int y = ground.getY();
		for (Part p : parts) {
			BlockPos pos = p.pos;
			if (!level.isLoaded(pos) || taken.contains(pos.asLong())) {
				return false;
			}
			BlockState here = level.getBlockState(pos);
			if (!here.isAir() && !(soft && pos.getY() == y && (here.is(Blocks.SNOW) || here.is(Blocks.SHORT_GRASS) || here.is(Blocks.FERN)))) {
				return false;
			}
			if (Math.abs(pos.getX() - anchor.getX()) <= 2 && Math.abs(pos.getZ() - anchor.getZ()) <= 2) {
				return false; // keep the heart of the square open
			}
			for (BlockPos d : doors) {
				if (Math.abs(d.getX() - pos.getX()) <= 2 && Math.abs(d.getZ() - pos.getZ()) <= 2 && Math.abs(d.getY() - pos.getY()) <= 3) {
					return false;
				}
			}
			if (pos.getY() == y) {
				BlockPos below = pos.below();
				if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || taken.contains(below.asLong())) {
					return false;
				}
			}
		}
		// room for the keeper and the customer of a stall, and nobody standing where blocks go
		AABB box = null;
		for (Part p : parts) {
			AABB b = new AABB(p.pos);
			box = box == null ? b : box.minmax(b);
		}
		return box == null || level.getEntities((net.minecraft.world.entity.Entity) null, box.inflate(0.2)).isEmpty();
	}

	/** Two blocks of open air at {@code p} on sturdy ground (the keeper's place inside a stall). */
	private static boolean open(ServerLevel level, BlockPos p) {
		return level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
			&& level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
	}

	private static void reserve(Set<Long> taken, List<Part> parts) {
		for (Part p : parts) {
			taken.add(p.pos.asLong());
			// a one-block margin round each part so dressings never touch
			for (Direction d : Direction.Plane.HORIZONTAL) {
				taken.add(p.pos.relative(d).asLong());
			}
		}
	}

	/** Ground positions on rings round the anchor, in a seeded order. */
	static List<BlockPos> candidates(BlockPos anchor, int minR, int maxR, long seed) {
		List<BlockPos> out = new ArrayList<>();
		int turn = (int) Math.floorMod(seed, 360L);
		for (int r = minR; r <= maxR; r += 2) {
			int steps = Math.max(8, r * 3);
			for (int k = 0; k < steps; k++) {
				double a = Math.toRadians(turn + 360.0 * k / steps);
				out.add(anchor.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)));
			}
		}
		return out;
	}

	/** The horizontal direction from {@code from} toward {@code to}. */
	static Direction facing(BlockPos from, BlockPos to) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/**
	 * A stall three wide and two deep: posts at the corners, a counter in the middle of the front with wares on it, a
	 * striped canopy over all, the keeper's place behind the counter.
	 */
	static List<Part> stall(BlockPos c, Direction f, Direction r, Palette pal, int index) {
		List<Part> parts = new ArrayList<>();
		BlockPos back = c.relative(f.getOpposite());
		BlockState fence = pal.fence();
		for (int dy = 0; dy < 2; dy++) {
			parts.add(new Part(c.relative(r.getOpposite()).above(dy), fence));
			parts.add(new Part(c.relative(r).above(dy), fence));
			parts.add(new Part(back.relative(r.getOpposite()).above(dy), fence));
			parts.add(new Part(back.relative(r).above(dy), fence));
		}
		parts.add(new Part(c, pal.counter(r.getAxis())));
		parts.add(new Part(c.above(), WARES[Math.floorMod(index, WARES.length)].get()));
		for (int side = -1; side <= 1; side++) {
			for (BlockPos row : new BlockPos[] {c, back}) {
				parts.add(new Part(row.relative(r, side).above(2), (side & 1) == 0 ? pal.wool() : pal.accent()));
			}
		}
		return parts;
	}

	static List<Part> pole(BlockPos g, Palette pal, int index) {
		return List.of(new Part(g, pal.fence()), new Part(g.above(), pal.fence()), new Part(g.above(2), index % 2 == 0 ? pal.wool() : pal.accent()),
			new Part(g.above(3), Blocks.LANTERN.defaultBlockState()));
	}

	static List<Part> hay(BlockPos g) {
		return List.of(new Part(g, Blocks.HAY_BLOCK.defaultBlockState()), new Part(g.above(), Blocks.JACK_O_LANTERN.defaultBlockState()));
	}

	static List<Part> lanternPost(BlockPos g, Palette pal) {
		return List.of(new Part(g, pal.fence()), new Part(g.above(), pal.fence()), new Part(g.above(2), Blocks.LANTERN.defaultBlockState()));
	}

	/** A trestle table three long: posts topped with cloth. */
	static List<Part> table(BlockPos g, Direction along, Palette pal, int index) {
		List<Part> parts = new ArrayList<>();
		for (int i = -1; i <= 1; i++) {
			BlockPos p = g.relative(along, i);
			parts.add(new Part(p, pal.fence()));
			parts.add(new Part(p.above(), pal.carpet()));
		}
		return parts;
	}

	private static final java.util.function.Supplier<BlockState>[] WARES = wares();

	@SuppressWarnings("unchecked")
	private static java.util.function.Supplier<BlockState>[] wares() {
		return new java.util.function.Supplier[] {
			() -> Blocks.MELON.defaultBlockState(),
			() -> Blocks.CAKE.defaultBlockState(),
			() -> Blocks.PUMPKIN.defaultBlockState(),
			() -> Blocks.POTTED_RED_TULIP.defaultBlockState(),
			() -> Blocks.LANTERN.defaultBlockState(),
			() -> Blocks.POTTED_OXEYE_DAISY.defaultBlockState()
		};
	}

	/** A culture's colours and wood for its dressings. */
	record Palette(String wood, String colour, String accentColour) {
		static Palette of(Culture c) {
			return switch (c) {
				case TEMPERATE -> new Palette("oak", "red", "white");
				case WISTERIA -> new Palette("cherry", "purple", "white");
				case NORTHERN -> new Palette("spruce", "blue", "white");
				case ALPINE -> new Palette("spruce", "red", "white");
				case TUNDRA -> new Palette("spruce", "light_blue", "white");
				case MOORLAND -> new Palette("dark_oak", "green", "yellow");
				case MARITIME -> new Palette("birch", "blue", "white");
				case TROPICAL -> new Palette("jungle", "orange", "lime");
				case DESERT -> new Palette("acacia", "yellow", "red");
				case MEDITERRANEAN -> new Palette("birch", "blue", "white");
				case STEPPE -> new Palette("acacia", "red", "yellow");
				case KARST -> new Palette("bamboo", "red", "yellow");
				case BAYOU -> new Palette("mangrove", "green", "purple");
				case MESA -> new Palette("acacia", "orange", "red");
			};
		}

		BlockState fence() {
			return block(this.wood + "_fence", Blocks.OAK_FENCE);
		}

		BlockState counter(Direction.Axis axis) {
			BlockState s = this.wood.equals("bamboo") ? block("stripped_bamboo_block", Blocks.STRIPPED_OAK_LOG)
				: block("stripped_" + this.wood + "_log", Blocks.STRIPPED_OAK_LOG);
			return s.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)
				? s.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, axis) : s;
		}

		BlockState wool() {
			return block(this.colour + "_wool", Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.RED));
		}

		BlockState accent() {
			return block(this.accentColour + "_wool", Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE));
		}

		BlockState carpet() {
			return block(this.colour + "_carpet", Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.RED));
		}

		private static BlockState block(String path, Block fallback) {
			return BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(path)).orElse(fallback).defaultBlockState();
		}
	}
}
