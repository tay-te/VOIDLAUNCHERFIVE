package dev.voidmc.hearth.build.kit.craft;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * What a working trade hangs up to dry: hides on the tanner's frames, hanks of dyed cloth on the dyer's poles, sheets
 * of paper in the mill's loft. Each piece is a wall banner hung from the face of a beam, so it hangs a block and more
 * below it the way a hide or a length of cloth does, and reads from far off by its colour.
 */
public final class Hangs {
	private Hangs() {
	}

	/** Leather in its states: rawhide, limed white, tanned brown, curried dark. */
	public static final String[] HIDES = {"white", "brown", "white", "light_gray", "orange", "white", "brown"};
	/** Paper drying: white sheets, a few greyed. */
	public static final String[] PAPER = {"white", "white", "white", "light_gray"};
	/** The dyers' colours: woad, madder, weld and their mixtures, all strong and bright. */
	public static final String[] DYED = {"red", "blue", "yellow", "green", "purple", "orange", "light_blue", "magenta", "cyan", "lime", "black", "red", "blue"};

	/** A wall banner block of a colour by name. */
	public static Block banner(String colour) {
		return States.block("minecraft:" + colour + "_wall_banner", Blocks.WALL_BANNER.pick(DyeColor.WHITE));
	}

	/** The colour for one hanging piece at a position: chosen from {@code colours} by the seed and the position. */
	public static String colour(Draft d, String[] colours, int x, int y, int z) {
		return colours[(int) Math.floorMod(Noise.hash(d.seed() ^ 0x4A9C, x, y, z), (long) colours.length)];
	}

	/**
	 * The pattern that makes a hanging piece read as what it is: a hide's ragged edge (a curly border a shade darker), a
	 * length of cloth's selvedge (a thin stripe), nothing for paper.
	 */
	public static net.minecraft.nbt.CompoundTag dress(String[] colours, String colour) {
		if (colours == HIDES) {
			String edge = switch (colour) {
				case "white" -> "light_gray";
				case "light_gray" -> "gray";
				case "orange" -> "brown";
				default -> "black";
			};
			return dev.voidmc.hearth.build.draft.BlockEntities.banner(java.util.List.of(new String[][]{{"curly_border", edge}}));
		}
		if (colours == DYED) {
			return dev.voidmc.hearth.build.draft.BlockEntities.banner(java.util.List.of(new String[][]{{"stripe_bottom", colour.equals("white") ? "light_gray" : "white"}}));
		}
		return null;
	}

	/**
	 * Hangs a piece in the cell (x, y, z) from the beam behind it (the block toward {@code -face}), facing {@code face},
	 * if the cell is free. Answers whether it hung.
	 */
	public static boolean piece(Draft d, int x, int y, int z, Direction face, String colour) {
		return piece(d, x, y, z, face, colour, null);
	}

	/** {@link #piece} with a banner pattern (see {@link #dress}). */
	public static boolean piece(Draft d, int x, int y, int z, Direction face, String colour, net.minecraft.nbt.CompoundTag pattern) {
		if (!d.allowed(x, y, z) || !d.isEmpty(x, y, z)) {
			return false;
		}
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, States.facing(banner(colour), face), pattern));
		return true;
	}

	/** A piece of the given kind (one of the colour sets), its colour chosen by position and dressed to read as what it is. */
	public static boolean hang(Draft d, int x, int y, int z, Direction face, String[] colours) {
		String c = colour(d, colours, x, y, z);
		return piece(d, x, y, z, face, c, dress(colours, c));
	}

	/**
	 * A drying rail: a beam at height y along {@code run} for {@code length} cells from (x, z), with pieces hung from both
	 * faces (every cell, or every other when {@code sparse}), in the given colours. The beam's ends must be carried by
	 * the caller (posts, walls).
	 */
	public static void rail(Draft d, int x, int y, int z, Direction run, int length, String[] colours, boolean sparse, boolean bothFaces) {
		Mat beam = d.mat(Role.BEAM);
		Direction face = Dirs.right(run);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				if (d.allowed(px, y, pz) && (d.isAbsent(px, y, pz) || d.isEmpty(px, y, pz))) {
					d.set(px, y, pz, beam.log(run.getAxis()));
				}
			}
		}
		for (int i = 0; i < length; i++) {
			int px = x + Dirs.dx(run) * i;
			int pz = z + Dirs.dz(run) * i;
			if (sparse && (i & 1) == 1) {
				continue;
			}
			for (Direction f : bothFaces ? new Direction[]{face, face.getOpposite()} : new Direction[]{face}) {
				int hx = px + Dirs.dx(f);
				int hz = pz + Dirs.dz(f);
				if (Walls.inPlot(d, hx, hz) && d.isAbsent(hx, y - 1, hz) || !Walls.inPlot(d, hx, hz) && hz == -1 && y - 1 >= 3) {
					hang(d, hx, y, hz, f, colours);
				}
			}
		}
	}

	/**
	 * A free-standing drying frame: posts at both ends of a run (fence posts on a stone foot, or the frame's logs when
	 * {@code logs}), a beam across their heads at {@code height} above the standing level y, and pieces hung on both faces.
	 * Needs the run's cells free from y up and a cell either side for the hanging pieces.
	 */
	public static void frame(Draft d, int x, int y, int z, Direction run, int length, int height, String[] colours, boolean logs) {
		Mat post = logs ? d.mat(Role.FRAME) : Hall.woodFence(d);
		int top = y + height;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int end : new int[]{0, length - 1}) {
				int px = x + Dirs.dx(run) * end;
				int pz = z + Dirs.dz(run) * end;
				for (int yy = y; yy < top; yy++) {
					d.set(px, yy, pz, logs ? post.log(Direction.Axis.Y) : post.fence());
				}
			}
		}
		// the beam over the posts' heads, the pieces hung along its whole length
		rail(d, x, top, z, run, length, colours, false, true);
	}
}
