package dev.voidmc.hearth.social.lives;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.crowd.Barks;
import dev.voidmc.hearth.npc.crowd.Role;
import dev.voidmc.hearth.sim.lives.Life;
import dev.voidmc.hearth.sim.lives.Lives;
import dev.voidmc.hearth.sim.lives.LivesView;
import dev.voidmc.hearth.social.life.Venues;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Sweethearts walk out (docs/LIVING_WORLD_2.md §4.3, priority 30): on the rest day's afternoon the two go together to a
 * viewpoint (else the square) — the one leads, the other keeps at their side — stand there looking out, and one gives
 * the other a poppy. Seen only when both are embodied; their friendship warms either way (the day's pass).
 */
final class CourtingBehaviour implements NpcService.FolkBehaviour {
	static final Identifier ID = Hearth.id("lives/courting");
	static final int PRIORITY = 30;
	/** Couples who have given the flower today: "lower id|day". */
	static final Set<String> GIVEN = ConcurrentHashMap.newKeySet();
	private final Map<UUID, State> states = new HashMap<>();

	private static final class State {
		final UUID partner;
		final boolean leads;
		final BlockPos spot;
		long nextMove;
		long arrivedAt = -1;

		State(UUID partner, boolean leads, BlockPos spot) {
			this.partner = partner;
			this.leads = leads;
			this.spot = spot;
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
			return partner(folk) != null;
		} finally {
			Folks.t1(t0);
		}
	}

	private static NpcService.@Nullable FolkHandle partner(NpcService.FolkHandle folk) {
		Resident r = folk.resident();
		Settlement s = folk.settlement();
		if (r.age == AgeStage.CHILD || !Lives.config().lives().enabled() || !s.embodied() || !folk.awake() || Life.peek(r) == null) {
			return null;
		}
		UUID sw = Life.sweetheart(r);
		if (sw == null || !Folks.within(Folks.minute(s), NotableSchedule.COURT_FROM, NotableSchedule.COURT_TO)
			|| !HearthServices.calendar().restDay(s, Folks.today(s))) {
			return null;
		}
		NpcService npc = HearthServices.npc();
		NpcService.FolkHandle p = npc == null ? null : npc.folk(sw);
		return p != null && p.awake() && p.entity().distanceToSqr(folk.entity()) < 64 * 64 ? p : null;
	}

	/** Where a couple walks out to: a viewpoint of the town (the same for both, by the pair), else the square. */
	static BlockPos spot(Settlement s, UUID a, UUID b) {
		List<Poi> views = s.pois(PoiType.VIEWPOINT);
		BlockPos centre = s.center();
		List<Poi> near = new java.util.ArrayList<>();
		for (Poi p : views) {
			if (p.pos().distSqr(centre) < 96 * 96) {
				near.add(p);
			}
		}
		if (!near.isEmpty()) {
			long h = a.getLeastSignificantBits() ^ b.getLeastSignificantBits();
			return near.get((int) Math.floorMod(h, (long) near.size())).pos();
		}
		return Venues.square(s);
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		NpcService.FolkHandle p = partner(folk);
		if (p == null) {
			return;
		}
		UUID me = folk.resident().id;
		UUID other = p.resident().id;
		boolean leads = me.compareTo(other) < 0;
		BlockPos spot = spot(folk.settlement(), leads ? me : other, leads ? other : me);
		if (!leads) {
			spot = spot.east();
		}
		this.states.put(me, new State(other, leads, spot));
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		long t0 = Folks.t0();
		try {
			State st = this.states.get(folk.resident().id);
			if (st == null) {
				return false;
			}
			long t = folk.gameTime();
			NpcService npc = HearthServices.npc();
			NpcService.FolkHandle p = npc == null ? null : npc.folk(st.partner);
			if (p == null || (t & 63) == 0 && partner(folk) == null) {
				return false;
			}
			// the one leads to the spot; the other keeps at their side until they are there
			BlockPos goal = st.leads || Folks.near(p, st.spot, 3) ? st.spot : p.entity().blockPosition().east();
			if (!Folks.near(folk, goal, 1.5)) {
				if (t >= st.nextMove) {
					// the leader waits for the other to come up
					if (st.leads && p.entity().distanceToSqr(folk.entity()) > 36) {
						folk.stop();
						folk.lookAt(p.entity());
					} else {
						Folks.walk(folk, goal, 0.42);
					}
					st.nextMove = t + 25;
				}
				return true;
			}
			folk.stop();
			if (st.arrivedAt < 0) {
				st.arrivedAt = t;
			}
			if (t - st.arrivedAt < 200 || (t / 200 & 1) == 0) {
				folk.lookAt(p.entity());
			} else {
				folk.lookAt(st.spot.offset(st.spot.getX() > 0 ? 8 : -8, 1, 4));
			}
			Settlement s = folk.settlement();
			String key = (st.leads ? folk.resident().id : st.partner) + "|" + Folks.today(s);
			if (st.leads && t - st.arrivedAt == 120 && !GIVEN.contains(key) && Folks.near(p, st.spot, 3)) {
				GIVEN.add(key);
				if (GIVEN.size() > 2048) {
					GIVEN.clear();
				}
				folk.hold(new ItemStack(Items.POPPY));
				folk.swing();
				Barks.say(folk, "lives.courting.flower", Role.of(folk.resident(), false), null, null);
				LivesView.met(s, folk.resident(), p.resident(), 3);
			}
			if (st.leads && t - st.arrivedAt == 180) {
				folk.hold(ItemStack.EMPTY);
				p.hold(new ItemStack(Items.POPPY));
				Barks.say(p, "lives.courting.thanks", Role.of(p.resident(), false), null, null);
			}
			if (t - st.arrivedAt > 300 && (t % 400) == 0 && folk.entity().level() instanceof ServerLevel) {
				Barks.say(folk, "lives.courting", Role.of(folk.resident(), false), null, null);
			}
			return true;
		} finally {
			Folks.t1(t0);
		}
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		if (this.states.remove(folk.resident().id) != null) {
			folk.hold(ItemStack.EMPTY);
		}
	}
}
