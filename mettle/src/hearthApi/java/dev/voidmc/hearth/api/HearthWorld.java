package dev.voidmc.hearth.api;

import java.util.Collection;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/** Stub of Hearth's per-level state: only the members Mettle calls (see package-info; founding is the tour's). */
public interface HearthWorld {
	/** The sim calendar: whole days since the world began (the overworld's day count). */
	long day();

	/** Live (adopted) settlements: those that exist as records and are simulated. */
	Collection<? extends Settlement> settlements();

	/** The nearest live settlement to {@code pos} within {@code maxDistance} blocks of its center, if any. */
	@Nullable Settlement nearest(BlockPos pos, int maxDistance);

	/** Founds a live settlement at {@code at} (null culture: from the land); null if it cannot be founded there. */
	@Nullable Settlement found(BlockPos at, @Nullable Culture culture, @Nullable Settlement parent, String reason);

	/** Why a settlement cannot be founded at {@code at}, or null if it can. */
	@Nullable String whyNotFound(BlockPos at);
}
