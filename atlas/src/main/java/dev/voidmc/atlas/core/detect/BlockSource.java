package dev.voidmc.atlas.core.detect;

/**
 * Blocks by world position, as {@link Sig} codes: what the detectors look at round an anchor and the underground
 * reveal walks through. {@link Sig#UNKNOWN} where nothing is loaded. Read from worker threads; an implementation
 * only ever reads what the client already holds.
 */
@FunctionalInterface
public interface BlockSource {
	int code(int x, int y, int z);
}
