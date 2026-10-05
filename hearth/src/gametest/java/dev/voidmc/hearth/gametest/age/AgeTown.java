package dev.voidmc.hearth.gametest.age;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.age.Accretion;
import dev.voidmc.hearth.build.age.Decay;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.gametest.plan.IsoRenderer;
import dev.voidmc.hearth.gametest.plan.LayeredBlueprints;
import dev.voidmc.hearth.gametest.plan.Scene;
import dev.voidmc.hearth.plan.HearthPlanner;
import dev.voidmc.hearth.plan.PlanConfig;
import dev.voidmc.hearth.plan.PlanModule;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.testing.CatalogueDouble;
import dev.voidmc.hearth.plan.testing.MiniSim;
import java.io.File;
import java.util.Collection;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.jspecify.annotations.Nullable;

/**
 * A grown town seen three ways (docs/buildings/age.md), offline: as planned (every house as first built), grown (the
 * houses carry {@code additions}, more the nearer the old heart and the longer settled, by the plot's seed), and in
 * decline (a quarter on one side of the heart abandoned and falling in, {@code decay} rising outward). The planner and
 * the town are the ordinary ones (MiniSim, as {@code settlementPreview}); only the params on the houses' specs differ,
 * set where the blueprints are drawn.
 *
 * <pre>
 *   ./gradlew -I docs/buildings/age_preview.gradle ageTown -Pculture=temperate -Pdays=300 -Pterrain=hills -Pseed=2
 *   args: culture days terrain seed outDir
 * </pre>
 * Output: build/previews/age/agetown_&lt;culture&gt;_&lt;terrain&gt;_s&lt;seed&gt;_{planned,grown,decline}.png
 */
public final class AgeTown {
	private AgeTown() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		Culture culture = Culture.valueOf((args.length > 0 ? args[0] : "temperate").toUpperCase(Locale.ROOT));
		int days = Integer.parseInt(args.length > 1 ? args[1] : "300");
		String terrainName = args.length > 2 ? args[2] : "hills";
		long seed = Long.parseLong(args.length > 3 ? args[3] : "2");
		File out = new File(args.length > 4 ? args[4] : "build/previews/age");
		out.mkdirs();
		BlueprintService real = new LayeredBlueprints(dev.voidmc.hearth.build.BuildModule.standalone(), new CatalogueDouble());
		HearthServices.setBlueprints(real);
		TerrainProbe terrain = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrainName.toUpperCase(Locale.ROOT)), seed,
			culture == Culture.TUNDRA || culture == Culture.NORTHERN || culture == Culture.ALPINE ? 0.1F
				: culture == Culture.DESERT || culture == Culture.MESA ? 1.6F : 0.7F);
		BlockPos center = new BlockPos(0, terrain.groundY(0, 0), 0);
		MiniSim sim = new MiniSim(culture, terrain, seed, new HearthPlanner(PlanModule.STATE, PlanConfig.DEFAULTS), center);
		sim.run(days);
		PlanState state = sim.settlement.data(PlanModule.STATE);
		int hx = state.heartX;
		int hz = state.heartZ;
		int half = half(sim, hx, hz);
		String tag = culture.getSerializedName() + "_" + terrainName + "_s" + seed;
		for (String mode : new String[]{"planned", "grown", "decline"}) {
			Aged service = new Aged(real, mode, hx, hz, half);
			Scene scene = new Scene(terrain, hx - half, hz - half, 2 * half + 1, 2 * half + 1);
			scene.addSettlement(sim.settlement, terrain, Catalogue.current(), service);
			String title = String.format(Locale.ROOT, "%s on %s  ·  day %d  ·  %s  ·  %d houses grown, %d abandoned", cap(culture.getSerializedName()),
				terrainName, sim.day(), mode, service.grown, service.decayed);
			File f = new File(out, "agetown_" + tag + "_" + mode + ".png");
			ImageIO.write(new IsoRenderer(scene.facingDownhill(), 1900).render(title), "png", f);
			System.out.println("wrote " + f);
		}
	}

	private static int half(MiniSim sim, int hx, int hz) {
		java.util.List<Double> d = new java.util.ArrayList<>();
		for (PlotView p : sim.settlement.plots()) {
			var sp = p.spec();
			d.add(Math.max(Math.abs(sp.origin().getX() + sp.sizeX() / 2.0 - hx), Math.abs(sp.origin().getZ() + sp.sizeZ() / 2.0 - hz)));
		}
		java.util.Collections.sort(d);
		double r = d.isEmpty() ? 60 : d.get((int) Math.min(d.size() - 1, Math.floor(d.size() * 0.8)));
		return (int) Math.max(48, Math.min(160, r + 12));
	}

	private static String cap(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/** The real blueprints with the age params a town would give its houses. */
	static final class Aged implements BlueprintService {
		private final BlueprintService real;
		private final String mode;
		private final int hx;
		private final int hz;
		private final int half;
		int grown;
		int decayed;

		Aged(BlueprintService real, String mode, int hx, int hz, int half) {
			this.real = real;
			this.mode = mode;
			this.hx = hx;
			this.hz = hz;
			this.half = half;
		}

		@Override
		public Blueprint blueprint(PlotSpec spec, TerrainProbe terrain) {
			ArchetypeInfo info = this.real.archetype(spec.archetype());
			if (info == null || info.category() != ArchetypeCategory.HOUSING) {
				return this.real.blueprint(spec, terrain);
			}
			CompoundTag p = spec.params();
			double dx = spec.origin().getX() + spec.sizeX() / 2.0 - this.hx;
			double dz = spec.origin().getZ() + spec.sizeZ() / 2.0 - this.hz;
			double dist = Math.hypot(dx, dz) / Math.max(1, this.half);
			double u = Noise.unit(spec.seed(), 0xA6E, 0, 0);
			// every house that may grow carries the param from its first build (the yard it keeps), so the planned town
			// already shows the yards the grown town fills
			int additions = 0;
			if (!this.mode.equals("planned")) {
				// the old heart's houses have had longest to grow
				additions = (int) Math.round((1.0 - Math.min(1, dist)) * 4 * (0.4 + 0.8 * u));
			}
			p.putInt(Accretion.PARAM, additions);
			if (additions > 0) {
				this.grown++;
			}
			if (this.mode.equals("decline")) {
				// the quarter on the heart's east side, emptying outward
				double angle = Math.atan2(dz, dx);
				if (Math.abs(angle) < Math.PI / 3 && dist > 0.25) {
					double decay = Math.clamp((dist - 0.25) * 1.6 + (u - 0.5) * 0.3, 0.15, 1.0);
					p.putDouble(Decay.PARAM, decay);
					if (decay >= Decay.ABANDONED) {
						this.decayed++;
					}
				}
			}
			return this.real.blueprint(new PlotSpec(spec.archetype(), spec.style(), spec.seed(), spec.origin(), spec.sizeX(), spec.sizeZ(), spec.facing(),
				spec.maxHeight(), spec.tier(), p), terrain);
		}

		@Override
		public @Nullable ArchetypeInfo archetype(Identifier id) {
			return this.real.archetype(id);
		}

		@Override
		public Collection<ArchetypeInfo> archetypes() {
			return this.real.archetypes();
		}

		@Override
		public Identifier style(Culture culture) {
			return this.real.style(culture);
		}
	}
}
