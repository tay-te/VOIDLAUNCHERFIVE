package dev.voidmc.reel.client;

import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Whether the world around the camera is fully drawn: no chunks still on their way from the server, no sections
 * waiting to be meshed, every visible section uploaded. The director waits for this before a shot's first frame
 * (and, where it can, before each frame) so nothing pops in on camera.
 *
 * <p>Only sections within a circle a little short of the render distance count: the server sends chunks in a circle,
 * and vanilla meshes a section only once all its neighbours are loaded, so sections along the edge (and in the
 * corners of the square the client keeps) stay unmeshed for good.
 */
final class Readiness {
	private static final Method SODIUM_INSTANCE;
	private static final Method SODIUM_COMPLETE;
	private static final Method LOD_ACTIVE;
	private static final Method LOD_COMPLETE;
	/** Chunks short of the render distance a section must be to count. */
	private static final int EDGE = 2;

	static {
		Method instance = null;
		Method complete = null;
		try {
			Class<?> renderer = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
			instance = renderer.getMethod("instanceNullable");
			complete = renderer.getMethod("isTerrainRenderComplete");
		} catch (ReflectiveOperationException | LinkageError ignored) {
			// no Sodium: vanilla's sections are the ones drawn
		}
		SODIUM_INSTANCE = instance;
		SODIUM_COMPLETE = complete;
		Method active = null;
		Method far = null;
		try {
			Class<?> lod = Class.forName("dev.voidmc.lod.api.LodApi");
			active = lod.getMethod("active");
			far = lod.getMethod("complete");
		} catch (ReflectiveOperationException | LinkageError ignored) {
			// no VOID LOD: nothing beyond the chunks to wait for
		}
		LOD_ACTIVE = active;
		LOD_COMPLETE = far;
	}

	/** VOID LOD's far terrain around the camera: true without LOD, or once every far tile the view wants is drawn. */
	static boolean farComplete() {
		if (LOD_ACTIVE == null) {
			return true;
		}
		try {
			return !(boolean) LOD_ACTIVE.invoke(null) || (boolean) LOD_COMPLETE.invoke(null);
		} catch (ReflectiveOperationException e) {
			return true;
		}
	}

	/** Whether VOID LOD is loaded and drawing. */
	static boolean lodPresent() {
		try {
			return LOD_ACTIVE != null && (boolean) LOD_ACTIVE.invoke(null);
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	/** Why VOID LOD is not done, from its own engine (by reflection: Reel does not depend on LOD), or "". */
	static String farState() {
		if (LOD_ACTIVE == null) {
			return "";
		}
		try {
			Class<?> lod = Class.forName("dev.voidmc.lod.VoidLod");
			Object instance = lod.getMethod("get").invoke(null);
			if (instance == null) {
				return "lod absent";
			}
			Object engine = lod.getMethod("engine").invoke(instance);
			Object busy = engine.getClass().getMethod("busy").invoke(engine);
			Object queued = engine.getClass().getMethod("queued").invoke(engine);
			Object missing = engine.getClass().getMethod("terrainMissing").invoke(engine);
			Object trees = lod.getMethod("treesPending").invoke(instance);
			return "lod queued " + queued + ", terrain missing " + missing + ", trees pending " + trees + ", busy: " + busy;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return "lod state unknown: " + e;
		}
	}

	private Readiness() {
	}

	/**
	 * Whether what is still loading needs the server: chunks it has yet to send, counted two ways (the chunks the
	 * section graph is waiting on, and the neighbours an unmeshed section in view lacks, without which vanilla never
	 * meshes it). Those arrive only while the server ticks; anything else is meshing, which finishes on its own.
	 */
	static boolean waitingOnServer(Minecraft mc) {
		return missingInside(mc) > 0 || expectedInside(mc) > 0 || uncompiled(mc, true) > 0;
	}

	/**
	 * Chunks inside the counted circle the client does not have yet. Asked of the client's own chunk cache, so it holds
	 * whatever draws the terrain (Sodium does not use vanilla's section graph, which is what knows the other ways).
	 */
	private static int missingInside(Minecraft mc) {
		ChunkPos centre = ChunkPos.containing(mc.gameRenderer.mainCamera().blockPosition());
		int reach = reach(mc);
		int missing = 0;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (inside(dx, dz, reach) && !mc.level.getChunkSource().hasChunk(centre.x() + dx, centre.z() + dz)) {
					missing++;
				}
			}
		}
		return missing;
	}

	/** The whole view, far terrain included: what the first frame waits for. */
	static boolean ready(Minecraft mc) {
		return farComplete() && nearReady(mc);
	}

	/** The chunks in view: meshed and uploaded. */
	static boolean nearReady(Minecraft mc) {
		if (mc.level == null) {
			return false;
		}
		if (missingInside(mc) > 0) {
			return false;
		}
		Boolean sodium = sodium();
		if (sodium != null) {
			return sodium;
		}
		return expectedInside(mc) == 0 && mc.levelRenderer.hasRenderedAllSections() && uncompiled(mc, false) == 0;
	}

	/** For reports: what is still loading. */
	static String state(Minecraft mc) {
		if (mc.level == null) {
			return "no level";
		}
		String far = LOD_ACTIVE == null ? "" : "; far terrain " + (farComplete() ? "complete" : "building (" + farState() + ")");
		Boolean sodium = sodium();
		if (sodium != null) {
			return "chunks missing " + missingInside(mc) + ", sodium terrain " + (sodium ? "complete" : "incomplete") + far;
		}
		LevelRenderer renderer = mc.levelRenderer;
		SectionRenderDispatcher dispatcher = renderer.sectionRenderDispatcher();
		return String.format(Locale.ROOT, "expected chunks %d (%d inside), compile queue %s, unmeshed visible sections inside %d (%d lacking neighbours)",
			renderer.expectedChunks().size(), expectedInside(mc), dispatcher == null ? "none" : Integer.toString(dispatcher.getCompileQueueSize()),
			uncompiled(mc, false), uncompiled(mc, true)) + lacking(mc) + far;
	}

	/** Where the first few sections lacking a neighbour are, relative to the camera's chunk, and what they lack. */
	private static String lacking(Minecraft mc) {
		ChunkPos centre = ChunkPos.containing(mc.gameRenderer.mainCamera().blockPosition());
		int reach = reach(mc);
		StringBuilder b = new StringBuilder();
		int shown = 0;
		for (SectionRenderDispatcher.RenderSection section : mc.levelRenderer.visibleSections()) {
			if (section.sectionMesh.get() != CompiledSectionMesh.UNCOMPILED || shown >= 4) {
				continue;
			}
			long node = section.getSectionNode();
			int x = SectionPos.x(node);
			int z = SectionPos.z(node);
			if (!inside(x - centre.x(), z - centre.z(), reach) || neighbours(mc, x, z)) {
				continue;
			}
			b.append(shown == 0 ? "; at" : ",").append(" (").append(x - centre.x()).append(' ').append(SectionPos.y(node)).append(' ')
				.append(z - centre.z()).append(") lacks");
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (!mc.level.getChunkSource().hasChunk(x + dx, z + dz)) {
						b.append(' ').append(dx).append('/').append(dz);
					}
				}
			}
			shown++;
		}
		return b.toString();
	}

	private static Boolean sodium() {
		if (SODIUM_INSTANCE == null) {
			return null;
		}
		try {
			Object sodium = SODIUM_INSTANCE.invoke(null);
			return sodium == null ? null : (Boolean) SODIUM_COMPLETE.invoke(sodium);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	/** Within the counted circle: the server sends, and the client draws, chunks in a circle around the camera. */
	private static boolean inside(int dx, int dz, int reach) {
		return dx * dx + dz * dz <= reach * reach;
	}

	private static int reach(Minecraft mc) {
		return Math.max(1, mc.options.getEffectiveRenderDistance() - EDGE);
	}

	private static int expectedInside(Minecraft mc) {
		ChunkPos centre = ChunkPos.containing(mc.gameRenderer.mainCamera().blockPosition());
		int reach = reach(mc);
		int count = 0;
		for (long packed : mc.levelRenderer.expectedChunks()) {
			if (inside(ChunkPos.getX(packed) - centre.x(), ChunkPos.getZ(packed) - centre.z(), reach)) {
				count++;
			}
		}
		return count;
	}

	/** Unmeshed sections in view inside the counted area; with lackingNeighbours, only those missing a neighbour chunk. */
	private static int uncompiled(Minecraft mc, boolean lackingNeighbours) {
		ChunkPos centre = ChunkPos.containing(mc.gameRenderer.mainCamera().blockPosition());
		int reach = reach(mc);
		int count = 0;
		for (SectionRenderDispatcher.RenderSection section : mc.levelRenderer.visibleSections()) {
			if (section.sectionMesh.get() != CompiledSectionMesh.UNCOMPILED) {
				continue;
			}
			long node = section.getSectionNode();
			int x = SectionPos.x(node);
			int z = SectionPos.z(node);
			if (!inside(x - centre.x(), z - centre.z(), reach)) {
				continue;
			}
			if (!lackingNeighbours || !neighbours(mc, x, z)) {
				count++;
			}
		}
		return count;
	}

	private static boolean neighbours(Minecraft mc, int x, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!mc.level.getChunkSource().hasChunk(x + dx, z + dz)) {
					return false;
				}
			}
		}
		return true;
	}
}
