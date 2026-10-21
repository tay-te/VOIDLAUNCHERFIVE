package dev.voidmc.hearth.social.lives;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.crowd.Barks;
import dev.voidmc.hearth.npc.crowd.Role;
import dev.voidmc.hearth.sim.lives.Life;
import dev.voidmc.hearth.sim.lives.Lives;
import dev.voidmc.hearth.social.life.Venues;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Grief (docs/LIVING_WORLD_2.md §4.1, §4.3, priority 36): a widow or widower in mourning goes to the grave on the rest
 * day's morning — the cemetery, else the churchyard of the chapel or church — stands there with bowed head and prays.
 * (The darkened dress of mourning waits on the skin composer: requests/lwB2.md.)
 */
final class GriefBehaviour implements NpcService.FolkBehaviour {
	static final Identifier ID = Hearth.id("lives/grief");
	static final int PRIORITY = 36;
	static final String[] GRAVES = {"civic/cemetery", "civic/chapel", "civic/church", "civic/cathedral", "civic/temple"};
	private final Map<UUID, BlockPos> spots = new HashMap<>();

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
			Resident r = folk.resident();
			Settlement s = folk.settlement();
			if (r.age == AgeStage.CHILD || !Lives.config().lives().enabled() || !s.embodied() || !folk.awake() || Life.peek(r) == null) {
				return false;
			}
			long today = Folks.today(s);
			return Life.mourning(r, today) && HearthServices.calendar().restDay(s, today)
				&& Folks.within(Folks.minute(s), NotableSchedule.GRAVE_FROM, NotableSchedule.GRAVE_TO) && grave(s) != null;
		} finally {
			Folks.t1(t0);
		}
	}

	/** The town's burial ground: its cemetery, else its first place of worship. */
	static @Nullable BlockPos grave(Settlement s) {
		for (String path : GRAVES) {
			List<PlotView> plots = Venues.built(s, path);
			if (!plots.isEmpty()) {
				return Venues.middle(plots.get(0).spec());
			}
		}
		return null;
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		BlockPos g = grave(folk.settlement());
		if (g == null || !(folk.entity().level() instanceof ServerLevel level)) {
			return;
		}
		Venues.Venue v = new Venues.Venue(g, g, List.of(g), 3);
		BlockPos stand = Venues.standFor(level, v, folk.resident().id, 36);
		this.spots.put(folk.resident().id, stand);
		Folks.walk(folk, stand, 0.4);
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		BlockPos at = this.spots.get(folk.resident().id);
		if (at == null) {
			return false;
		}
		long t = folk.gameTime();
		if ((t & 63) == 0 && !wants(folk)) {
			return false;
		}
		if (!Folks.near(folk, at, 1.8)) {
			if ((t % 60) == 0) {
				Folks.walk(folk, at, 0.4);
			}
			return true;
		}
		folk.stop();
		if ((t % 200) == 0) {
			folk.pose("pray", 180);
			if (folk.entity().getRandom().nextInt(4) == 0) {
				Barks.say(folk, "lives.grief", Role.of(folk.resident(), false), null, null);
			}
		}
		return true;
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		if (this.spots.remove(folk.resident().id) != null) {
			folk.pose("none", 0);
		}
	}
}
