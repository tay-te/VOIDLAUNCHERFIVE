package dev.voidmc.hearth.build.extraction;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * One excavation in detail, for chasing a failure: {@code HEARTH_EXTRACT_DEBUG=archetype,culture,terrain,seed,facing,height[,planned]}
 * prints the checks' problems, the standing surface of the plot (local frame, the front row at the top) and the walk
 * maps at the height of every work face that cannot be reached. Run with
 * {@code ./gradlew test --rerun --tests '*ExtractionDebugTest*'}.
 */
class ExtractionDebugTest {
	private static char glyph(net.minecraft.world.level.block.state.BlockState s) {
		var b = s.getBlock();
		if (s.isAir()) {
			return '.';
		}
		if (b instanceof net.minecraft.world.level.block.StairBlock) {
			return 'S';
		}
		if (b instanceof net.minecraft.world.level.block.SlabBlock) {
			return '_';
		}
		if (b instanceof net.minecraft.world.level.block.LadderBlock) {
			return 'H';
		}
		if (b instanceof net.minecraft.world.level.block.FenceBlock || b instanceof net.minecraft.world.level.block.WallBlock) {
			return 'f';
		}
		if (b == net.minecraft.world.level.block.Blocks.GRAVEL) {
			return 'g';
		}
		if (b instanceof net.minecraft.world.level.block.LanternBlock) {
			return 'L';
		}
		if (b instanceof net.minecraft.world.level.block.ChainBlock) {
			return '|';
		}
		if (b instanceof net.minecraft.world.level.block.RailBlock || b instanceof net.minecraft.world.level.block.BaseRailBlock) {
			return '=';
		}
		return '@';
	}

	@Test
	void debug() {
		String spec0 = System.getenv("HEARTH_EXTRACT_DEBUG");
		Assumptions.assumeTrue(spec0 != null && !spec0.isEmpty());
		String[] a = spec0.split(",");
		ArchetypeInfo info = ExtractionArchetypesTest.SERVICE.archetype(Hearth.id(a[0].contains("/") ? a[0] : "extract/" + a[0]));
		Culture c = Culture.valueOf(a[1].toUpperCase(Locale.ROOT));
		SyntheticTerrain probe = SyntheticTerrain.named(a[2], a.length > 7 ? Long.parseLong(a[7]) : 42);
		long seed = Long.parseLong(a[3]);
		Direction facing = Direction.byName(a[4]);
		int height = Integer.parseInt(a[5]);
		CompoundTag params = a.length > 6 && a[6].equals("planned") ? ExtractionArchetypesTest.planned(info, seed) : new CompoundTag();
		PlotSpec spec = ExtractionArchetypesTest.spec(info, c, seed, facing, probe, height, params);
		BlueprintImpl bp = ExtractionArchetypesTest.SERVICE.compiled(spec, probe);
		System.out.println("== " + spec);
		for (String p : BlueprintChecks.all(bp, probe)) {
			System.out.println("PROBLEM " + p);
		}
		for (var fx : dev.voidmc.hearth.build.kit.light.AutoLighter.lightLogged(ExtractionArchetypesTest.SERVICE.draw(spec, probe))) {
			try {
				var fk = fx.getClass().getDeclaredField("kind");
				var fp = fx.getClass().getDeclaredField("parts");
				var fw = fx.getClass().getDeclaredField("why");
				fk.setAccessible(true);
				fp.setAccessible(true);
				fw.setAccessible(true);
				System.out.println("FIXTURE " + fk.get(fx) + " " + fw.get(fx) + " " + fp.get(fx));
			} catch (ReflectiveOperationException e) {
				System.out.println("FIXTURE " + fx);
			}
		}
		for (int s = 0; s < bp.stageCount(); s++) {
			int cuts = 0;
			int air = 0;
			for (int i = 0; i < bp.stepCount(s); i++) {
				var q = bp.pos(s, i);
				if (bp.state(s, i).isAir()) {
					air++;
					if (q.getY() < probe.groundY(q.getX(), q.getZ())) {
						cuts++;
					}
				}
			}
			System.out.println("STAGE " + s + " part " + bp.part(s) + " " + bp.stageKind(s) + " steps " + bp.stepCount(s) + " air " + air + " cuts " + cuts);
		}
		LocalFrame f = new LocalFrame(spec);
		// the standing surface: the highest walkable-looking level per column (relative to the floor)
		StringBuilder sb = new StringBuilder("surface (local x across, z down; relative to floor)\n");
		for (int z = -1; z < f.depth(); z++) {
			for (int x = 0; x < f.frontage(); x++) {
				int wx = f.worldX(x, z);
				int wz = f.worldZ(x, z);
				int g = probe.groundY(wx, wz);
				sb.append(String.format(Locale.ROOT, "%4d", g - spec.floorY()));
			}
			sb.append('\n');
		}
		System.out.println(sb);
		for (Poi p : bp.pois()) {
			if (p.type() == PoiType.WORK_FACE || p.type() == PoiType.JOB_SITE || p.type() == PoiType.STORAGE) {
				System.out.println(p.type() + " local " + f.localX(p.pos().getX(), p.pos().getZ()) + "," + (p.pos().getY() - spec.floorY()) + ","
					+ f.localZ(p.pos().getX(), p.pos().getZ()) + " world " + p.pos());
			}
		}
		// sections in the local frame: HEARTH_EXTRACT_Z=z1/z2 (rows across the frontage) and HEARTH_EXTRACT_X=x (along the depth)
		java.util.Map<Long, net.minecraft.world.level.block.state.BlockState> placed = new java.util.HashMap<>();
		for (int s = 0; s < bp.stageCount(); s++) {
			for (int i = 0; i < bp.stepCount(s); i++) {
				bp.step(s, i, (x, y, z, st, t) -> placed.put(net.minecraft.core.BlockPos.asLong(x, y, z), st));
			}
		}
		for (String zs : System.getenv().getOrDefault("HEARTH_EXTRACT_Z", "").split("/")) {
			if (zs.isEmpty()) {
				continue;
			}
			int lz = Integer.parseInt(zs);
			StringBuilder sec = new StringBuilder("section at local z=" + lz + " (x across, y down from +8)" + System.lineSeparator());
			for (int y = 8; y >= -26; y--) {
				sec.append(String.format(Locale.ROOT, "%4d ", y));
				for (int x = 0; x < f.frontage(); x++) {
					int wx = f.worldX(x, lz);
					int wz = f.worldZ(x, lz);
					int wy = spec.floorY() + y;
					var st = placed.get(net.minecraft.core.BlockPos.asLong(wx, wy, wz));
					char ch;
					if (st != null) {
						ch = glyph(st);
					} else {
						ch = wy < probe.groundY(wx, wz) ? '#' : probe.waterY(wx, wz) != dev.voidmc.hearth.api.TerrainProbe.NO_WATER && wy < probe.waterY(wx, wz) ? '~' : ' ';
					}
					sec.append(ch);
				}
				sec.append(System.lineSeparator());
			}
			System.out.println(sec);
		}
		for (String xs : System.getenv().getOrDefault("HEARTH_EXTRACT_X", "").split("/")) {
			if (xs.isEmpty()) {
				continue;
			}
			int lx = Integer.parseInt(xs);
			StringBuilder sec = new StringBuilder("section at local x=" + lx + " (z across from the front, y down from +12)" + System.lineSeparator());
			for (int y = 12; y >= -16; y--) {
				sec.append(String.format(Locale.ROOT, "%4d ", y));
				for (int z = -1; z < f.depth(); z++) {
					int wx = f.worldX(lx, z);
					int wz = f.worldZ(lx, z);
					int wy = spec.floorY() + y;
					var st = placed.get(net.minecraft.core.BlockPos.asLong(wx, wy, wz));
					char ch;
					if (st != null) {
						ch = glyph(st);
					} else {
						ch = wy < probe.groundY(wx, wz) ? '#' : probe.waterY(wx, wz) != dev.voidmc.hearth.api.TerrainProbe.NO_WATER && wy < probe.waterY(wx, wz) ? '~' : ' ';
					}
					sec.append(ch);
				}
				sec.append(System.lineSeparator());
			}
			System.out.println(sec);
		}
		for (String ys : (System.getenv().getOrDefault("HEARTH_EXTRACT_Y", "")).split("/")) {
			if (!ys.isEmpty()) {
				System.out.println(BlueprintChecks.reachMap(bp, probe, spec.floorY() + Integer.parseInt(ys)));
			}
		}
	}
}
