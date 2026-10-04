package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.Silhouette;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.StepSink;
import dev.voidmc.hearth.api.testing.SimpleSettlement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/** Shared doubles for the repair tests: vanilla bootstrapped, a blueprint made of explicit steps, a settlement. */
final class RepairTestKit {
	private static boolean booted;

	private RepairTestKit() {
	}

	static synchronized void boot() {
		if (!booted) {
			SharedConstants.tryDetectVersion();
			Bootstrap.bootStrap();
			booted = true;
		}
	}

	record Step(int x, int y, int z, BlockState state, @Nullable CompoundTag tag) {
	}

	/** A blueprint that is just its steps, stage by stage. */
	static final class StepBlueprint implements Blueprint {
		final PlotSpec spec;
		final List<List<Step>> stages = new ArrayList<>();
		final List<BlockPos> entrances = new ArrayList<>();
		int chunkCalls;

		StepBlueprint(PlotSpec spec) {
			this.spec = spec;
		}

		StepBlueprint stage() {
			this.stages.add(new ArrayList<>());
			return this;
		}

		StepBlueprint put(int x, int y, int z, BlockState state) {
			this.stages.getLast().add(new Step(x, y, z, state, null));
			return this;
		}

		@Override
		public PlotSpec spec() {
			return this.spec;
		}

		@Override
		public BoundingBox bounds() {
			BoundingBox box = null;
			for (List<Step> st : this.stages) {
				for (Step s : st) {
					BlockPos p = new BlockPos(s.x, s.y, s.z);
					if (box == null) {
						box = new BoundingBox(p);
					} else {
						box.encapsulate(p);
					}
				}
			}
			return box == null ? new BoundingBox(this.spec.origin()) : box;
		}

		@Override
		public int stageCount() {
			return this.stages.size();
		}

		@Override
		public StageKind stageKind(int stage) {
			return StageKind.values()[Math.min(stage, StageKind.values().length - 1)];
		}

		@Override
		public int stepCount(int stage) {
			return this.stages.get(stage).size();
		}

		@Override
		public void step(int stage, int index, StepSink sink) {
			Step s = this.stages.get(stage).get(index);
			sink.accept(s.x, s.y, s.z, s.state, s.tag);
		}

		@Override
		public void forEachInChunk(int chunkX, int chunkZ, int fromStage, int toStage, StepSink sink) {
			this.chunkCalls++;
			for (int st = fromStage; st < toStage && st < this.stages.size(); st++) {
				for (Step s : this.stages.get(st)) {
					if (s.x >> 4 == chunkX && s.z >> 4 == chunkZ) {
						sink.accept(s.x, s.y, s.z, s.state, s.tag);
					}
				}
			}
		}

		@Override
		public int work(int stage) {
			return stepCount(stage);
		}

		@Override
		public int[] goods(int stage) {
			return new int[Goods.VALUES.length];
		}

		@Override
		public List<Poi> pois() {
			return List.of();
		}

		@Override
		public List<BlockPos> entrances() {
			return this.entrances;
		}

		@Override
		public int beds() {
			return 0;
		}

		@Override
		public Silhouette silhouette() {
			return null;
		}
	}

	/** Blueprints by plot id, and which archetypes are excavations. */
	static final class Source implements PlotLocator.Source {
		final Map<Integer, Blueprint> blueprints = new HashMap<>();
		int asked;

		@Override
		public @Nullable Blueprint blueprint(Settlement settlement, PlotView plot) {
			this.asked++;
			return this.blueprints.get(plot.id());
		}

		@Override
		public boolean extraction(PlotSpec spec) {
			return spec.archetype().getPath().startsWith("extract/");
		}
	}

	static PlotSpec spec(String archetype, int x, int y, int z, int sx, int sz) {
		return new PlotSpec(Hearth.id(archetype), Hearth.id("temperate"), 1, new BlockPos(x, y, z), sx, sz, Direction.SOUTH, 12, 1, new CompoundTag());
	}

	static SimpleSettlement settlement() {
		return new SimpleSettlement(7, "Testford", Culture.values()[0], new BlockPos(0, 64, 0), 0);
	}

	static Identifier id(String path) {
		return Hearth.id(path);
	}
}
