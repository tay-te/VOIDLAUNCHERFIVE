package dev.voidmc.atlas.core.under;

/**
 * The depth scrubber's window on a world's height: a span of blocks within the level's own bottom and top (whatever its
 * height: a vanilla overworld's -64 to 319, an Earth world's -64 to 639, the Nether's 0 to 255), moved only when the
 * slice shown nears its edge, so a drag along it stays put.
 */
public final class DepthWindow {
	private DepthWindow() {
	}

	/**
	 * The window {@code {bottom, top}} for the slice at {@code sliceY}, from the one shown before ({@code bottom}): 8-block
	 * aligned, {@code span} blocks, inside {@code minY} to {@code maxY} (inclusive).
	 */
	public static int[] of(int minY, int maxY, int sliceY, int bottom, int span) {
		int lo = Math.floorDiv(minY, 8) * 8;
		int hi = Math.max(lo, Math.floorDiv(maxY + 1, 8) * 8 - span);
		if (sliceY < bottom + 32 || sliceY > bottom + span - 32) {
			bottom = Math.floorDiv(sliceY - span / 2, 8) * 8;
		}
		bottom = Math.max(lo, Math.min(hi, bottom));
		return new int[]{bottom, bottom + span};
	}
}
