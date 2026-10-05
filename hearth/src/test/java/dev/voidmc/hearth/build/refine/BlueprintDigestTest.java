package dev.voidmc.hearth.build.refine;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * A digest of every compiled blueprint per archetype (14 cultures, two terrains, two seeds): a tool for refactoring the
 * machinery (the light pass, the compiler) without changing what it builds. Not a gate. Run with
 * {@code HEARTH_DIGEST=1 HEARTH_DIGEST_OUT=<file> ./gradlew test --rerun --tests '*BlueprintDigestTest*'} before and after,
 * then compare the files ({@code HEARTH_DIGEST=<name>} narrows to archetypes whose path contains it).
 */
@EnabledIfEnvironmentVariable(named = "HEARTH_DIGEST", matches = ".+")
class BlueprintDigestTest {
	@Test
	void digest() throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		BlueprintServiceImpl service = BuildModule.standalone();
		String only = System.getenv("HEARTH_DIGEST");
		StringBuilder sb = new StringBuilder();
		long total = 0;
		for (ArchetypeInfo info : service.archetypes()) {
			if (!only.equals("1") && !info.id().getPath().contains(only)) {
				continue;
			}
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			long t0 = System.nanoTime();
			for (String terrain : new String[]{"flat", "hills"}) {
				for (Culture c : Culture.values()) {
					for (int s = 0; s < 2; s++) {
						long seed = 7919L * (s + 1) + c.ordinal() * 104729L;
						Direction facing = new Direction[]{Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST}[(s + c.ordinal()) % 4];
						SyntheticTerrain probe = SyntheticTerrain.named(terrain, 42);
						PlotSpec spec = spec(info, c, seed, facing, probe);
						BlueprintImpl bp = Compiler.compile(service.draw(spec, probe));
						for (int st = 0; st < bp.stageCount(); st++) {
							for (int i = 0; i < bp.stepCount(st); i++) {
								BlockPos p = bp.pos(st, i);
								md.update((st + ":" + p.getX() + "," + p.getY() + "," + p.getZ() + "=" + bp.state(st, i) + ";").getBytes(StandardCharsets.UTF_8));
							}
						}
					}
				}
			}
			total += System.nanoTime() - t0;
			sb.append(String.format(Locale.ROOT, "%-28s %s%n", info.id().getPath(), HexFormat.of().formatHex(md.digest()).substring(0, 16)));
		}
		sb.append(String.format(Locale.ROOT, "time %.1f s%n", total / 1e9));
		System.out.println("\nBLUEPRINT DIGEST\n" + sb);
		String out = System.getenv("HEARTH_DIGEST_OUT");
		if (out != null) {
			Files.writeString(Path.of(out), sb.toString());
		}
	}

	private static PlotSpec spec(ArchetypeInfo info, Culture c, long seed, Direction facing, TerrainProbe probe) {
		Random r = new Random(seed * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		boolean zAxis = facing.getAxis() == Direction.Axis.Z;
		int sizeX = zAxis ? frontage : depth;
		int sizeZ = zAxis ? depth : frontage;
		int ox = -sizeX / 2;
		int oz = -sizeZ / 2;
		int fx = switch (facing) {
			case EAST -> ox + sizeX;
			case WEST -> ox - 1;
			default -> ox + sizeX / 2;
		};
		int fz = switch (facing) {
			case SOUTH -> oz + sizeZ;
			case NORTH -> oz - 1;
			default -> oz + sizeZ / 2;
		};
		int floor = probe.groundY(fx, fz);
		if (probe.waterY(fx, fz) != TerrainProbe.NO_WATER) {
			floor = Math.max(floor, probe.waterY(fx, fz) + 1);
		}
		return new PlotSpec(info.id(), StyleRegistry.styleId(c), seed, new BlockPos(ox, floor, oz), sizeX, sizeZ, facing, Math.max(info.maxHeight(), 8), 2,
			new CompoundTag());
	}
}
