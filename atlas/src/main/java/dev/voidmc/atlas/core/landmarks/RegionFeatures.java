package dev.voidmc.atlas.core.landmarks;

import dev.voidmc.atlas.core.Frame;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What one region's land holds that is worth a name, worked out from its map ({@link RegionAnalysis}) and kept beside
 * it on disk, so the landmarks are there the moment a world is joined again:
 *
 * <ul>
 *   <li><b>points</b>: peaks (with their prominence), waterfalls (with their drop), ravines (with their depth);</li>
 *   <li><b>cells</b>: the region in {@value #CELL}-block cells, each labelled with the patch it belongs to — a patch is
 *   a connected run of one biome, of lake water or of river water — so patches can be joined across region borders
 *   into areas ({@link AreaIndex}) and a block can be told which area it is in.</li>
 * </ul>
 */
public final class RegionFeatures {
	public static final int CELL = 8;
	public static final int CELLS = dev.voidmc.atlas.core.Coords.REGION_SIZE / CELL;
	public static final int CELL_AREA = CELLS * CELLS;
	/** No patch. */
	public static final int NONE = 0xFF;

	public enum PointKind {
		PEAK, WATERFALL, RAVINE
	}

	public enum PatchKind {
		/** A run of one biome. */
		BIOME,
		/** Standing water that is not the sea or a river. */
		LAKE,
		/** River water. */
		RIVER
	}

	/**
	 * A point landmark. {@code size}: a peak's prominence, a waterfall's drop, a ravine's depth (blocks); {@code length}
	 * a ravine's length (0 otherwise).
	 */
	public record Point(PointKind kind, int x, int y, int z, int size, int length, int biome) {
	}

	/**
	 * A patch inside the region: its kind and biome, how many cells it has, the sum of its cells' positions (for its
	 * middle), and its best label cell (the one deepest inside it) with that depth in cells.
	 */
	public record Patch(PatchKind kind, int biome, int cells, long sumX, long sumZ, int labelCell, int labelDepth) {
		public int labelX(int rx) {
			return (rx << dev.voidmc.atlas.core.Coords.REGION_SHIFT) + (this.labelCell % CELLS) * CELL + CELL / 2;
		}

		public int labelZ(int rz) {
			return (rz << dev.voidmc.atlas.core.Coords.REGION_SHIFT) + (this.labelCell / CELLS) * CELL + CELL / 2;
		}
	}

	private static final int MAGIC = 0x41544C46; // ATLF
	private static final int VERSION = 1;

	public final int rx;
	public final int rz;
	public final List<Point> points;
	/** The land's patches (biomes) per cell: an index into {@link #land}, or {@link #NONE}. */
	public final byte[] landCells;
	public final List<Patch> land;
	/** The water's patches (lakes, rivers) per cell: an index into {@link #water}, or {@link #NONE}. */
	public final byte[] waterCells;
	public final List<Patch> water;

	public RegionFeatures(int rx, int rz, List<Point> points, byte[] landCells, List<Patch> land, byte[] waterCells, List<Patch> water) {
		this.rx = rx;
		this.rz = rz;
		this.points = List.copyOf(points);
		this.landCells = landCells;
		this.land = List.copyOf(land);
		this.waterCells = waterCells;
		this.water = List.copyOf(water);
	}

	public static RegionFeatures empty(int rx, int rz) {
		byte[] none = new byte[CELL_AREA];
		Arrays.fill(none, (byte) NONE);
		return new RegionFeatures(rx, rz, List.of(), none, List.of(), none.clone(), List.of());
	}

	public int landAt(int cell) {
		return this.landCells[cell] & 0xFF;
	}

	public int waterAt(int cell) {
		return this.waterCells[cell] & 0xFF;
	}

	public byte[] encode() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(CELL_AREA * 2 + 256);
			try (DataOutputStream out = new DataOutputStream(bytes)) {
				out.writeShort(this.points.size());
				for (Point p : this.points) {
					out.writeByte(p.kind().ordinal());
					out.writeInt(p.x());
					out.writeShort(p.y());
					out.writeInt(p.z());
					out.writeInt(p.size());
					out.writeInt(p.length());
					out.writeChar(p.biome());
				}
				writePatches(out, this.land);
				out.write(this.landCells);
				writePatches(out, this.water);
				out.write(this.waterCells);
			}
			return Frame.wrap(MAGIC, VERSION, bytes.toByteArray());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void writePatches(DataOutputStream out, List<Patch> patches) throws IOException {
		out.writeShort(patches.size());
		for (Patch p : patches) {
			out.writeByte(p.kind().ordinal());
			out.writeChar(p.biome());
			out.writeInt(p.cells());
			out.writeLong(p.sumX());
			out.writeLong(p.sumZ());
			out.writeShort(p.labelCell());
			out.writeShort(p.labelDepth());
		}
	}

	private static List<Patch> readPatches(DataInputStream in) throws IOException {
		int n = in.readUnsignedShort();
		List<Patch> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(new Patch(PatchKind.values()[in.readUnsignedByte()], in.readChar(), in.readInt(), in.readLong(), in.readLong(), in.readUnsignedShort(),
				in.readUnsignedShort()));
		}
		return out;
	}

	public static RegionFeatures decode(int rx, int rz, byte[] data) throws IOException {
		byte[] payload = Frame.unwrap(MAGIC, VERSION, data);
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
			int n = in.readUnsignedShort();
			List<Point> points = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				points.add(new Point(PointKind.values()[in.readUnsignedByte()], in.readInt(), in.readShort(), in.readInt(), in.readInt(), in.readInt(),
					in.readChar()));
			}
			List<Patch> land = readPatches(in);
			byte[] landCells = new byte[CELL_AREA];
			in.readFully(landCells);
			List<Patch> water = readPatches(in);
			byte[] waterCells = new byte[CELL_AREA];
			in.readFully(waterCells);
			return new RegionFeatures(rx, rz, points, landCells, land, waterCells, water);
		}
	}
}
