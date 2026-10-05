package dev.voidmc.hearth.build.age;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.style.StyleRegistry;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * One decayed spec in detail, for debugging: {@code HEARTH_AGE_DEBUG=archetype,culture,terrain,seed,facing,decay[,y/y]}
 * prints the checks, the way in found on the drawing and walk maps at the given floor-relative heights.
 */
class AgeDebugTest {
	/** {@code HEARTH_AGE_SCAN=archetype,culture,terrain,seed,facing}: what accretion reads of a house. */
	@Test
	void scan() {
		String env = System.getenv("HEARTH_AGE_SCAN");
		if (env == null) {
			return;
		}
		String[] a = env.split(",");
		ArchetypeInfo info = AgeSupport.SERVICE.archetype(Hearth.id(a[0]));
		Culture c = Culture.valueOf(a[1].toUpperCase(java.util.Locale.ROOT));
		SyntheticTerrain probe = SyntheticTerrain.named(a[2], 42);
		CompoundTag params = new CompoundTag();
		if (a.length > 5) {
			params.putString(Accretion.PARAM, a[5].replace(';', ','));
		}
		PlotSpec spec = AgeSupport.spec(info, StyleRegistry.styleId(c), Long.parseLong(a[3]), Direction.byName(a[4]), probe, params);
		var d = AgeSupport.SERVICE.draw(spec, probe);
		var route = Access.route(d);
		var h = dev.voidmc.hearth.build.kit.age.HouseScan.of(d, route);
		System.out.println("plot " + d.frontage() + "x" + d.depth() + " body " + h.x0 + "," + h.z0 + " .. " + h.x1 + "," + h.z1 + " eave " + h.eave
			+ " floors " + h.floors + " flat " + h.flat + " top " + h.top + " route " + route.size());
		for (Direction out : dev.voidmc.hearth.build.draft.Dirs.HORIZONTAL) {
			var s = h.side(out);
			StringBuilder sb = new StringBuilder(out + " party " + h.party(out) + " depths:");
			for (int i = 0; i <= s.last(); i++) {
				sb.append(' ').append(h.depthOut(s, i, 5, Math.min(h.eave - 1, 4)));
			}
			System.out.println(sb);
		}
		int ya = a.length > 6 ? Integer.parseInt(a[6]) : 0;
		int yb = a.length > 7 ? Integer.parseInt(a[7]) : 4;
		for (int y = ya; y <= yb; y++) {
			System.out.println("y=" + y);
			for (int z = d.depth() - 1; z >= -1; z--) {
				StringBuilder sb = new StringBuilder(String.format("%3d ", z));
				for (int x = 0; x < d.frontage(); x++) {
					var cell = d.cell(x, y, z);
					sb.append(cell == null ? ' ' : cell.state().isAir() ? '.' : cell.stage().name().charAt(0));
				}
				System.out.println(sb);
			}
		}
	}

	/** {@code HEARTH_AGE_STATS=1} (with HEARTH_AGE_LOG=1): every housing archetype in every culture grown by 8. */
	@Test
	void stats() {
		if (System.getenv("HEARTH_AGE_STATS") == null) {
			return;
		}
		SyntheticTerrain probe = SyntheticTerrain.named(System.getenv().getOrDefault("HEARTH_AGE_TERRAIN", "flat"), 42);
		for (ArchetypeInfo info : AgeSupport.SERVICE.archetypes()) {
			if (info.category() != dev.voidmc.hearth.api.ArchetypeCategory.HOUSING) {
				continue;
			}
			for (Culture c : Culture.values()) {
				if (!info.suits(c)) {
					continue;
				}
				for (long seed = 1; seed <= 3; seed++) {
					CompoundTag params = new CompoundTag();
					params.putInt(Accretion.PARAM, 8);
					PlotSpec spec = AgeSupport.spec(info, StyleRegistry.styleId(c), seed * 131, AgeSupport.FACINGS[(int) seed % 4], probe, params);
					AgeSupport.SERVICE.draw(spec, probe);
				}
			}
		}
	}

	@Test
	void debug() {
		String env = System.getenv("HEARTH_AGE_DEBUG");
		if (env == null) {
			return;
		}
		String[] a = env.split(",");
		ArchetypeInfo info = AgeSupport.SERVICE.archetype(Hearth.id(a[0]));
		Culture c = Culture.valueOf(a[1].toUpperCase(java.util.Locale.ROOT));
		SyntheticTerrain probe = SyntheticTerrain.named(a[2], 42);
		CompoundTag params = new CompoundTag();
		params.putDouble(Decay.PARAM, Double.parseDouble(a[5]));
		PlotSpec spec = AgeSupport.spec(info, StyleRegistry.styleId(c), Long.parseLong(a[3]), Direction.byName(a[4]), probe, params);
		PlotSpec intact = AgeSupport.with(spec, Decay.PARAM, 0.0);
		var draft = AgeSupport.SERVICE.draw(intact, probe);
		Access.DEBUG = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
		Access.route(draft);
		for (int y = -3; y <= 3; y++) {
			System.out.println("walker y=" + y);
			for (int z = -1; z <= draft.depth(); z++) {
				StringBuilder sb = new StringBuilder();
				for (int x = -1; x <= draft.frontage(); x++) {
					boolean r = Access.DEBUG.contains(dev.voidmc.hearth.build.draft.Draft.key(x, y, z));
					boolean wk = Access.walkable(draft, x, y, z);
					sb.append(r ? 'r' : wk ? '+' : Access.at(draft, x, y, z).isAir() ? '.' : '#');
				}
				System.out.println(sb);
			}
		}
		Access.DEBUG = null;
		System.out.println("route on the drawing: " + Access.route(draft).size() + " cells, entrance " + java.util.Arrays.toString(draft.entrances().get(0)));
		for (long k : Access.route(draft)) {
			System.out.print(" (" + dev.voidmc.hearth.build.draft.Draft.keyX(k) + "," + dev.voidmc.hearth.build.draft.Draft.keyY(k) + ","
				+ dev.voidmc.hearth.build.draft.Draft.keyZ(k) + ")");
		}
		System.out.println();
		BlueprintImpl bp = AgeSupport.SERVICE.compiled(spec, probe);
		BlueprintImpl bp0 = AgeSupport.SERVICE.compiled(intact, probe);
		System.out.println("checks: " + BlueprintChecks.all(bp, probe));
		System.out.println("intact checks: " + BlueprintChecks.all(bp0, probe));
		try {
			// what the check walks to from the street (by reflection: the walker is the check's own)
			Class<?> wc = Class.forName("dev.voidmc.hearth.build.check.BlueprintChecks$World");
			var ctor = wc.getDeclaredConstructor(BlueprintImpl.class, dev.voidmc.hearth.api.TerrainProbe.class);
			ctor.setAccessible(true);
			Object world = ctor.newInstance(bp0, probe);
			var walk = BlueprintChecks.class.getDeclaredMethod("walk", wc, java.util.List.class, int[].class);
			walk.setAccessible(true);
			var f = new dev.voidmc.hearth.build.blueprint.LocalFrame(intact);
			java.util.List<net.minecraft.core.BlockPos> street = new java.util.ArrayList<>();
			for (int lx = 0; lx < f.frontage(); lx++) {
				int wx = f.worldX(lx, -1);
				int wz = f.worldZ(lx, -1);
				street.add(new net.minecraft.core.BlockPos(wx, Math.max(probe.groundY(wx, wz), intact.floorY()), wz));
			}
			var b = bp0.bounds();
			int[] box = {b.minX() - 2, b.minY() - 2, b.minZ() - 2, b.maxX() + 2, b.maxY() + 2, b.maxZ() + 2};
			var set = (it.unimi.dsi.fastutil.longs.LongOpenHashSet) walk.invoke(null, world, street, box);
			for (int ly = -3; ly <= 3; ly++) {
				System.out.println("check street walk y=" + ly + " (local rows z=-1.., cols x=-2..)");
				for (int lz = -2; lz <= f.depth() + 1; lz++) {
					StringBuilder sb = new StringBuilder();
					for (int lx = -2; lx <= f.frontage() + 1; lx++) {
						long k = net.minecraft.core.BlockPos.asLong(f.worldX(lx, lz), intact.floorY() + ly, f.worldZ(lx, lz));
						sb.append(set.contains(k) ? 'S' : Access.walkable(draft, lx, ly, lz) ? '+' : Access.at(draft, lx, ly, lz).isAir() ? '.' : '#');
					}
					System.out.println(sb);
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		String ys = a.length > 6 ? a[6] : "0";
		for (String y : ys.split("/")) {
			System.out.println("intact " + BlueprintChecks.reachMap(bp0, probe, spec.floorY() + Integer.parseInt(y)));
			System.out.println("decayed " + BlueprintChecks.reachMap(bp, probe, spec.floorY() + Integer.parseInt(y)));
		}
	}
}
