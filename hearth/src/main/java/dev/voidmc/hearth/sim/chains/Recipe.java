package dev.voidmc.hearth.sim.chains;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * One link of a production chain (docs/LIVING_WORLD.md §7.5), as data in {@code data/<ns>/hearth_sim/recipes/*.json}:
 * <pre>
 * { "job": "smelter", "workplace": "hearth:work/smelter",
 *   "inputs": {"ore": 4, "fuel": 2}, "outputs": {"metal": 3}, "labour": 1.0 }
 * </pre>
 * A batch takes {@code labour} worker-days and turns the inputs into the outputs. With {@code workplace} the recipe is
 * followed only by workers whose workplace is that archetype; with no {@code job}, by every worker there.
 *
 * @param tools    tools worn per batch (negative: the config's default per worker-day)
 * @param share    the most of a worker's day this recipe may take (hand work done between other work: 0.3)
 * @param target   the recipe runs while each output is below this share of the stores' capacity for it
 * @param priority higher is tried first among a worker's recipes
 */
public record Recipe(Identifier id, @Nullable JobType job, @Nullable Identifier workplace, double[] inputs, double[] outputs, double labour,
	double tools, double share, double target, int priority) {

	public static final Codec<Recipe> CODEC = RecordCodecBuilder.create(i -> i.group(
		Identifier.CODEC.fieldOf("id").forGetter(Recipe::id),
		JobType.CODEC.optionalFieldOf("job").forGetter(r -> Optional.ofNullable(r.job)),
		Identifier.CODEC.optionalFieldOf("workplace").forGetter(r -> Optional.ofNullable(r.workplace)),
		SimData.GOODS.optionalFieldOf("inputs", new double[Goods.VALUES.length]).forGetter(Recipe::inputs),
		SimData.GOODS.fieldOf("outputs").forGetter(Recipe::outputs),
		Codec.doubleRange(0.001, 1000).optionalFieldOf("labour", 1.0).forGetter(Recipe::labour),
		Codec.DOUBLE.optionalFieldOf("tools", -1.0).forGetter(Recipe::tools),
		Codec.doubleRange(0, 1).optionalFieldOf("share", 1.0).forGetter(Recipe::share),
		Codec.doubleRange(0, 1).optionalFieldOf("target", 0.9).forGetter(Recipe::target),
		Codec.INT.optionalFieldOf("priority", 0).forGetter(Recipe::priority)
	).apply(i, (id, job, workplace, in, out, labour, tools, share, target, priority) -> new Recipe(id, job.orElse(null), workplace.orElse(null), in,
		out, labour, tools, share, target, priority)));

	public Recipe {
		if (job == null && workplace == null) {
			throw new IllegalArgumentException("recipe " + id + " names neither a job nor a workplace");
		}
	}

	/** Whether every output is already at its target share of the stores. */
	boolean satisfied(int[] stock, int[] capacity) {
		for (int g = 0; g < this.outputs.length; g++) {
			if (this.outputs[g] > 0 && stock[g] < capacity[g] * this.target) {
				return false;
			}
		}
		return true;
	}

	/** How badly the outputs are wanted: the lowest fill of any output (0 empty, 1 full). Lower goes first. */
	double fill(int[] stock, int[] capacity) {
		double fill = 1;
		for (int g = 0; g < this.outputs.length; g++) {
			if (this.outputs[g] > 0 && capacity[g] > 0) {
				fill = Math.min(fill, stock[g] / (double) capacity[g]);
			}
		}
		return fill;
	}
}
