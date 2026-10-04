package dev.voidmc.hearth.law.folk;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.NpcService.FolkBehaviour;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.crime.CrimeDesk;
import dev.voidmc.hearth.law.justice.Confrontations;
import dev.voidmc.hearth.law.state.LawState.PendingReport;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * A witness on the way to the guard (docs/LIVING_WORLD.md §4). The brave walk up to the offender first and demand an
 * account (the player may bribe or threaten them, {@link Confrontations}); the rest cry out and go — running, if they
 * are timid — to the nearest guard, or the guardhouse, and the report is made when they get there. A witness bribed,
 * threatened or killed on the way never arrives; one who cannot find a guard sends word anyway when the report deadline
 * passes (the module's ticker files it).
 */
public final class WitnessBehaviour implements FolkBehaviour {
	public static final Identifier ID = Hearth.id("law/witness");
	static final int GIVE_UP_TICKS = 20 * 60;

	private final Map<UUID, Job> jobs = new HashMap<>();

	private enum Mode {
		CONFRONT, TALK, SEEK
	}

	private static final class Job {
		final PendingReport report;
		final boolean run;
		Mode mode;
		@Nullable UUID guard;
		@Nullable BlockPos post;
		int ticks;
		int repath;
		int shout;

		Job(PendingReport report, Mode mode, boolean run) {
			this.report = report;
			this.mode = mode;
			this.run = run;
		}
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int priority() {
		return 250;
	}

	@Override
	public boolean wants(FolkHandle folk) {
		if (!folk.awake() || folk.resident().job == JobType.GUARD || folk.resident().job == JobType.JAILER) {
			return false;
		}
		return report(folk) != null;
	}

	private static @Nullable PendingReport report(FolkHandle folk) {
		UUID me = folk.resident().id;
		for (PendingReport p : Law.state(folk.settlement()).pending()) {
			if (p.witness().equals(me)) {
				return p;
			}
		}
		return null;
	}

	@Override
	public void start(FolkHandle folk) {
		PendingReport report = report(folk);
		if (report == null) {
			return;
		}
		FolkTraits traits = FolkTraits.of(folk.resident());
		FolkTraits.Reaction reaction = traits.reaction();
		ServerPlayer offender = player(folk, report.crime().offender());
		Mode mode = Mode.SEEK;
		if (reaction == FolkTraits.Reaction.CONFRONT && offender != null && folk.entity().distanceTo(offender) < 16
			&& Confrontations.of(offender.getUUID()) == null) {
			mode = Mode.CONFRONT;
		}
		Job job = new Job(report, mode, reaction == FolkTraits.Reaction.FLEE);
		this.jobs.put(folk.resident().id, job);
		if (mode == Mode.SEEK) {
			findHelp(folk, job);
		}
	}

	@Override
	public boolean tick(FolkHandle folk) {
		Job job = this.jobs.get(folk.resident().id);
		if (job == null || !stillPending(folk.settlement(), job.report)) {
			return false;
		}
		job.ticks++;
		job.repath--;
		ServerPlayer offender = player(folk, job.report.crime().offender());
		switch (job.mode) {
			case CONFRONT -> {
				if (offender == null || job.ticks > 20 * 20 || folk.entity().distanceTo(offender) > 24) {
					job.mode = Mode.SEEK;
					findHelp(folk, job);
					return true;
				}
				if (folk.entity().distanceTo(offender) <= 3.0) {
					folk.stop();
					Confrontations.open(offender, folk, Confrontations.Kind.WITNESS);
					job.mode = Mode.TALK;
				} else if (job.repath <= 0) {
					folk.moveTo(offender.blockPosition(), 0.9);
					job.repath = 10;
				}
			}
			case TALK -> {
				Confrontations.Talk talk = offender == null ? null : Confrontations.of(offender.getUUID());
				if (talk == null || !talk.folk.equals(folk.resident().id)) {
					job.mode = Mode.SEEK;
					findHelp(folk, job);
					return true;
				}
				if (offender != null) {
					folk.lookAt(offender);
				}
				if (talk.state == Confrontations.State.OPEN) {
					return true;
				}
				Confrontations.release(offender.getUUID());
				if (talk.state == Confrontations.State.SETTLED) {
					return false; // bought or cowed: the report is gone
				}
				job.mode = Mode.SEEK;
				findHelp(folk, job);
			}
			case SEEK -> {
				return seek(folk, job);
			}
		}
		return true;
	}

	/** Heads for a guard (or the guardhouse) and reports on arrival. */
	private boolean seek(FolkHandle folk, Job job) {
		NpcService npc = HearthServices.npc();
		if (job.ticks > GIVE_UP_TICKS) {
			// found a guard in the end, somewhere out of sight
			file(folk, job);
			return false;
		}
		if (--job.shout <= 0) {
			folk.say(Component.translatable(job.run ? "hearth.law.say.help" : "hearth.law.say.guards"), null);
			job.shout = 20 * 8;
		}
		BlockPos target = null;
		if (job.guard != null && npc != null) {
			FolkHandle guard = npc.folk(job.guard);
			if (guard != null && guard.entity().isAlive()) {
				if (folk.entity().distanceTo(guard.entity()) <= 3.0) {
					guard.lookAt(folk.entity());
					guard.say(Component.translatable("hearth.law.say.see_to_it"), null);
					file(folk, job);
					return false;
				}
				target = guard.entity().blockPosition();
			} else {
				job.guard = null;
				findHelp(folk, job);
			}
		}
		if (target == null && job.post != null) {
			if (folk.entity().blockPosition().closerThan(job.post, 2.5)) {
				file(folk, job);
				return false;
			}
			target = job.post;
		}
		if (target != null && job.repath <= 0) {
			if (!folk.moveTo(target, job.run ? 1.25 : 1.05)) {
				job.post = null;
				job.guard = null;
			}
			job.repath = 15;
		}
		return true;
	}

	private void findHelp(FolkHandle folk, Job job) {
		NpcService npc = HearthServices.npc();
		job.ticks = 0;
		if (npc != null) {
			double radius = Law.config().witnesses().guardRadius();
			for (FolkHandle other : npc.folkNear((ServerLevel) folk.entity().level(), folk.entity().position(), radius)) {
				JobType job2 = other.resident().job;
				if ((job2 == JobType.GUARD || job2 == JobType.JAILER) && other.awake()) {
					job.guard = other.resident().id;
					return;
				}
			}
		}
		Settlement s = folk.settlement();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (PoiType type : new PoiType[]{PoiType.GUARD_POST, PoiType.CELL}) {
			for (Poi p : s.pois(type)) {
				double d = p.pos().distSqr(folk.entity().blockPosition());
				if (d < bestD) {
					bestD = d;
					best = p.pos();
				}
			}
		}
		job.post = best;
	}

	private static boolean stillPending(Settlement s, PendingReport report) {
		for (PendingReport p : Law.state(s).pending()) {
			if (p.id() == report.id() && p.witness().equals(report.witness())) {
				return true;
			}
		}
		return false;
	}

	private static void file(FolkHandle folk, Job job) {
		for (PendingReport p : Law.state(folk.settlement()).pending()) {
			if (p.id() == job.report.id() && p.witness().equals(job.report.witness())) {
				CrimeDesk.file((ServerLevel) folk.entity().level(), folk.settlement(), p);
				return;
			}
		}
	}

	@Override
	public void stop(FolkHandle folk) {
		Job job = this.jobs.remove(folk.resident().id);
		folk.stop();
		if (job != null && job.mode == Mode.TALK) {
			ServerPlayer offender = player(folk, job.report.crime().offender());
			Confrontations.Talk talk = offender == null ? null : Confrontations.of(offender.getUUID());
			if (talk != null && talk.folk.equals(folk.resident().id) && talk.state == Confrontations.State.OPEN) {
				Confrontations.close(offender);
			}
		}
	}

	private static @Nullable ServerPlayer player(FolkHandle folk, UUID id) {
		MinecraftServer server = folk.entity().level().getServer();
		return server == null ? null : server.getPlayerList().getPlayer(id);
	}

	public void clear() {
		this.jobs.clear();
	}
}
