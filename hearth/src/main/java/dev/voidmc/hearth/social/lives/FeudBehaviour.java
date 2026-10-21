package dev.voidmc.hearth.social.lives;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.CrowdService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.crowd.Barks;
import dev.voidmc.hearth.npc.crowd.Role;
import dev.voidmc.hearth.sim.lives.Lives;
import dev.voidmc.hearth.sim.lives.LivesView;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A feud acted out in passing (docs/LIVING_WORLD_2.md §4.3, priority 42): folk of feuding houses who come within a few
 * blocks of each other turn away and walk off, with an insult said within earshot (and an answer); of an evening at the
 * tavern they may come to blows ({@code CrowdService.brawl}, lane A3's brawl). At most once a minute and a half a folk.
 */
final class FeudBehaviour implements NpcService.FolkBehaviour {
	static final Identifier ID = Hearth.id("lives/feud");
	static final int PRIORITY = 42;
	static final double RANGE = 7;
	static final long COOLDOWN = 20 * 90;
	private final Map<UUID, Long> last = new HashMap<>();
	private final Map<UUID, State> states = new HashMap<>();

	private static final class State {
		final UUID foe;
		final BlockPos away;
		final long until;

		State(UUID foe, BlockPos away, long until) {
			this.foe = foe;
			this.away = away;
			this.until = until;
		}
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int priority() {
		return PRIORITY;
	}

	@Override
	public boolean wants(NpcService.FolkHandle folk) {
		long t0 = Folks.t0();
		try {
			Long l = this.last.get(folk.resident().id);
			if (l != null && folk.gameTime() - l < COOLDOWN) {
				return false;
			}
			return foe(folk) != null;
		} finally {
			Folks.t1(t0);
		}
	}

	/** A folk of a house at feud with this one's, within range and in sight. */
	private static NpcService.@Nullable FolkHandle foe(NpcService.FolkHandle folk) {
		Resident r = folk.resident();
		Settlement s = folk.settlement();
		if (r.age == AgeStage.CHILD || !Lives.config().lives().enabled() || !s.embodied() || !folk.awake()) {
			return null;
		}
		Set<UUID> foes = LivesView.foes(s, r.id);
		NpcService npc = HearthServices.npc();
		if (foes == null || foes.isEmpty() || npc == null || !(folk.entity().level() instanceof ServerLevel level)) {
			return null;
		}
		for (NpcService.FolkHandle o : npc.folkNear(level, folk.entity().position(), RANGE)) {
			if (o.resident() == r || o.resident().age == AgeStage.CHILD || !o.awake()) {
				continue;
			}
			Set<UUID> theirs = LivesView.foes(s, o.resident().id);
			if (theirs != null && foes.contains(LivesView.head(s, o.resident()).id) && folk.canSee(o.entity())) {
				return o;
			}
		}
		return null;
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		NpcService.FolkHandle foe = foe(folk);
		long t = folk.gameTime();
		this.last.put(folk.resident().id, t);
		if (this.last.size() > 1024) {
			this.last.values().removeIf(v -> t - v > COOLDOWN);
		}
		if (foe == null) {
			return;
		}
		Settlement s = folk.settlement();
		Vec3 me = folk.entity().position();
		Vec3 them = foe.entity().position();
		Vec3 dir = me.subtract(them);
		double len = Math.max(0.1, dir.horizontalDistance());
		BlockPos away = BlockPos.containing(me.x + dir.x / len * 5, me.y, me.z + dir.z / len * 5);
		this.states.put(folk.resident().id, new State(foe.resident().id, away, t + 80));
		int minute = Folks.minute(s);
		boolean evening = Folks.within(minute, 19 * 60, 2 * 60);
		CrowdService crowds = HearthServices.crowds();
		if (evening && crowds != null && folk.entity().getRandom().nextDouble() < Lives.config().feuds().brawlChance()
			&& crowds.brawl(s, folk.resident().id, foe.resident().id, "feud")) {
			Barks.say(folk, "lives.feud.brawl", Role.of(folk.resident(), false), null, null);
			if (HearthServices.lives() != null) {
				HearthServices.lives().lifeEvent(s, folk.resident().id, "brawl", null);
			}
			this.states.remove(folk.resident().id);
			return;
		}
		folk.lookAt(foe.entity());
		Barks.say(folk, "lives.feud.insult", Role.of(folk.resident(), false), null, null);
		// the other answers in kind, now and then
		if (folk.entity().getRandom().nextInt(2) == 0) {
			Barks.say(foe, "lives.feud.answer", Role.of(foe.resident(), false), null, null);
		}
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		State st = this.states.get(folk.resident().id);
		if (st == null) {
			return false;
		}
		long t = folk.gameTime();
		if (t > st.until) {
			return false;
		}
		if (t == st.until - 60) {
			Folks.walk(folk, st.away, 0.55);
		}
		return true;
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		this.states.remove(folk.resident().id);
	}
}
