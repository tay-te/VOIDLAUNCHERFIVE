package dev.voidmc.hearth.social.life;

import dev.voidmc.hearth.api.Culture;
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
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private Dressings() {
	}

	/** A stall as set up: where its keeper stands and where customers stand. */
	public record Stall(BlockPos keeper, BlockPos counter, BlockPos customer, int index) {
	}

	/** One block of a dressing, in placing order. */
	record Part(BlockPos pos, BlockState state) {
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
		place(level, ledger, e, all);
		return stalls;
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
		place(level, ledger, e, all);
	}

	/** Takes down what was set up for events that have ended (or every dressing, with {@code all}), where loaded. */
	public static int takeDown(ServerLevel level, SocialLedger ledger, long now, boolean all, @Nullable Set<String> keep) {
		int removed = 0;
		for (int i = ledger.dressings.size() - 1; i >= 0; i--) {
			SocialLedger.Dressing d = ledger.dressings.get(i);
			boolean due = all || d.until() <= now || keep != null && !keep.contains(d.event());
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
		for (Part p : parts) {
			ledger.dressings.add(new SocialLedger.Dressing(p.pos.asLong(), BuiltInRegistries.BLOCK.getKey(p.state.getBlock()).toString(), e.key(),
				e.end()));
		}
		for (Part p : parts) {
			level.setBlock(p.pos, p.state, FLAGS);
		}
	}

	/** Every cell of the parts is air with nothing standing in it, the ground under each column is sturdy and level. */
	private static boolean clear(ServerLevel level, List<Part> parts, Set<Long> taken, List<BlockPos> doors, BlockPos anchor, BlockPos ground) {
		int y = ground.getY();
		for (Part p : parts) {
			BlockPos pos = p.pos;
			if (!level.isLoaded(pos) || taken.contains(pos.asLong()) || !level.getBlockState(pos).isAir()) {
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
