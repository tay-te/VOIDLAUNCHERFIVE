package dev.voidmc.fray.combat;

import dev.voidmc.fray.api.ThreatEntry;
import dev.voidmc.fray.api.ThreatService;
import dev.voidmc.fray.combat.math.ThreatTable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/** {@link ThreatService}: one {@link ThreatTable} per creature in its combat state. */
public final class Threat implements ThreatService {
	public static final Threat INSTANCE = new Threat();

	private Threat() {
	}

	private static ThreatTable table(Mob mob, boolean create) {
		CombatState s = create ? CombatState.of(mob) : CombatState.peek(mob);
		if (s == null) {
			return null;
		}
		if (s.threat == null && create) {
			s.threat = new ThreatTable();
		}
		return s.threat;
	}

	@Override
	public void add(Mob mob, LivingEntity source, float amount, Identifier reason) {
		if (source == mob || amount <= 0) {
			return;
		}
		table(mob, true).add(source.getId(), amount, mob.level().getGameTime());
	}

	@Override
	public void taunt(Mob mob, LivingEntity taunter, int ticks) {
		table(mob, true).taunt(taunter.getId(), ticks, mob.level().getGameTime());
	}

	@Override
	public @Nullable LivingEntity top(Mob mob) {
		ThreatTable t = table(mob, false);
		if (t == null) {
			return null;
		}
		int id = t.top(mob.level().getGameTime());
		Entity e = id < 0 ? null : mob.level().getEntity(id);
		return e instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	@Override
	public List<ThreatEntry> table(Mob mob) {
		ThreatTable t = table(mob, false);
		List<ThreatEntry> out = new ArrayList<>();
		if (t != null) {
			for (int i = 0; i < t.size; i++) {
				if (mob.level().getEntity(t.ids[i]) instanceof LivingEntity le) {
					out.add(new ThreatEntry(le, t.threat[i]));
				}
			}
		}
		out.sort((a, b) -> Float.compare(b.threat(), a.threat()));
		return out;
	}

	@Override
	public void clear(Mob mob) {
		ThreatTable t = table(mob, false);
		if (t != null) {
			t.clear();
		}
	}
}
