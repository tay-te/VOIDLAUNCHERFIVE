package dev.voidmc.lod.gui;

import dev.voidmc.lod.VoidLod;
import dev.voidmc.lod.core.AutoTune;
import dev.voidmc.lod.core.LodEngine;
import dev.voidmc.lod.render.LodRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * The LOD's lines on the F3 screen: what it draws and holds, what that costs a frame, and the detail it draws
 * at. Shown in the F3 overlay by default (see {@code DebugScreenEntryListMixin}), and listed as
 * {@code void_lod} in the debug options (which show an entry's path alone) to move or hide.
 */
public final class LodDebugEntry implements DebugScreenEntry {
	/** A literal rather than {@link VoidLod#ID}: the mixin asks for it before the mod has started. */
	public static final Identifier ID = Identifier.fromNamespaceAndPath("void_lod", "void_lod");

	public static void register() {
		DebugScreenEntries.register(ID, new LodDebugEntry());
	}

	@Override
	public void display(DebugScreenDisplayer displayer, @Nullable Level level, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
		VoidLod lod = VoidLod.get();
		if (lod != null && lod.engine() != null) {
			displayer.addToGroup(ID, lines(lod));
		}
	}

	@Override
	public boolean isAllowed(boolean reducedDebugInfo) {
		// nothing here about the world that reduced debug info hides
		return true;
	}

	static List<String> lines(VoidLod lod) {
		List<String> lines = new ArrayList<>(3);
		LodEngine e = lod.engine();
		LodRenderer r = lod.renderer();
		if (!lod.isEnabled() || !lod.active()) {
			lines.add("VOID LOD: " + (lod.isEnabled() ? "nothing to sample here" : "off"));
			return lines;
		}
		lines.add(String.format(Locale.ROOT, "VOID LOD: %d tiles drawn, %,d quads; %d cached, %.0f MB; %d queued", r != null ? r.lastTiles : 0,
			r != null ? r.lastQuads : 0, e.tileCount(), e.gpuBytes() / 1048576.0, e.queued()));
		if (r != null) {
			lines.add(String.format(Locale.ROOT, "LOD frame: GPU %s, prepare %.2f ms, %d workers", r.gpuMillis < 0 ? "n/a" : String.format(Locale.ROOT, "%.2f ms", r.gpuMillis),
				r.lastPrepareNanos / 1e6, e.threads()));
		}
		AutoTune tuner = lod.tuner();
		String tune = !lod.settings().autoTune ? "off"
			: (tuner.step() == 0 ? "full detail" : "step " + tuner.step()) + (tuner.holding ? ", game at its frame cap" : "");
		lines.add(String.format(Locale.ROOT, "LOD %s: reach %d, detail %.1f px (%.1f far), auto-tune %s", lod.settings().preset.serializedName(), (int) lod.radius,
			lod.detail, lod.farDetail, tune));
		return lines;
	}
}
