package dev.voidmc.bestiary.ai;

import dev.voidmc.bestiary.ai.math.Morale;
import dev.voidmc.bestiary.ai.profile.CreatureProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Squads and morale (BESTIARY.md §5). A champion pack is a squad: its members carry their leader's id; the leader buffs
 * them within 12 blocks ({@link Scaling#damage}); when it dies the rest test morale. A creature alone flees under 20 %
 * health if its family flees at all (beasts, arthropods); undead and constructs never do. Run every 20 ticks per
 * creature.
 */
public final class Squads {
	private Squads() {
	}

	/** Role from the profile, picked per creature so a crowd mixes bruisers and flankers; pack leaders lead. */
	public static String role(Mob mob, CreatureProfile p, CreatureData d) {
		if (d.elite() && d.packSize() > 0) {
			return "leader";
		}
		List<String> roles = p.roles();
		if (roles.isEmpty()) {
			return "bruiser";
		}
		return roles.get(Math.floorMod(mob.getUUID().hashCode() >>> 3, roles.size()));
	}

	private static final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<it.unimi.dsi.fastutil.ints.IntAVLTreeSet> WAITERS =
		new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();

	/**
	 * This creature's place among those waiting on the same target (ordered by entity id, so it is stable while the crowd
	 * stays the same; a creature leaves the list when it changes target, dies or unloads).
	 */
	static int waiterIndex(Brain b, net.minecraft.world.entity.LivingEntity target) {
		if (b.waitingOn != target.getId()) {
			leave(b);
			b.waitingOn = target.getId();
		}
		it.unimi.dsi.fastutil.ints.IntAVLTreeSet set = WAITERS.computeIfAbsent(target.getId(), k -> new it.unimi.dsi.fastutil.ints.IntAVLTreeSet());
		set.add(b.mob.getId());
		return set.headSet(b.mob.getId()).size();
	}

	public static void leave(Brain b) {
		if (b.waitingOn >= 0) {
			it.unimi.dsi.fastutil.ints.IntAVLTreeSet set = WAITERS.get(b.waitingOn);
			if (set != null) {
				set.remove(b.mob.getId());
				if (set.isEmpty()) {
					WAITERS.remove(b.waitingOn);
				}
			}
			b.waitingOn = -1;
		}
	}

	public static void clear() {
		WAITERS.clear();
	}

	static void morale(Brain b, ServerLevel level, long now) {
		CreatureProfile.Morale m = b.profile.morale();
		if (!m.flees()) {
			return;
		}
		Mob mob = b.mob;
		CreatureData d = CreatureData.of(mob);
		boolean inPack = d.leader().isPresent();
		boolean leaderDied = false;
		float alive = 1;
		if (inPack) {
			UUID leader = d.leader().get();
			Entity l = level.getEntity(leader);
			leaderDied = l == null || !l.isAlive();
			List<Mob> mates = level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(16), o -> o.isAlive() && CreatureData.get(o) != null
				&& CreatureData.of(o).leader().map(leader::equals).orElse(false));
			alive = Math.min(1, mates.size() / 4F);
		}
		float health = mob.getHealth() / Math.max(1, mob.getMaxHealth());
		boolean flee = Morale.flees(true, inPack, leaderDied, alive, health, m.aloneBelow(), m.packBelow());
		if (flee && b.awareness == Brain.Awareness.ENGAGED) {
			b.setAwareness(Brain.Awareness.FLEEING, now);
			Tokens.release(b);
		} else if (!flee && b.awareness == Brain.Awareness.FLEEING && now - b.awarenessSince > 100) {
			b.setAwareness(Brain.Awareness.ENGAGED, now);
		}
	}
}
