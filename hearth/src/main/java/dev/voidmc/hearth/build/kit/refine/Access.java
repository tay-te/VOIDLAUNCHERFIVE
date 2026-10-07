package dev.voidmc.hearth.build.kit.refine;

import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.Doors;
import dev.voidmc.hearth.build.kit.Guards;

/**
 * The access pass every blueprint goes through when it compiles, after the archetype has drawn and before the sealed
 * voids are filled and the light pass runs (the audit's rules S1, S3 and S4, made once for every family): doors that can
 * be used ({@link Doors#finish}: nothing in a doorway, no door into a wall or over a drop, no chest under a solid block),
 * head room over every flight ({@link dev.voidmc.hearth.build.kit.Stairs#finish}) and guards on the edges folk could
 * step off ({@link Guards#finish}). An archetype that does these itself loses
 * nothing: the pass only touches what is still wrong.
 */
public final class Access {
	private Access() {
	}

	/** What the pass did: doors seen, props cleared, doors bricked up, chests made barrels, guards set. */
	public record Report(int doors, int cleared, int bricked, int lids, int guards) {
	}

	public static Report finish(Draft d) {
		// -Dhearth.access.off=true: the drawing as the archetype left it (to measure what the pass does)
		if (Boolean.getBoolean("hearth.access.off")) {
			return new Report(0, 0, 0, 0, 0);
		}
		Doors.Report r = Doors.finish(d);
		dev.voidmc.hearth.build.kit.Stairs.finish(d);
		// -Dhearth.lofts.off=true: without the loft pass (to measure it alone)
		if (!Boolean.getBoolean("hearth.lofts.off")) {
			Lofts.finish(d);
		}
		int guards = Guards.finish(d);
		return new Report(r.doors(), r.cleared(), r.bricked(), r.lids(), guards);
	}
}
