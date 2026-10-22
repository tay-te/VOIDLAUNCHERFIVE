package dev.voidmc.fray.combat;

import dev.voidmc.fray.api.ProfileService;
import dev.voidmc.fray.api.Rank;
import dev.voidmc.fray.combat.net.CombatSync;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** {@link ProfileService}: rank, subtitle and affixes on the creature's combat state, synced to trackers. */
public final class Profiles implements ProfileService {
	public static final Profiles INSTANCE = new Profiles();

	private Profiles() {
	}

	@Override
	public void set(LivingEntity entity, Rank rank, @Nullable Component subtitle, List<Identifier> affixes) {
		CombatState s = CombatState.of(entity);
		s.rank = rank;
		s.subtitle = subtitle;
		s.affixes = List.copyOf(affixes);
		s.poise.max = Poise.max(entity, rank);
		s.poise.value = s.poise.max;
		CombatSync.profile(s);
	}

	@Override
	public Rank rank(LivingEntity entity) {
		CombatState s = CombatState.peek(entity);
		return s == null ? Rank.COMMON : s.rank;
	}
}
