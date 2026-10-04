package dev.voidmc.hearth.build.kit.light;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.States;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * The fittings the lighting pass can put up, each built only where it is sound: hung from something sturdy, standing on
 * something, attached to a wall that can take it, never in anyone's way. Coordinates are the draft's local frame.
 */
final class Fixtures {
	private Fixtures() {
	}

	/** How far below the standing level a ceiling lantern hangs, by the room's height (the lantern's own row). */
	static int hangHeight(int roomHeight) {
		int h = roomHeight <= 4 ? 2 : roomHeight <= 6 ? 3 : roomHeight <= 9 ? 4 : 5;
		return Math.min(h, roomHeight - 1);
	}

	static BlockState chain(Session s) {
		return States.axis(s.m.chain, Direction.Axis.Y);
	}

	static BlockState hung(Session s) {
		return States.lantern(s.m.lantern, true);
	}

	static BlockState standing(Session s) {
		return States.lantern(s.m.lantern, false);
	}

	static BlockState fence(Session s, int x, int y, int z) {
		BlockState f = s.m.bracketWood().fence().at(x, y, z);
		return f == null ? Blocks.OAK_FENCE.defaultBlockState() : f;
	}

	// ------------------------------------------------------------------------------------------- ceilings

	/** The nearest sound ceiling fitting to a point of the room (searching two cells round it). */
	static @Nullable Fixture ceilingNear(Session s, Session.Room r, double fx, double fz, int hang, boolean wheel) {
		int cx = (int) Math.round(fx);
		int cz = (int) Math.round(fz);
		Fixture best = null;
		double bestD = Double.MAX_VALUE;
		for (int dz = -2; dz <= 2; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				int x = cx + dx;
				int z = cz + dz;
				if (x < r.ax || x > r.bx || z < r.az || z > r.bz) {
					continue;
				}
				double dd = (x - fx) * (x - fx) + (z - fz) * (z - fz);
				if ("table".equals(s.surface(x, r.y, z))) {
					// over the table, where a builder hangs it
					dd -= 0.9;
				}
				if (dd >= bestD) {
					continue;
				}
				Fixture f = wheel ? wheel(s, x, r.y, z, hang) : null;
				if (f == null) {
					f = ceiling(s, x, r.y, z, hang);
				}
				if (f != null) {
					best = f;
					bestD = dd;
				}
			}
		}
		return best;
	}

	/** A lantern hung over (x, z) from the ceiling above standing level y, on a chain when the ceiling is high. */
	static @Nullable Fixture ceiling(Session s, int x, int y, int z, int hang) {
		int roof = s.sp.roofY(x, y, z);
		if (roof == Integer.MAX_VALUE || !s.g.contains(x, roof, z)) {
			return null;
		}
		int yl = Math.min(y + hang, roof - 1);
		if (yl < y + 2 || !Session.sturdyBelow(s.g.at(x, roof, z))) {
			return null;
		}
		for (int yy = yl; yy < roof; yy++) {
			if (!s.hungOk(x, yy, z)) {
				return null;
			}
		}
		Fixture f = new Fixture(Fixture.Kind.CEILING, 1.0);
		for (int yy = roof - 1; yy > yl; yy--) {
			f.part(x, yy, z, chain(s));
		}
		f.part(x, yl, z, hung(s));
		return f;
	}

	/**
	 * A wheel chandelier: a chain from the ceiling to a hub with four arms, a lantern hung under each arm (or, in the
	 * north, candles on the arms round a lantern hung under the hub).
	 */
	static @Nullable Fixture wheel(Session s, int x, int y, int z, int hang) {
		int roof = s.sp.roofY(x, y, z);
		if (roof == Integer.MAX_VALUE || !s.g.contains(x, roof, z)) {
			return null;
		}
		int yl = Math.min(y + hang, roof - 2);
		int hub = yl + 1;
		if (yl < y + 3 || !Session.sturdyBelow(s.g.at(x, roof, z))) {
			return null;
		}
		for (int yy = hub; yy < roof; yy++) {
			if (!s.solidOk(x, yy, z)) {
				return null;
			}
		}
		for (Direction dir : Dirs.HORIZONTAL) {
			int ax = x + Dirs.dx(dir);
			int az = z + Dirs.dz(dir);
			if (!s.solidOk(ax, hub, az)) {
				return null;
			}
			if (s.m.candleWheel ? !s.free(ax, hub + 1, az) : !s.hungOk(ax, yl, az)) {
				return null;
			}
		}
		if (s.m.candleWheel && !s.hungOk(x, yl, z)) {
			return null;
		}
		Fixture f = new Fixture(s.m.candleWheel ? Fixture.Kind.CANDLE_WHEEL : Fixture.Kind.WHEEL, 1.6);
		for (int yy = roof - 1; yy > hub; yy--) {
			f.part(x, yy, z, chain(s));
		}
		f.part(x, hub, z, fence(s, x, hub, z));
		for (Direction dir : Dirs.HORIZONTAL) {
			int ax = x + Dirs.dx(dir);
			int az = z + Dirs.dz(dir);
			f.part(ax, hub, az, fence(s, ax, hub, az));
		}
		if (s.m.candleWheel) {
			for (Direction dir : Dirs.HORIZONTAL) {
				int ax = x + Dirs.dx(dir);
				int az = z + Dirs.dz(dir);
				f.part(ax, hub + 1, az, Session.candles(s.m.candle(ax, hub + 1, az), 4));
			}
			f.part(x, yl, z, hung(s));
		} else {
			for (Direction dir : Dirs.HORIZONTAL) {
				f.part(x + Dirs.dx(dir), yl, z + Dirs.dz(dir), hung(s));
			}
		}
		return f;
	}

	// ------------------------------------------------------------------------------------------- the fill

	/**
	 * Candles for a small room's design: on each piece of furniture in or round it (a table, a barrel, a counter, a
	 * shelf), as many as a house of its standing burns (two in a poor one, three in an ordinary one, four in a fine one).
	 */
	static void candleSpots(Session s, Session.Room r, List<Fixture> out) {
		int y = r.y;
		int most = s.d.tier() <= 1 ? 2 : s.d.tier() == 2 ? 3 : 4;
		for (int z = r.az - 1; z <= r.bz + 1; z++) {
			for (int x = r.ax - 1; x <= r.bx + 1; x++) {
				String surface = s.surface(x, y, z);
				if (surface == null || !inOrBeside(s, r, x, z)) {
					continue;
				}
				BlockState above = s.g.at(x, y + 1, z);
				boolean plate = Session.isPlate(above) && s.g.placed(x, y + 1, z);
				if (!plate && !s.free(x, y + 1, z) || s.sp.walkable(x, y + 1, z) && s.sp.reached(x, y + 1, z) && cuts(s, x, y + 1, z)) {
					continue;
				}
				Fixture c = new Fixture(Fixture.Kind.CANDLES, surface.equals("table") ? 0.7 : 0.85);
				c.part(x, y + 1, z, Session.candles(s.m.candle(x, y + 1, z), most), StageKind.INTERIOR);
				out.add(c);
			}
		}
	}

	/** Whether a column is one of the room's cells or next to one. */
	private static boolean inOrBeside(Session s, Session.Room r, int x, int z) {
		for (int[] o : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			int nx = x + o[0];
			int nz = z + o[1];
			if (s.g.contains(nx, r.y, nz) && r.cells.contains(s.g.index(nx, r.y, nz))) {
				return true;
			}
		}
		return false;
	}

	/** Candidates for a room's dark corner at (cx, cz), within {@code radius} of it. */
	static void roomFill(Session s, Session.Room r, int cx, int cz, int radius, List<Fixture> out) {
		int y = r.y;
		boolean corridor = Math.min(r.width(), r.length()) <= 2;
		int hang = hangHeight(r.height);
		// furniture, candles and lamps, over the room's box and its walls' furniture
		for (int z = Math.max(r.az - 1, cz - radius); z <= Math.min(r.bz + 1, cz + radius); z++) {
			for (int x = Math.max(r.ax - 1, cx - radius); x <= Math.min(r.bx + 1, cx + radius); x++) {
				for (int yy = y; yy <= y + 2; yy++) {
					BlockState at = s.g.at(x, yy, z);
					if (Session.unlitCandle(at) && s.g.placed(x, yy, z)) {
						Fixture f = new Fixture(Fixture.Kind.RELIGHT, 0.3);
						f.part(x, yy, z, Session.lit(at), StageKind.INTERIOR);
						out.add(f);
					}
				}
				String surface = s.surface(x, y, z);
				if (surface == null) {
					continue;
				}
				BlockState above = s.g.at(x, y + 1, z);
				boolean plate = Session.isPlate(above) && s.g.placed(x, y + 1, z);
				if (!plate && !s.free(x, y + 1, z)) {
					continue;
				}
				if (s.sp.walkable(x, y + 1, z) && s.sp.reached(x, y + 1, z) && cuts(s, x, y + 1, z)) {
					continue;
				}
				int n = s.m.candleCount(x, y + 1, z, surface.equals("table") ? 2 : 1);
				Fixture c = new Fixture(Fixture.Kind.CANDLES, surface.equals("table") ? 0.7 : 0.85);
				c.part(x, y + 1, z, Session.candles(s.m.candle(x, y + 1, z), n), StageKind.INTERIOR);
				out.add(c);
				Fixture c4 = new Fixture(Fixture.Kind.CANDLES, surface.equals("table") ? 0.8 : 0.95);
				c4.part(x, y + 1, z, Session.candles(s.m.candle(x, y + 1, z), 4), StageKind.INTERIOR);
				out.add(c4);
				if (!plate && !surface.equals("table") && !s.body(x, y + 1, z)) {
					Fixture l = new Fixture(Fixture.Kind.LAMP, 1.1);
					l.part(x, y + 1, z, standing(s), StageKind.INTERIOR);
					out.add(l);
				}
			}
		}
		// ceiling lanterns anywhere in the room, sconces and torches on its walls, light under rugs
		for (int k = 0; k < r.cells.size(); k++) {
			int i = r.cells.getInt(k);
			int x = s.g.x0 + i % s.g.sx;
			int z = s.g.z0 + (i / s.g.sx) % s.g.sz;
			if (Math.abs(x - cx) > radius || Math.abs(z - cz) > radius) {
				continue;
			}
			Fixture ce = ceiling(s, x, y, z, hang);
			if (ce != null) {
				out.add(new Fixture(ce.kind, 1.15).copyFrom(ce));
			}
			for (Direction dir : Dirs.HORIZONTAL) {
				int wx = x + Dirs.dx(dir);
				int wz = z + Dirs.dz(dir);
				Direction out2 = dir.getOpposite();
				Fixture sc = sconce(s, x, y, z, wx, wz, out2, r.height, corridor);
				if (sc != null) {
					if (besideDoor(s, x, y, z, dir)) {
						sc.bonus += 0.08;
					}
					out.add(sc);
				}
			}
			if (s.unmet(i)) {
				Fixture gl = glow(s, x, y, z);
				if (gl != null) {
					out.add(gl);
				}
			}
		}
	}

	private static boolean nearUnmet(Session s, int x, int y, int z, int radius) {
		for (int dz = -radius; dz <= radius; dz++) {
			for (int dx = -radius; dx <= radius; dx++) {
				if (Math.abs(dx) + Math.abs(dz) > radius) {
					continue;
				}
				int nx = x + dx;
				int nz = z + dz;
				for (int dy = -1; dy <= 1; dy++) {
					if (s.g.contains(nx, y + dy, nz) && s.unmet(s.g.index(nx, y + dy, nz))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Whether the wall cell beside (x, z) toward {@code wall} is next to a doorway in the same wall. */
	private static boolean besideDoor(Session s, int x, int y, int z, Direction wall) {
		Direction side = Dirs.right(wall);
		for (int k : new int[]{1, -1}) {
			int nx = x + Dirs.dx(wall) + Dirs.dx(side) * k;
			int nz = z + Dirs.dz(wall) + Dirs.dz(side) * k;
			Block b = s.g.at(nx, y, nz).getBlock();
			if (b instanceof DoorBlock || b instanceof FenceGateBlock) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A wall light inside a room at (x, z) on the wall at (wx, wz): a lantern hung from a bracket (a fence post against the
	 * wall) above head height where the room is tall enough, else a torch.
	 */
	static @Nullable Fixture sconce(Session s, int x, int y, int z, int wx, int wz, Direction out, int height, boolean corridor) {
		if (!s.m.torches && height >= 4 && s.wallAt(wx, y + 3, wz, out) && s.solidOk(x, y + 3, z) && s.hungOk(x, y + 2, z)) {
			Fixture f = new Fixture(Fixture.Kind.SCONCE, corridor ? 0.95 : 1.2);
			f.part(x, y + 3, z, fence(s, x, y + 3, z));
			f.part(x, y + 2, z, hung(s));
			return f;
		}
		int ty = height >= 3 ? y + 2 : y + 1;
		if (s.wallAt(wx, ty, wz, out) && s.free(x, ty, z) && !s.sp.walkable(x, ty, z)) {
			Fixture f = new Fixture(Fixture.Kind.TORCH, s.m.torches ? (corridor ? 0.9 : 1.05) : 1.6);
			f.part(x, ty, z, States.wallTorch(s.m.torch, out));
			return f;
		}
		return null;
	}

	/** Light under a rug: the floor block swapped for a glowing one, a carpet laid over it. */
	static @Nullable Fixture glow(Session s, int x, int y, int z) {
		BlockState feet = s.g.at(x, y, z);
		BlockState floor = s.g.at(x, y - 1, z);
		Draft.Cell fc = s.d.cell(x, y - 1, z);
		if (fc == null || !floor.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
			|| floor.getLightEmission() > 0 || fc.tag() != null) {
			return null;
		}
		boolean carpet = Session.carpet(feet);
		if (!carpet && !(feet.isAir() && s.free(x, y, z))) {
			return null;
		}
		// not over a room below (its ceiling would glow)
		if (s.sp.walkable(x, y - 3, z) || s.g.at(x, y - 2, z).isAir() && s.g.placed(x, y - 2, z)) {
			return null;
		}
		Fixture f = new Fixture(Fixture.Kind.GLOW, 2.6);
		f.part(x, y - 1, z, s.m.glow.defaultBlockState(), fc.stage());
		if (!carpet) {
			f.part(x, y, z, s.cloth(x, y, z).defaultBlockState(), StageKind.INTERIOR);
		}
		return f;
	}

	// ------------------------------------------------------------------------------------------- doors

	/** Lamps for the outside of an entrance at (ex, ey, ez). */
	static void doorLamps(Session s, int ex, int ey, int ez, List<Fixture> out) {
		for (int dz = -2; dz <= 2; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				int x = ex + dx;
				int z = ez + dz;
				boolean beside = Math.abs(dx) + Math.abs(dz) == 1;
				boolean over = dx == 0 && dz == 0;
				for (Direction dir : Dirs.HORIZONTAL) {
					int wx = x + Dirs.dx(dir);
					int wz = z + Dirs.dz(dir);
					Direction outward = dir.getOpposite();
					// a lantern on a bracket at lintel height, else at head height when nothing is there to bump into
					for (int ly : new int[]{ey + 2, ey + 1, ey + 3, ey + 4}) {
						if (s.wallAt(wx, ly + 1, wz, outward) && s.solidOk(x, ly + 1, z) && s.hungOk(x, ly, z)) {
							Fixture f = new Fixture(Fixture.Kind.SCONCE, ly == ey + 2 ? 1.0 : 1.15);
							f.part(x, ly + 1, z, fence(s, x, ly + 1, z), StageKind.EXTERIOR);
							f.part(x, ly, z, hung(s), StageKind.EXTERIOR);
							f.bonus = beside ? 0.12 : over ? 0.08 : 0;
							out.add(f);
							break;
						}
					}
					for (int ty = ey + 2; ty <= ey + 3; ty++) {
						if (s.wallAt(wx, ty, wz, outward) && s.free(x, ty, z) && !s.sp.walkable(x, ty, z)) {
							Fixture f = new Fixture(Fixture.Kind.TORCH, s.m.torches ? 1.0 : 1.5);
							f.part(x, ty, z, States.wallTorch(s.m.torch, outward), StageKind.EXTERIOR);
							f.bonus = beside ? 0.12 : 0;
							out.add(f);
						}
					}
				}
				Fixture eave = eave(s, x, ey, z);
				if (eave != null) {
					eave.bonus = beside || over ? 0.1 : 0;
					out.add(eave);
				}
				// posts and yard lamps at the door's level, or up on the ground above it (a door at the foot of a bank)
				for (int ly = ey; ly <= ey + 4; ly++) {
					Fixture top = postTop(s, x, ly, z);
					if (top != null) {
						out.add(top);
					}
				}
				if (!over && Math.max(Math.abs(dx), Math.abs(dz)) <= 2) {
					for (int yy = ey; yy <= ey + 3; yy++) {
						Fixture y = yard(s, x, yy, z, true);
						if (y != null) {
							out.add(y);
						}
						if (s.m.yard == Manner.Yard.TIKI) {
							// a tiki torch burns a step dimmer than a lantern: a lantern on a post where the torch falls short
							Fixture post = yard(s, x, yy, z, true, Manner.Yard.POST, 1.45);
							if (post != null) {
								out.add(post);
							}
						}
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------- ground

	/** Candidates round a dark cell outside (or under a porch, a gallery, an open hall). */
	static void groundNear(Session s, int cx, int cy, int cz, List<Fixture> out) {
		int r = 5;
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				int x = cx + dx;
				int z = cz + dz;
				int dist = Math.abs(dx) + Math.abs(dz);
				for (int y = cy - 3; y <= cy + 3; y++) {
					if (!s.sp.walkable(x, y, z)) {
						continue;
					}
					Fixture eave = eave(s, x, y, z);
					if (eave != null) {
						out.add(eave);
					}
					if (dist <= 3) {
						for (Direction dir : Dirs.HORIZONTAL) {
							int wx = x + Dirs.dx(dir);
							int wz = z + Dirs.dz(dir);
							Direction outward = dir.getOpposite();
							if (s.wallAt(wx, y + 3, wz, outward) && s.solidOk(x, y + 3, z) && s.hungOk(x, y + 2, z)) {
								Fixture f = new Fixture(Fixture.Kind.SCONCE, 1.0);
								f.part(x, y + 3, z, fence(s, x, y + 3, z));
								f.part(x, y + 2, z, hung(s));
								out.add(f);
							} else if (s.m.torches && s.wallAt(wx, y + 2, wz, outward) && s.free(x, y + 2, z) && !s.sp.walkable(x, y + 2, z)) {
								Fixture f = new Fixture(Fixture.Kind.TORCH, 1.05);
								f.part(x, y + 2, z, States.wallTorch(s.m.torch, outward));
								out.add(f);
							}
						}
					}
					if ((dx + dz & 1) == 0 && dist <= 4 && s.sp.reached(x, y, z)) {
						Fixture yd = yard(s, x, y, z);
						if (yd != null) {
							out.add(yd);
						}
					}
				}
				for (int y = cy - 2; y <= cy + 3; y++) {
					Fixture top = postTop(s, x, y, z);
					if (top != null) {
						out.add(top);
					}
				}
			}
		}
	}

	/** A lantern hung under the roof over a standing cell (an eave, a porch roof, a gallery, an open hall's ceiling). */
	static @Nullable Fixture eave(Session s, int x, int y, int z) {
		int roof = s.sp.roofY(x, y, z);
		if (roof == Integer.MAX_VALUE || roof - y > 7 || !s.g.contains(x, roof, z) || !Session.sturdyBelow(s.g.at(x, roof, z))) {
			return null;
		}
		int yl = Math.min(roof - 1, y + 3);
		if (yl < y + 2) {
			return null;
		}
		for (int yy = yl; yy < roof; yy++) {
			if (!s.hungOk(x, yy, z)) {
				return null;
			}
		}
		Fixture f = new Fixture(Fixture.Kind.EAVE, 0.9);
		for (int yy = roof - 1; yy > yl; yy--) {
			f.part(x, yy, z, chain(s));
		}
		f.part(x, yl, z, hung(s));
		return f;
	}

	/** A lantern stood on a fence or wall post at (x, y - 1, z) (a torch, in the tiki cultures). */
	static @Nullable Fixture postTop(Session s, int x, int y, int z) {
		BlockState below = s.g.at(x, y - 1, z);
		if (!Session.fenceOrWall(below) || !s.g.placed(x, y - 1, z) || !s.free(x, y, z) || s.body(x, y, z) && s.sp.walkable(x, y, z)) {
			return null;
		}
		Fixture f = new Fixture(Fixture.Kind.POST_TOP, 0.85);
		f.part(x, y, z, s.m.yard == Manner.Yard.TIKI ? Blocks.TORCH.defaultBlockState() : standing(s), StageKind.EXTERIOR);
		return f;
	}

	/**
	 * The culture's yard light on a free standing cell: a fence-post lamp, a brazier on a stone stand, a stone lantern
	 * under its little cap, a tiki torch. Only where it blocks no way through, no door and no work.
	 */
	static @Nullable Fixture yard(Session s, int x, int y, int z) {
		return yard(s, x, y, z, false);
	}

	/**
	 * {@link #yard(Session, int, int, int)}; {@code byDoor} for a door's lamp: it may stand beside the door or the gate
	 * (next to their points, never on them) and on the plot's street edge, where a gate's lamp post stands.
	 */
	static @Nullable Fixture yard(Session s, int x, int y, int z, boolean byDoor) {
		return yard(s, x, y, z, byDoor, s.m.yard, 1.3);
	}

	/** A yard light of the given kind (a door in a torch-lit culture may still get a lantern on a post where a torch is too dim). */
	static @Nullable Fixture yard(Session s, int x, int y, int z, boolean byDoor, Manner.Yard kind, double cost) {
		if (!s.yards || !s.sp.walkable(x, y, z) || !s.sp.reached(x, y, z) || !s.g.inPlot(x, z) || s.sp.roofed(x, y, z)) {
			return null;
		}
		if (x <= 0 || z < (byDoor ? 0 : 1) || x >= s.d.frontage() - 1 || z >= s.d.depth() - 1) {
			return null;
		}
		for (int yy = y; yy < y + 3 && !byDoor; yy++) {
			if (s.nearPoi.contains(net.minecraft.core.BlockPos.asLong(x, yy, z))) {
				return null;
			}
		}
		BlockState floor = s.g.at(x, y - 1, z);
		Block fb = floor.getBlock();
		if (fb == Blocks.DIRT_PATH || fb == Blocks.FARMLAND || fb instanceof SlabBlock || fb instanceof StairBlock || fb == Blocks.GRAVEL
			|| fb instanceof net.minecraft.world.level.block.LiquidBlock || !Session.sturdyAbove(floor)) {
			return null;
		}
		int tall = switch (kind) {
			case POST, TIKI -> 3;
			case BRAZIER -> 2;
			case STONE_LANTERN -> 3;
		};
		for (int yy = y; yy < y + tall; yy++) {
			if (!s.free(x, yy, z)) {
				return null;
			}
		}
		if (cuts(s, x, y, z)) {
			return null;
		}
		Fixture f = new Fixture(Fixture.Kind.YARD, cost);
		switch (kind) {
			case POST -> {
				f.part(x, y, z, fence(s, x, y, z), StageKind.EXTERIOR);
				f.part(x, y + 1, z, fence(s, x, y + 1, z), StageKind.EXTERIOR);
				f.part(x, y + 2, z, standing(s), StageKind.EXTERIOR);
			}
			case TIKI -> {
				f.part(x, y, z, fence(s, x, y, z), StageKind.EXTERIOR);
				f.part(x, y + 1, z, fence(s, x, y + 1, z), StageKind.EXTERIOR);
				f.part(x, y + 2, z, Blocks.TORCH.defaultBlockState(), StageKind.EXTERIOR);
			}
			case BRAZIER -> {
				BlockState stand = s.m.stone().wall().at(x, y, z);
				f.part(x, y, z, stand == null ? Blocks.COBBLESTONE_WALL.defaultBlockState() : stand, StageKind.EXTERIOR);
				f.part(x, y + 1, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false), StageKind.EXTERIOR);
			}
			case STONE_LANTERN -> {
				BlockState stand = s.m.stone().wall().at(x, y, z);
				BlockState cap = s.m.stone().slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM).at(x, y + 2, z);
				f.part(x, y, z, stand == null ? Blocks.STONE_BRICK_WALL.defaultBlockState() : stand, StageKind.EXTERIOR);
				f.part(x, y + 1, z, standing(s), StageKind.EXTERIOR);
				f.part(x, y + 2, z, cap == null ? Blocks.STONE_BRICK_SLAB.defaultBlockState() : cap, StageKind.EXTERIOR);
			}
		}
		// out of the way: against a fence, a wall, a hedge or the plot's edge
		for (Direction dir : Dirs.HORIZONTAL) {
			int nx = x + Dirs.dx(dir);
			int nz = z + Dirs.dz(dir);
			if (!s.g.inPlot(nx, nz) || !Spaces.passable(s.g.at(nx, y, nz))) {
				f.bonus = 0.08;
				break;
			}
		}
		return f;
	}

	/** Whether standing something at (x, y, z) would cut a way through (its walkable neighbours no longer meet). */
	static boolean cuts(Session s, int x, int y, int z) {
		java.util.List<int[]> ns = new java.util.ArrayList<>();
		for (Direction dir : Dirs.HORIZONTAL) {
			int nx = x + Dirs.dx(dir);
			int nz = z + Dirs.dz(dir);
			for (int dy = 1; dy >= -1; dy--) {
				if (s.sp.walkable(nx, y + dy, nz) && s.sp.reached(nx, y + dy, nz)) {
					ns.add(new int[]{nx, y + dy, nz});
					break;
				}
			}
		}
		if (ns.size() <= 1) {
			return false;
		}
		it.unimi.dsi.fastutil.longs.LongOpenHashSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
		q.add(ns.get(0));
		seen.add(net.minecraft.core.BlockPos.asLong(ns.get(0)[0], ns.get(0)[1], ns.get(0)[2]));
		while (!q.isEmpty()) {
			int[] c = q.poll();
			for (Direction dir : Dirs.HORIZONTAL) {
				int nx = c[0] + Dirs.dx(dir);
				int nz = c[2] + Dirs.dz(dir);
				if (Math.abs(nx - x) > 3 || Math.abs(nz - z) > 3 || nx == x && nz == z) {
					continue;
				}
				for (int dy = 1; dy >= -1; dy--) {
					int ny = c[1] + dy;
					if (s.sp.walkable(nx, ny, nz)) {
						if (seen.add(net.minecraft.core.BlockPos.asLong(nx, ny, nz))) {
							q.add(new int[]{nx, ny, nz});
						}
						break;
					}
				}
			}
		}
		for (int[] n : ns) {
			if (!seen.contains(net.minecraft.core.BlockPos.asLong(n[0], n[1], n[2]))) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------- last resort

	/**
	 * Vanilla's invisible light block near a cell nothing else can light: in a free cell beside something built (so it
	 * does not float), at the lowest level that lights the cell, raised a little when that clears its dark neighbours
	 * too.
	 */
	static @Nullable Fixture hidden(Session s, int x, int y, int z, int min) {
		Fixture best = null;
		double bestScore = -1;
		int[][] spots = {{0, 0, 0}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 2, 0}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}};
		int all = Session.bit(LightRules.Kind.values());
		for (int[] o : spots) {
			int hx = x + o[0];
			int hy = y + o[1];
			int hz = z + o[2];
			if (!hiddenOk(s, hx, hy, hz)) {
				continue;
			}
			int dist = Math.abs(o[0]) + Math.abs(o[1]) + Math.abs(o[2]);
			for (int level = Math.min(15, min + dist); level <= Math.min(15, min + dist + 6); level += 2) {
				Fixture f = new Fixture(Fixture.Kind.HIDDEN, 1);
				f.part(hx, hy, hz, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level));
				if (!Session.within(f, x, y, z, min) || !s.g.contains(x, y, z)) {
					continue;
				}
				double gain = s.evaluate(f, all, s.g.index(x, y, z), min);
				double score = gain / (1 + 0.25 * (level - min));
				if (s.targetHit() && score > bestScore) {
					best = f;
					bestScore = score;
				}
			}
		}
		return best;
	}

	/**
	 * A lit recess: the block under a standing cell (built or natural ground, in the plot) swapped for the culture's
	 * glowing block, for a cell with no air beside it to hide a light in (the foot of a ladder in a shaft). Never in the
	 * ceiling of a room below.
	 */
	static @Nullable Fixture recess(Session s, int x, int y, int z, int min) {
		if (!s.g.inPlot(x, z) || !s.g.contains(x, y - 1, z) || !s.d.allowed(x, y - 1, z)) {
			return null;
		}
		BlockState floor = s.g.at(x, y - 1, z);
		Draft.Cell fc = s.d.cell(x, y - 1, z);
		if (!floor.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
			|| floor.getLightEmission() > 0 || fc != null && fc.tag() != null || s.sp.walkable(x, y - 3, z)) {
			return null;
		}
		Fixture f = new Fixture(Fixture.Kind.GLOW, 3.0);
		f.part(x, y - 1, z, s.m.glow.defaultBlockState(), fc == null ? StageKind.FOUNDATION : fc.stage());
		if (!s.satisfiesTarget(f, x, y, z, min)) {
			return null;
		}
		return f;
	}

	private static boolean hiddenOk(Session s, int x, int y, int z) {
		if (!s.d.allowed(x, y, z) || !s.d.isEmpty(x, y, z) || !s.g.contains(x, y, z) || !s.g.at(x, y, z).isAir()) {
			return false;
		}
		// joined to something built or to the ground, face or edge (the floating check's rule)
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					int nz = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
					if (nz == 0 || nz == 3) {
						continue;
					}
					if (!s.d.isEmpty(x + dx, y + dy, z + dz) && !s.d.get(x + dx, y + dy, z + dz).isAir()) {
						return true;
					}
					if (nz == 1 && s.d.isAbsent(x + dx, y + dy, z + dz) && y + dy < s.d.site().ground(x + dx, z + dz)) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
