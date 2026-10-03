package dev.voidmc.hearth.npc.look;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.npc.person.Genome;

/**
 * Everything a folk's skin is drawn from: the appearance genome plus who they are. Two folk with equal looks have
 * identical skins, which is what lets the client cache composed textures by this record.
 *
 * @param appearance the resident's appearance genome ({@link Genome})
 */
public record FolkLook(int appearance, Culture culture, JobType job, AgeStage age, boolean female) {
	/** Slim (three-pixel) arms: women usually, men with the unusual-build gene. */
	public boolean slim() {
		return this.female != Genome.unusualBuild(this.appearance);
	}

	/** A stable 64-bit key, for caches and texture names. */
	public long key() {
		return (long) this.appearance << 32 | (long) this.culture.ordinal() << 16 | (long) this.job.ordinal() << 8
			| (long) this.age.ordinal() << 1 | (this.female ? 1 : 0);
	}
}
