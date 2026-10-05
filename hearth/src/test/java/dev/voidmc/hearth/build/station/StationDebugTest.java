package dev.voidmc.hearth.build.station;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.StyleRegistry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/**
 * A debugging aid, not a gate: with {@code HEARTH_STATION_DEBUG=archetype;culture;seed;facing;frontage;depth;height;terrain[;k=v,...]}
 * it builds that one spec and prints its check problems and, in local coordinates, every block that floats. Run with
 * {@code ./gradlew test --rerun --tests '*StationDebugTest*'}.
 */
class StationDebugTest {
	@Test
	void debugOneSpec() {
		String arg = System.getenv("HEARTH_STATION_DEBUG");
		if (arg == null || arg.isBlank()) {
			return;
		}
		String[] a = arg.split(";");
		ArchetypeInfo info = StationSupport.info(a[0].contains("/") ? a[0] : "house/" + a[0]);
		Culture c = Culture.valueOf(a[1].toUpperCase(Locale.ROOT));
		long seed = Long.parseLong(a[2]);
		Direction facing = Direction.byName(a[3]);
		int frontage = Integer.parseInt(a[4]);
		int depth = Integer.parseInt(a[5]);
		int height = Integer.parseInt(a[6]);
		String terrain = a.length > 7 ? a[7] : "flat";
		CompoundTag params = new CompoundTag();
		if (a.length > 8) {
			for (String kv : a[8].split(",")) {
				String[] p = kv.split("=");
				if (p.length == 2) {
					params.putString(p[0], p[1]);
				}
			}
		}
		SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
		PlotSpec spec = StationSupport.plot(info, StyleRegistry.styleId(c), seed, facing, probe, params, frontage, depth, height, 2);
		BlueprintImpl bp = StationSupport.SERVICE.compiled(spec, probe);
		System.out.println("== " + spec);
		for (String p : BlueprintChecks.all(bp, probe)) {
			System.out.println("CHECK " + p);
		}
		LocalFrame f = new LocalFrame(spec);
		Long2ObjectOpenHashMap<BlockState> placed = new Long2ObjectOpenHashMap<>();
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				placed.put(bp.pos(s, i).asLong(), bp.state(s, i));
			}
		}
		for (var poi : bp.pois()) {
			BlockPos p = poi.pos();
			System.out.println("POI " + poi.type() + (poi.job() == null ? "" : "/" + poi.job()) + " local " + f.localX(p.getX(), p.getZ()) + ","
				+ (p.getY() - spec.floorY()) + "," + f.localZ(p.getX(), p.getZ()));
		}
		String plan = System.getenv("HEARTH_STATION_PLAN");
		if (plan != null && !plan.isBlank()) {
			// top-down slices at the listed local levels: the street at the bottom, local x from left to right as seen from it
			java.util.Map<Long, BlockState> local = new java.util.HashMap<>();
			placed.forEach((k, st) -> {
				BlockPos p = BlockPos.of(k);
				local.put(BlockPos.asLong(f.localX(p.getX(), p.getZ()), p.getY() - spec.floorY(), f.localZ(p.getX(), p.getZ())), st);
			});
			for (String ys : plan.split(",")) {
				int y = Integer.parseInt(ys.trim());
				System.out.println("-- level " + y);
				for (int z = f.depth() - 1; z >= -1; z--) {
					StringBuilder sb = new StringBuilder(String.format("%3d ", z));
					for (int x = f.frontage() - 1; x >= 0; x--) {
						BlockState st = local.get(BlockPos.asLong(x, y, z));
						sb.append(glyph(st));
					}
					System.out.println(sb);
				}
			}
		}
		LongOpenHashSet solid = new LongOpenHashSet();
		placed.forEach((k, st) -> {
			if (!st.isAir()) {
				solid.add((long) k);
			}
		});
		LongOpenHashSet seen = new LongOpenHashSet();
		LongArrayFIFOQueue q = new LongArrayFIFOQueue();
		for (long k : solid) {
			BlockPos p = BlockPos.of(k);
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				BlockState at = placed.get(n.asLong());
				boolean natural = at == null && n.getY() < probe.groundY(n.getX(), n.getZ());
				if (natural) {
					seen.add(k);
					q.enqueue(k);
					break;
				}
			}
		}
		while (!q.isEmpty()) {
			BlockPos p = BlockPos.of(q.dequeueLong());
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						int nz = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
						if (nz == 0 || nz == 3) {
							continue;
						}
						long n = BlockPos.asLong(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
						if (solid.contains(n) && seen.add(n)) {
							q.enqueue(n);
						}
					}
				}
			}
		}
		for (long k : solid) {
			if (!seen.contains(k)) {
				BlockPos p = BlockPos.of(k);
				System.out.println("FLOAT local " + f.localX(p.getX(), p.getZ()) + "," + (p.getY() - spec.floorY()) + "," + f.localZ(p.getX(), p.getZ()) + " "
					+ placed.get(k));
			}
		}
	}

	private static char glyph(BlockState st) {
		if (st == null) {
			return ' ';
		}
		if (st.isAir()) {
			return '.';
		}
		String n = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
		if (n.endsWith("_bed")) {
			return 'B';
		}
		if (n.endsWith("_door")) {
			return 'D';
		}
		if (n.endsWith("_stairs")) {
			return 'S';
		}
		if (n.equals("ladder")) {
			return 'H';
		}
		if (n.equals("chest")) {
			return 'C';
		}
		if (n.equals("barrel")) {
			return 'b';
		}
		if (n.equals("lectern")) {
			return 'L';
		}
		if (n.contains("bookshelf")) {
			return 'K';
		}
		if (n.contains("glass") || n.contains("pane")) {
			return 'w';
		}
		if (n.endsWith("_slab")) {
			return '_';
		}
		if (n.endsWith("_trapdoor")) {
			return 't';
		}
		if (n.contains("fence") || n.contains("wall")) {
			return '|';
		}
		if (n.contains("furnace") || n.contains("smoker") || n.contains("campfire")) {
			return 'F';
		}
		if (n.contains("lantern") || n.contains("candle") || n.contains("torch")) {
			return '*';
		}
		if (n.contains("carpet")) {
			return '~';
		}
		if (n.contains("chair") || n.contains("stool") || n.contains("bench")) {
			return 'h';
		}
		return '#';
	}
}
