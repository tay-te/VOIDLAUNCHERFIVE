package dev.voidmc.atlas.core.landmarks;

/** For tests in other packages: RegionAnalysis's package-private parts. */
public final class RegionAnalysisAccess {
	private RegionAnalysisAccess() {
	}

	public static int prominence(short[] g, int c) {
		return RegionAnalysis.prominence(g, c);
	}
}
