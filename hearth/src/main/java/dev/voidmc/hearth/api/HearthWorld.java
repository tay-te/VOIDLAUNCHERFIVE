package dev.voidmc.hearth.api;

import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** The settlements of one dimension, and its shared Hearth state. Implemented by the sim module; server thread only. */
public interface HearthWorld {
	/** The Hearth state of a level, or null if Hearth does not run there (it runs in the overworld). */
	static @Nullable HearthWorld of(ServerLevel level) {
		return HearthServices.world(level);
	}

	ServerLevel level();

	TerrainProbe terrain();

	/** The sim calendar: whole days since the world began (the overworld's day count). */
	long day();

	/** Live (adopted) settlements: those that exist as records and are simulated. */
	Collection<? extends Settlement> settlements();

	@Nullable Settlement settlement(long id);

	/** The settlement whose bounds contain {@code pos} (with a small margin), if any. */
	@Nullable Settlement settlementAt(BlockPos pos);

	/** The nearest live settlement to {@code pos} within {@code maxDistance} blocks of its center, if any. */
	@Nullable Settlement nearest(BlockPos pos, int maxDistance);

	<T> T data(HearthDataKey<T> key);

	void markDirty();
}
