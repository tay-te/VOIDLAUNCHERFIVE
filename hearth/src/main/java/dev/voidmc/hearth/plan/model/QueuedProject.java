package dev.voidmc.hearth.plan.model;

import dev.voidmc.hearth.api.PlotSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

/**
 * A project the planner has decided on but not yet handed to the sim. Planning one need can make several plots — the
 * lane first, then the row of houses along it — and {@code next} hands them out one per call, in order, so roads are
 * always started before what stands on them.
 */
public record QueuedProject(PlotSpec spec, int replaces, String reason) {
	CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.put("spec", PlotSpec.CODEC.encodeStart(NbtOps.INSTANCE, this.spec).getOrThrow());
		t.putInt("replaces", this.replaces);
		t.putString("reason", this.reason);
		return t;
	}

	static QueuedProject load(CompoundTag t) {
		PlotSpec spec = PlotSpec.CODEC.parse(NbtOps.INSTANCE, t.get("spec")).getOrThrow();
		return new QueuedProject(spec, t.getIntOr("replaces", -1), t.getStringOr("reason", ""));
	}
}
