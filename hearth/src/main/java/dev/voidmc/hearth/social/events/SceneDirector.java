package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.Settlement;

/**
 * What a kind of scene does beyond its data timeline (docs/LIVING_WORLD_2.md §3.3): the fire's flames, the raiders, the
 * archery targets and their scores, the joust and the melee, the bier, the rotten vegetables, the pig led away. Every
 * hook runs on the server thread with the town embodied; {@link #tick} every 20 ticks.
 */
public interface SceneDirector {
	SceneDirector DEFAULT = new SceneDirector() {
	};

	/** The scene has begun (its cast chosen, the first beats not yet played). */
	default void begin(SceneRun run, Settlement s) {
	}

	/** Every 20 ticks while it runs, after the minute's beats. */
	default void tick(SceneRun run, Settlement s, long gameTime) {
	}

	/** A beat the timeline runner does not know ({@code spawn}, or the kind's own words). */
	default void beat(SceneRun run, Settlement s, EventRule.Beat beat) {
	}

	/** It is over (time is up, it was ended, or the town folded): take away what it made. */
	default void end(SceneRun run, Settlement s) {
	}

	/** Whether the scene ends before its time (the fire is out, the raiders gone). */
	default boolean done(SceneRun run, Settlement s) {
		return false;
	}

	static SceneDirector of(EventKind kind) {
		return switch (kind) {
			case FIRE -> FireScene.INSTANCE;
			case RAID -> RaidScene.INSTANCE;
			case FAIR -> FairScene.INSTANCE;
			case TOURNAMENT -> TourneyScene.INSTANCE;
			case FUNERAL -> FuneralScene.INSTANCE;
			case PILLORY, EXECUTION -> PilloryScene.INSTANCE;
			case TAX -> TaxScene.INSTANCE;
			case RUNAWAY_PIG, HORSE_FAIR, SLAUGHTER_DAY -> BeastScene.INSTANCE;
			case WEDDING -> WeddingScene.INSTANCE;
			default -> DEFAULT;
		};
	}
}
