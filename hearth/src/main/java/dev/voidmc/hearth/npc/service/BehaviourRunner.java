package dev.voidmc.hearth.npc.service;

import dev.voidmc.hearth.api.NpcService.FolkBehaviour;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs other modules' {@link FolkBehaviour}s for one folk, alongside the routine (docs/FOLK.md §13).
 *
 * <p>About once a second (staggered by the brain) it asks, highest priority first, the behaviours ranked above both the
 * routine's current priority and the behaviour already running whether they {@link FolkBehaviour#wants want} the folk;
 * the first that does takes over. The running behaviour is ticked every tick until it returns false or something higher
 * wants the folk. A behaviour that throws is stopped and left alone by this folk for a minute, and the error is logged
 * once per behaviour, so one module's bug never takes a town's folk down with it.
 */
public final class BehaviourRunner {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");
	/** Game ticks a behaviour that threw is skipped for this folk. */
	static final long SUSPEND_TICKS = 1200;
	private static final Set<Identifier> LOGGED = ConcurrentHashMap.newKeySet();

	/** What the owner (the brain) does when a behaviour takes the folk over or hands it back. */
	public interface Owner {
		/** Stop the routine's task, stand up, put tools away: a behaviour is taking over. */
		void takeOver(FolkBehaviour behaviour);

		/** The behaviour ended: the routine plans afresh. */
		void handBack(FolkBehaviour behaviour);
	}

	private @Nullable FolkBehaviour current;
	private final Map<Identifier, Long> suspendedUntil = new HashMap<>();

	public boolean active() {
		return this.current != null;
	}

	public @Nullable FolkBehaviour current() {
		return this.current;
	}

	/** The running behaviour's priority, or {@link Integer#MIN_VALUE} if none runs. */
	public int priority() {
		return this.current == null ? Integer.MIN_VALUE : this.current.priority();
	}

	/**
	 * Asks the behaviours ranked above {@code floor} and above the running one whether they want the folk; starts the
	 * first that does (stopping the running one). Returns the behaviour now running, or null.
	 */
	public @Nullable FolkBehaviour poll(FolkHandle folk, int floor, List<FolkBehaviour> behaviours, Owner owner) {
		int above = Math.max(floor, priority());
		long now = folk.gameTime();
		for (FolkBehaviour b : behaviours) {
			if (b.priority() <= above) {
				// sorted highest first: nothing further down can win
				break;
			}
			if (b == this.current || suspended(b, now)) {
				continue;
			}
			boolean wants;
			try {
				wants = b.wants(folk);
			} catch (RuntimeException e) {
				fail(b, now, "wants", e);
				continue;
			}
			if (wants) {
				start(folk, b, owner);
				return this.current;
			}
		}
		return this.current;
	}

	/** Ticks the running behaviour; returns whether one is still running afterwards. */
	public boolean tick(FolkHandle folk, Owner owner) {
		FolkBehaviour b = this.current;
		if (b == null) {
			return false;
		}
		boolean more;
		try {
			more = b.tick(folk);
		} catch (RuntimeException e) {
			fail(b, folk.gameTime(), "tick", e);
			more = false;
		}
		if (!more && this.current == b) {
			stop(folk, owner);
		}
		return this.current != null;
	}

	/** Stops the running behaviour (a higher reaction, the folk folding away). */
	public void stop(FolkHandle folk, Owner owner) {
		FolkBehaviour b = this.current;
		if (b == null) {
			return;
		}
		this.current = null;
		try {
			b.stop(folk);
		} catch (RuntimeException e) {
			fail(b, folk.gameTime(), "stop", e);
		}
		owner.handBack(b);
	}

	private void start(FolkHandle folk, FolkBehaviour b, Owner owner) {
		if (this.current != null) {
			stop(folk, owner);
		}
		owner.takeOver(b);
		this.current = b;
		try {
			b.start(folk);
		} catch (RuntimeException e) {
			fail(b, folk.gameTime(), "start", e);
			this.current = null;
			owner.handBack(b);
		}
	}

	private boolean suspended(FolkBehaviour b, long now) {
		Long until = this.suspendedUntil.get(b.id());
		if (until == null) {
			return false;
		}
		if (now >= until) {
			this.suspendedUntil.remove(b.id());
			return false;
		}
		return true;
	}

	private void fail(FolkBehaviour b, long now, String where, RuntimeException e) {
		this.suspendedUntil.put(b.id(), now + SUSPEND_TICKS);
		if (LOGGED.add(b.id())) {
			LOG.error("folk behaviour {} failed in {}; it is skipped for this folk for a minute (logged once)", b.id(), where, e);
		}
	}
}
