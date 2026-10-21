package dev.voidmc.hearth.sim.lives;

import dev.voidmc.hearth.api.LivesService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@link LivesService} for other modules (docs/LIVING_WORLD_2.md §4.2): the town events' trials ask for feuds, the
 * player's commissions for feuds to settle, the crowds who is whose kin.
 */
public final class LivesServiceImpl implements LivesService {
	public static final LivesServiceImpl INSTANCE = new LivesServiceImpl();

	private LivesServiceImpl() {
	}

	@Override
	public List<Notable> notables(Settlement s) {
		List<Notable> out = new ArrayList<>();
		for (TownLives.Notable n : s.data(Lives.TOWN).notables) {
			out.add(new Notable(n.resident, n.role, n.thread, n.beat));
		}
		return out;
	}

	@Override
	public @Nullable Relation relation(Settlement s, UUID a, UUID b) {
		Resident ra = s.resident(a);
		Resident rb = s.resident(b);
		if (ra == null || rb == null || a.equals(b)) {
			return null;
		}
		if (b.equals(Kin.spouse(ra))) {
			return new Relation("spouse", 100);
		}
		if (Kin.parents(ra).contains(b) || Kin.parents(rb).contains(a) || Kin.children(ra).contains(b)) {
			return new Relation("kin", 80);
		}
		if (Kin.close(ra, rb)) {
			return new Relation("kin", 70);
		}
		TownLives town = s.data(Lives.TOWN);
		Life.Relation r = Life.relation(ra, b, Math.max(0, town.lastDay), Lives.config().relations());
		if (r != null && r.bond() != Life.Bond.ACQUAINTANCE) {
			return new Relation(r.bond().id(), r.strength());
		}
		if (Feuds.feuding(s, town, ra, rb)) {
			return new Relation("feud", -50);
		}
		return r == null ? null : new Relation(r.bond().id(), r.strength());
	}

	@Override
	public List<Feud> feuds(Settlement s) {
		List<Feud> out = new ArrayList<>();
		for (TownLives.Feud f : s.data(Lives.TOWN).feuds) {
			out.add(new Feud(f.headA, f.headB, f.cause, f.since));
		}
		return out;
	}

	@Override
	public void lifeEvent(Settlement s, UUID resident, String kind, @Nullable UUID player) {
		Lives.lifeEvent(s, resident, kind, player);
	}
}
