package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** What a trade does in working hours, embodied: the next task each time the last one ends. */
public interface WorkBehaviour {
	/** The next stretch of work, or null to idle at the workplace for a moment. */
	@Nullable Task next(FolkBrain brain, ServerLevel level);

	/** The folk is going back to being a record: take down anything left half done (scaffolding). */
	default void onFold(FolkBrain brain, ServerLevel level) {
	}

	static WorkBehaviour of(JobType job, AgeStage age) {
		if (age == AgeStage.CHILD) {
			return new ChoresWork();
		}
		return switch (job) {
			case BUILDER -> new BuilderWork();
			case FARMER -> new FarmerWork();
			case GUARD -> new GuardWork();
			case FORESTER -> new ForesterWork();
			// at the excavations (quarry pits, mines) when one is open; at the rock by the workplace when not
			case MINER, QUARRIER -> new ExcavationWork(job, new FieldWork(JobType.QUARRIER));
			case CLAY_DIGGER, SALTER -> new ExcavationWork(job, new DiggerWork(job));
			case HERDER -> new HerderWork();
			case FISHER -> new FisherWork();
			case BEEKEEPER -> new BeekeeperWork();
			case HAULER -> new HaulerWork();
			case HUNTER, COURIER -> new FieldWork(job);
			case MERCHANT, INNKEEPER, MINTER, TRADER, FENCE -> new CounterWork(job);
			case REEVE, STEWARD, JUDGE, JAILER -> new OfficialWork(job);
			case LORD -> new LordWork();
			case ENTERTAINER, BATH_KEEPER, STABLEMASTER, HEALER -> new ServiceWork(job);
			case NONE -> new ChoresWork();
			default -> new CraftWork(job);
		};
	}
}
