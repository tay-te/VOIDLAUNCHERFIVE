package dev.voidmc.hearth.gametest.deep;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.archetype.Archetypes;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.check.BlueprintChecks;
import dev.voidmc.hearth.build.kit.deep.DeepTerrain;
import dev.voidmc.hearth.build.style.StyleRegistry;
import dev.voidmc.hearth.gametest.render.Assets;
import dev.voidmc.hearth.gametest.render.IsoRenderer;
import dev.voidmc.hearth.gametest.render.IsoRenderer.View;
import dev.voidmc.hearth.gametest.render.ModelLibrary;
import dev.voidmc.hearth.gametest.render.NightLight;
import dev.voidmc.hearth.gametest.render.NightPanels;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;

/**
 * A street of a deep town in a synthetic realm (LIVING_WORLD.md §8.2), laid out the way the deep planner lays one out
 * ({@code plan.deep.DeepTown}): a row of dwellings cut into the foot of the realm's wall facing the street, two of them
 * with a second dwelling stacked on their roofs and set back into the rock, the forge among them; across the street the
 * hall, the market, the lift's tower rising to the vault and a glow garden. Every plot gets the deep planner's params
 * ({@code realm}, {@code character}, {@code local}, {@code ceiling_y}); every blueprint is checked. Rendered by day from
 * the street and from behind the hall, and lit only by its own light under the vault. Run through
 * {@code docs/buildings/deep_preview.gradle}:
 *
 * <pre>
 *   ./gradlew -I docs/buildings/deep_preview.gradle deepTown -Pstyle=temperate -Pseed=3 -Pcharacter=wilds
 *   args: style  seed  character  outDir  [params]
 * </pre>
 */
public final class DeepTown {
	private DeepTown() {
	}

	private record Plot(String id, int x0, int z0, int frontage, int depth, Direction facing, CompoundTag extra) {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		String styleArg = args.length > 0 ? args[0] : "temperate";
		long seed = args.length > 1 ? Long.parseLong(args[1]) : 1;
		String character = args.length > 2 ? args[2] : "wilds";
		Path out = Path.of(args.length > 3 ? args[3] : "build/previews/deep");
		Files.createDirectories(out);
		ArchetypeRegistry registry = new ArchetypeRegistry();
		Archetypes.registerAll(registry);
		BlueprintServiceImpl service = new BlueprintServiceImpl(registry.freeze(), StyleRegistry.builtin());
		IsoRenderer renderer = new IsoRenderer(new ModelLibrary(new Assets()));
		for (String styleName : styleArg.split(",")) {
			Culture culture = Culture.valueOf(styleName.toUpperCase(Locale.ROOT));
			render(service, renderer, culture, seed, character, out);
		}
	}

	private static void render(BlueprintServiceImpl service, IsoRenderer renderer, Culture culture, long seed, String character, Path out) throws Exception {
		// the realm's wall to the north, its face some six blocks north of the street's near side
		DeepTerrain dt = new DeepTerrain(DeepTerrain.Kind.WALL, 42, Direction.NORTH, 6);
		List<Plot> plots = new ArrayList<>();
		CompoundTag none = new CompoundTag();
		// the wall side: dwellings in a row facing the street, their backs cut into the rubble at the wall's foot; the forge
		int[] rowA = {-44, -35, -26, -17};
		for (int i = 0; i < rowA.length; i++) {
			CompoundTag p = new CompoundTag();
			p.putBoolean("party_left", i > 0);
			p.putBoolean("party_right", i < rowA.length - 1);
			plots.add(new Plot("deep/dwelling", rowA[i], -9, 9, 9, Direction.SOUTH, p));
		}
		plots.add(new Plot("deep/forge", -7, -11, 11, 11, Direction.SOUTH, none));
		int[] rowB = {5, 14};
		for (int i = 0; i < rowB.length; i++) {
			CompoundTag p = new CompoundTag();
			p.putBoolean("party_left", i > 0);
			p.putBoolean("party_right", i < rowB.length - 1);
			plots.add(new Plot("deep/dwelling", rowB[i], -9, 9, 9, Direction.SOUTH, p));
		}
		// across the street, facing it: the hall, the market, the lift, the glow garden
		plots.add(new Plot("deep/hall", -31, 4, 23, 25, Direction.NORTH, none));
		plots.add(new Plot("deep/market", -6, 4, 15, 15, Direction.NORTH, none));
		CompoundTag lift = new CompoundTag();
		lift.putString("deep_facing", "north");
		plots.add(new Plot("deep/lift", 11, 5, 11, 11, Direction.NORTH, lift));
		plots.add(new Plot("deep/glow_garden", 24, 4, 17, 17, Direction.NORTH, none));

		List<BlueprintImpl> built = new ArrayList<>();
		List<PlotSpec> specs = new ArrayList<>();
		int k = 0;
		for (Plot pl : plots) {
			PlotSpec spec = spec(service, pl, culture, seed * 1000 + k++, dt, character);
			specs.add(spec);
		}
		// two dwellings stacked on the row's roofs, set back half their depth into the rock
		for (int i : new int[]{1, 2}) {
			PlotSpec below = specs.get(i);
			int set = Math.max(2, below.depth() / 2);
			CompoundTag p = below.params().copy();
			p.putBoolean("stacked", true);
			p.putInt("level", 1);
			p.putInt("street_y", below.floorY() + below.maxHeight());
			int floor = below.floorY() + below.maxHeight();
			int ceiling = p.getIntOr("ceiling_y", floor + 40);
			int height = Math.max(7, Math.min(9, ceiling - floor - 1));
			p.putInt("ceiling_y", dt.ceilingOver(below.origin().getX(), below.origin().getZ() - set, below.origin().getX() + below.sizeX() - 1,
				below.origin().getZ() - set + below.sizeZ() - 1));
			specs.add(new PlotSpec(below.archetype(), below.style(), below.seed() + 77, new BlockPos(below.origin().getX(), floor, below.origin().getZ() - set),
				below.sizeX(), below.sizeZ(), below.facing(), height, below.tier(), p));
		}
		for (PlotSpec spec : specs) {
			BlueprintImpl bp = service.compiled(spec, dt);
			for (String problem : BlueprintChecks.all(bp, dt)) {
				// a stacked dwelling's street is the terrace of the roof below, a step under its floor (the check looks for it
				// at the floor itself, in the air)
				if (spec.params().getBooleanOr("stacked", false) && problem.contains("from the street")) {
					continue;
				}
				System.out.println("CHECK " + spec.archetype().getPath() + " at " + spec.origin().toShortString() + ": " + problem);
			}
			built.add(bp);
		}
		int x0 = -48;
		int x1 = 44;
		int z0 = -20;
		int z1 = 32;
		DeepScene scene = DeepScene.build(dt, x0, z0, x1, z1, DeepTerrain.FLOOR - 4, character, dt::ceilingY);
		for (BlueprintImpl bp : built) {
			scene.apply(bp);
		}
		// the render looks in under the vault: the lift's shaft is cut where it enters the rock
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = dt.ceilingY(x, z); y < dt.ceilingY(x, z) + 120; y++) {
					scene.world.set(x, y, z, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				}
			}
		}
		String base = "town_" + culture.getSerializedName() + "_" + character + "_s" + seed;
		BufferedImage front = renderer.render(scene.world, new View(25, 30), 11, 2, 0);
		// the street's side: the realm's wall cut down to a stub so the render can look over it at the fronts of the row
		DeepScene cut = DeepScene.build(new Stub(dt, DeepTerrain.FLOOR + 2), x0, z0, x1, z1, DeepTerrain.FLOOR - 4, character, null);
		for (BlueprintImpl bp : built) {
			cut.apply(bp);
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = dt.ceilingY(x, z); y < dt.ceilingY(x, z) + 120; y++) {
					cut.world.set(x, y, z, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				}
			}
		}
		BufferedImage back = renderer.render(cut.world, new View(200, 34), 11, 2, 0);
		NightLight light = NightLight.of(scene.sealed);
		BufferedImage lit = renderer.render(scene.world, new View(25, 30), 11, 2, NightPanels.BG, light);
		String title = "deep town  " + culture.getSerializedName() + "  " + character + "  seed " + seed;
		ImageIO.write(DeepPreview.panel(List.of(front), List.of(), List.of(), title + "  (from the street)", 0xFFE9E4DA), "png",
			out.resolve(base + ".png").toFile());
		ImageIO.write(DeepPreview.panel(List.of(back), List.of(), List.of(), title + "  (from the wall side, the realm's wall cut away)", 0xFFE9E4DA), "png",
			out.resolve(base + "_back.png").toFile());
		ImageIO.write(DeepPreview.panel(List.of(lit), List.of(), List.of(), title + "  (lit by its own light under the vault)", NightPanels.BG), "png",
			out.resolve(base + "_lit.png").toFile());
		System.out.println("rendered " + base + " to " + out.toAbsolutePath());
	}

	/** The realm's ground with its wall cut down to a stub (a cutaway for the render; the blueprints are drawn on the real one). */
	private record Stub(DeepTerrain base, int cap) implements dev.voidmc.hearth.api.TerrainProbe {
		@Override
		public long seed() {
			return this.base.seed();
		}

		@Override
		public int minY() {
			return this.base.minY();
		}

		@Override
		public int maxY() {
			return this.base.maxY();
		}

		@Override
		public int seaLevel() {
			return this.base.seaLevel();
		}

		@Override
		public int groundY(int x, int z) {
			return Math.min(this.cap, this.base.groundY(x, z));
		}

		@Override
		public int waterY(int x, int z) {
			return NO_WATER;
		}

		@Override
		public net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> biome(int x, int z) {
			return null;
		}

		@Override
		public float temperature(int x, int z) {
			return 0.8F;
		}
	}

	/** A plot as the deep planner gives it: the floor where its front meets the street, the vault over it, the realm's params. */
	private static PlotSpec spec(BlueprintServiceImpl service, Plot pl, Culture culture, long seed, DeepTerrain dt, String character) {
		ArchetypeInfo info = service.archetype(Hearth.id(pl.id()));
		boolean zAxis = pl.facing().getAxis() == Direction.Axis.Z;
		int sizeX = zAxis ? pl.frontage() : pl.depth();
		int sizeZ = zAxis ? pl.depth() : pl.frontage();
		int fx = pl.x0() + sizeX / 2;
		int fz = pl.facing() == Direction.SOUTH ? pl.z0() + sizeZ : pl.facing() == Direction.NORTH ? pl.z0() - 1 : pl.z0() + sizeZ / 2;
		int floor = dt.groundY(fx, fz);
		CompoundTag params = pl.extra().copy();
		params.putLong("realm", 7L);
		params.putString("character", character);
		params.put("local", dev.voidmc.hearth.plan.resource.Materials.realm(character));
		int height;
		if (pl.id().equals("deep/lift")) {
			int below = 64;
			params.putInt("bottom_y", floor);
			params.putInt("top_y", floor + below);
			params.putInt("street_y", floor + below);
			params.putInt("deep_street_y", floor);
			params.putInt("ceiling_y", dt.ceilingOver(pl.x0(), pl.z0(), pl.x0() + sizeX - 1, pl.z0() + sizeZ - 1));
			height = below + 10;
		} else {
			int ceiling = dt.ceilingOver(pl.x0(), pl.z0(), pl.x0() + sizeX - 1, pl.z0() + sizeZ - 1);
			params.putInt("ceiling_y", ceiling);
			height = Math.min(info.maxHeight(), Math.max(info.minHeight(), ceiling - floor - 1));
		}
		return new PlotSpec(info.id(), StyleRegistry.styleId(culture), seed, new BlockPos(pl.x0(), floor, pl.z0()), sizeX, sizeZ, pl.facing(), height, 2,
			params);
	}
}
