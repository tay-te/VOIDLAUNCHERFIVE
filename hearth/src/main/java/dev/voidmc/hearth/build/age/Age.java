package dev.voidmc.hearth.build.age;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.build.terrain.Site;
import net.minecraft.core.BlockPos;

/**
 * What time does to a building, applied to every drawing after its archetype has drawn it and before it compiles
 * (the hooks into the pipeline, in {@code BlueprintServiceImpl.draw}: {@link #draw} in place of the archetype's own draw, then {@link #apply}): first {@link Accretion} (the additions a
 * household made over generations: the plot param {@code additions}, housing only), then {@link Decay} (the plot param
 * {@code decay}, any archetype). Both are no-ops without their params, so every other blueprint is exactly what its
 * archetype drew. Dressings for special days are not part of the blueprint at all: see {@link Dressings}.
 */
public final class Age {
	private Age() {
	}

	/**
	 * Draws the archetype onto the draft as the house was first built: on the whole plot, or, for a housing plot that
	 * carries {@code additions}, on the part of it {@link Accretion#reserve} leaves after keeping a yard to grow into.
	 */
	public static void draw(Archetype a, Draft d, Style style, TerrainProbe terrain) {
		int[] r = Accretion.reserve(a.info(), d.spec());
		if (r == null) {
			a.draw(d);
			return;
		}
		PlotSpec spec = d.spec();
		LocalFrame f = d.frame();
		int wx0 = Math.min(f.worldX(r[0], r[1]), f.worldX(r[2], r[3]));
		int wz0 = Math.min(f.worldZ(r[0], r[1]), f.worldZ(r[2], r[3]));
		int fr = r[2] - r[0] + 1;
		int dp = r[3] - r[1] + 1;
		boolean zAxis = spec.facing().getAxis() == net.minecraft.core.Direction.Axis.Z;
		PlotSpec inner = new PlotSpec(spec.archetype(), spec.style(), spec.seed(), new BlockPos(wx0, spec.floorY(), wz0), zAxis ? fr : dp,
			zAxis ? dp : fr, spec.facing(), spec.maxHeight(), spec.tier(), Accretion.innerParams(spec, r));
		Draft first = new Draft(inner, style, new Site(inner, terrain));
		a.draw(first);
		d.absorb(first, r[0], r[1]);
	}

	/** Applies the plot's additions and decay to a finished drawing. */
	public static void apply(ArchetypeInfo info, Draft d) {
		Accretion.apply(info, d);
		Decay.apply(d, Decay.level(d.spec()));
	}
}
