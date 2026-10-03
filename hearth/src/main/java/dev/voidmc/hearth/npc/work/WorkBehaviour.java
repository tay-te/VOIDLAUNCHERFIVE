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
			case FORESTER, QUARRIER, HUNTER, HERDER, FISHER, COURIER -> new FieldWork(job);
			case MERCHANT, INNKEEPER, MINTER -> new CounterWork(job);
			case NONE -> new ChoresWork();
			default -> new CraftWork(job);
		};
	}
}
