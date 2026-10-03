package dev.voidmc.lod.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The materials far terrain is made of: one small id per block face the LOD has met (a block state's top or
 * its side), so a column can say "grass top over dirt over stone" in a few ints and the renderer can texture
 * close tiles with the faces themselves. Ids are dense from 1 (0 is "unknown"), handed out once per world as
 * sources and chunk ingestion first ask for them, and never reused, so a built tile's ids stay valid.
 *
 * <p>Thread-safe: workers and the client thread both ask.
 */
public final class Materials {
	/** One face of one block state. */
	public record Face(BlockState state, boolean top) {
	}

	private final Map<Face, Integer> ids = new ConcurrentHashMap<>();
	private final List<Face> faces = new ArrayList<>();

	public Materials() {
		this.faces.add(null);
	}

	/** The id of {@code state}'s top face ({@code top}) or side face, handed out on first ask. */
	public int id(BlockState state, boolean top) {
		Face face = new Face(state, top);
		Integer id = this.ids.get(face);
		if (id != null) {
			return id;
		}
		synchronized (this.faces) {
			return this.ids.computeIfAbsent(face, f -> {
				this.faces.add(f);
				return this.faces.size() - 1;
			});
		}
	}

	/** The face behind {@code id}, or null for 0 or an id this registry never handed out. */
	public Face face(int id) {
		synchronized (this.faces) {
			return id > 0 && id < this.faces.size() ? this.faces.get(id) : null;
		}
	}

	/** How many ids have been handed out, 0 included: valid ids are below this. */
	public int size() {
		synchronized (this.faces) {
			return this.faces.size();
		}
	}
}
