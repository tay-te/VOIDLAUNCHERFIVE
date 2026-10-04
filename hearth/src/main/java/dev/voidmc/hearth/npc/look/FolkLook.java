package dev.voidmc.hearth.npc.look;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.npc.person.Genome;
import dev.voidmc.hearth.npc.person.Office;

/**
 * Everything a folk's skin is drawn from: the appearance genome plus who they are. Two folk with equal looks have
 * identical skins, which is what lets the client cache composed textures by this record.
 *
 * @param appearance the resident's appearance genome ({@link Genome})
 * @param station    their household's place in society: the poor in patched, faded clothes, merchants in fine coats,
 *                   the gentry and the lord in rich colours (docs/LIVING_WORLD.md §8.1)
 * @param office     an office held beyond the trade (the guard captain's plume, the master builder's plans)
 */
public record FolkLook(int appearance, Culture culture, JobType job, AgeStage age, boolean female, Station station, Office office) {
	/** A look with the station the job usually brings and no office (previews, tests, older callers). */
	public FolkLook(int appearance, Culture culture, JobType job, AgeStage age, boolean female) {
		this(appearance, culture, job, age, female, Station.of(job), Office.NONE);
	}

	/** Slim (three-pixel) arms: women usually, men with the unusual-build gene. */
	public boolean slim() {
		return this.female != Genome.unusualBuild(this.appearance);
	}

	/** The key without station and office: seeds the composer's choices, so rising in the world changes the finish of
	 * someone's clothes, not who they are. */
	public long baseKey() {
		return (long) this.appearance << 32 | (long) this.culture.ordinal() << 16 | (long) this.job.ordinal() << 8 | (long) this.age.ordinal() << 1
			| (this.female ? 1 : 0);
	}

	/** A stable 64-bit key, for caches and texture names. */
	public long key() {
		return (long) this.appearance << 32 | (long) this.office.ordinal() << 28 | (long) this.station.ordinal() << 24
			| (long) this.culture.ordinal() << 16 | (long) this.job.ordinal() << 8 | (long) this.age.ordinal() << 1 | (this.female ? 1 : 0);
	}
}
