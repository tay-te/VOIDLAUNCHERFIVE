package dev.voidmc.hearth.build.scratch;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.Test;

class ScratchDumpTest {
	@Test
	void dump() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BlueprintServiceImpl service = BuildModule.standalone();
		String[] which = System.getProperty("dump", "house/merchant_house:temperate:39").split(";");
		for (String w : which) {
			String[] a = w.split(":");
			ArchetypeInfo info = service.archetype(Hearth.id(a[0]));
			Culture culture = Culture.valueOf(a[1].toUpperCase());
			long seed = Long.parseLong(a[2]);
			java.util.Random r = new java.util.Random(seed * 31 + info.id().hashCode());
			int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
			int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
			SyntheticTerrain flat = SyntheticTerrain.named("flat", 1);
			int ground = flat.groundY(0, 0);
			int tier = a[0].equals("house/manor") ? 3 : 2;
			PlotSpec spec = new PlotSpec(info.id(), StyleRegistry.styleId(culture), seed, new BlockPos(0, ground, 0), frontage, depth, Direction.SOUTH,
				Math.max(info.maxHeight(), 8), tier, new CompoundTag());
			BlueprintImpl bp = Compiler.compile(service.draw(spec, flat));
			Map<BlockPos, BlockState> m = new HashMap<>();
			int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
			for (int st = 0; st < bp.stageCount(); st++) {
				for (int i = 0; i < bp.stepCount(st); i++) {
					bp.step(st, i, (x, y, z, state, tag) -> m.put(new BlockPos(x, y, z), state));
				}
			}
			for (BlockPos p : m.keySet()) {
				minY = Math.min(minY, p.getY());
				maxY = Math.max(maxY, p.getY());
			}
			StringBuilder sb = new StringBuilder();
			sb.append("== ").append(w).append(" ").append(frontage).append("x").append(depth).append(" ground ").append(ground).append('\n');
			for (Poi p : bp.pois()) {
				if (p.type() == PoiType.BED || p.type() == PoiType.JOB_SITE || p.type() == PoiType.STORAGE) {
					sb.append("  poi ").append(p.type()).append(" ").append(p.pos().getX()).append(",").append(p.pos().getY() - ground).append(",").append(p.pos().getZ()).append('\n');
				}
			}
			for (int y = ground - 1; y <= maxY; y++) {
				sb.append("y=").append(y - ground).append('\n');
				for (int z = -2; z < depth + 3; z++) {
					StringBuilder row = new StringBuilder(String.format("%3d ", z));
					for (int x = -2; x < frontage + 2; x++) {
						BlockState s = m.get(new BlockPos(x, y, z));
						row.append(ch(s));
					}
					sb.append(row).append('\n');
				}
			}
			System.out.println(sb);
		}
	}

	private static String ch(BlockState s) {
		if (s == null || s.isAir()) {
			return " .";
		}
		Block b = s.getBlock();
		if (b instanceof StairBlock) {
			String f = switch (s.getValue(StairBlock.FACING)) { case NORTH -> "n"; case SOUTH -> "s"; case EAST -> "e"; default -> "w"; };
			return (s.getValue(StairBlock.HALF) == Half.TOP ? "^" : "/") + f;
		}
		if (b instanceof SlabBlock) {
			return s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM ? " _" : s.getValue(SlabBlock.TYPE) == SlabType.TOP ? " ~" : " #";
		}
		if (b instanceof BedBlock) return " B";
		if (b instanceof LadderBlock) return " L";
		if (b instanceof DoorBlock) return " D";
		if (b instanceof TrapDoorBlock) return s.getValue(TrapDoorBlock.OPEN) ? " t" : " T";
		if (b instanceof CarpetBlock) return " c";
		var shape = s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		if (shape.isEmpty()) return " ,";
		if (s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return " #";
		return " o";
	}
}
