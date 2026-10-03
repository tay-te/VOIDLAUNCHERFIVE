package dev.voidmc.hearth.npc.dev;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import dev.voidmc.hearth.npc.place.BlueprintSource;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The dev sandbox's settlement: {@link SimpleSettlement} plus the parts of the sim the npc module needs to be tried out
 * on its own — always embodied, and its plots know their (sandbox) blueprints and advance stage by stage as builders
 * place steps, the way the sim will. Lives in memory only. After integration the real sim replaces all of this.
 */
public final class SandboxSettlement extends SimpleSettlement implements BlueprintSource {
	private final Map<Integer, Blueprint> blueprints = new HashMap<>();

	public SandboxSettlement(long id, String name, Culture culture, BlockPos center, long foundedDay) {
		super(id, name, culture, center, foundedDay);
		setEmbodied(true);
	}

	public SimplePlot addPlot(Blueprint blueprint, PlotState state) {
		SimplePlot plot = addPlot(blueprint.spec());
		plot.state = state;
		if (state == PlotState.BUILT) {
			plot.stage = blueprint.stageCount();
		}
		this.blueprints.put(plot.id(), blueprint);
		return plot;
	}

	@Override
	public @Nullable Blueprint blueprint(PlotView plot) {
		return this.blueprints.get(plot.id());
	}

	/** As the sim does it: the step counter moves on, the stage when its steps are done, and BUILT at the end. */
	@Override
	public void advanceConstruction(int plotId, int steps) {
		SimplePlot plot = plot(plotId);
		Blueprint bp = this.blueprints.get(plotId);
		if (plot == null || bp == null) {
			super.advanceConstruction(plotId, steps);
			return;
		}
		if (plot.state == PlotState.PLANNED) {
			plot.state = PlotState.BUILDING;
		}
		plot.step += steps;
		while (plot.stage < bp.stageCount() && plot.step >= bp.stepCount(plot.stage)) {
			plot.step -= bp.stepCount(plot.stage);
			plot.stage++;
		}
		if (plot.stage >= bp.stageCount()) {
			plot.stage = bp.stageCount();
			plot.step = 0;
			plot.state = PlotState.BUILT;
		}
		markDirty();
	}
}
