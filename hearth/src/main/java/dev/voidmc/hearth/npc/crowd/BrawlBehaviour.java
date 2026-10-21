package dev.voidmc.hearth.npc.crowd;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.NpcService.FolkBehaviour;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * A brawl as folk act it (docs/LIVING_WORLD_2.md §0.3: brawl 260): the two fighters close and trade blows with their
 * fists until one is down to half health; the guard sent for walks over and parts them.
 */
public final class BrawlBehaviour implements FolkBehaviour {
	public static final Identifier ID = Hearth.id("crowd/brawl");
	public static final int PRIORITY = 260;

	private final Brawls brawls;
	private final Map<UUID, int[]> state = new HashMap<>();

	public BrawlBehaviour(Brawls brawls) {
		this.brawls = brawls;
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
	public boolean wants(FolkHandle folk) {
		return folk.awake() && this.brawls.of(folk.resident().id) != null;
	}

	@Override
	public void start(FolkHandle folk) {
		// [ticks, next swing, repath, barked]
		this.state.put(folk.resident().id, new int[]{0, 10 + Math.floorMod(folk.resident().id.hashCode(), 10), 0, 0});
		folk.hold(ItemStack.EMPTY);
	}

	@Override
	public boolean tick(FolkHandle folk) {
		UUID me = folk.resident().id;
		Brawls.Brawl b = this.brawls.of(me);
		int[] st = this.state.get(me);
		NpcService npc = HearthServices.npc();
		if (b == null || st == null || npc == null || !(folk.entity().level() instanceof ServerLevel level)) {
			return false;
		}
		st[0]++;
		st[2]--;
		if (!b.fighter(me)) {
			return part(folk, b, st, npc, level);
		}
		FolkHandle other = npc.folk(b.opponent(me));
		if (other == null) {
			this.brawls.end(b, Brawls.End.GONE, level.getServer());
			return false;
		}
		LivingEntity them = other.entity();
		double d = folk.entity().distanceTo(them);
		folk.lookAt(them);
		if (st[3] == 0 && me.equals(b.a)) {
			st[3] = 1;
			Barks.say(folk, "crowd_brawl", Role.FOLK, null, null);
		}
		if (d > 1.9) {
			if (st[2] <= 0) {
				folk.moveTo(them.blockPosition(), 1.1);
				st[2] = 10;
			}
			return true;
		}
		folk.stop();
		if (--st[1] <= 0) {
			st[1] = 16 + folk.entity().getRandom().nextInt(12);
			folk.swing();
			if (d <= 2.4) {
				them.hurtServer(level, level.damageSources().mobAttack(folk.entity()), 1.0F);
				double dx = them.getX() - folk.entity().getX();
				double dz = them.getZ() - folk.entity().getZ();
				double len = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
				them.push(dx / len * 0.35, 0.08, dz / len * 0.35);
				if (them.getHealth() <= them.getMaxHealth() / 2 || folk.entity().getHealth() <= folk.entity().getMaxHealth() / 2) {
					Barks.say(other, "crowd_brawl_yield", Role.FOLK, null, null);
					this.brawls.end(b, Brawls.End.YIELDED, level.getServer());
					return false;
				}
			}
		}
		return true;
	}

	/** The guard: walks over and comes between them. */
	private boolean part(FolkHandle folk, Brawls.Brawl b, int[] st, NpcService npc, ServerLevel level) {
		FolkHandle a = npc.folk(b.a);
		if (a == null) {
			return false;
		}
		double d = folk.entity().distanceTo(a.entity());
		if (d > 3.0) {
			if (st[2] <= 0) {
				folk.moveTo(a.entity().blockPosition(), 1.2);
				st[2] = 15;
			}
			return st[0] < 20 * 40;
		}
		folk.stop();
		folk.lookAt(a.entity());
		Barks.say(folk, "crowd_brawl_part", Role.GUARD, null, null);
		this.brawls.end(b, Brawls.End.PARTED, level.getServer());
		return false;
	}

	@Override
	public void stop(FolkHandle folk) {
		this.state.remove(folk.resident().id);
		folk.stop();
	}

	public void clear() {
		this.state.clear();
	}
}
