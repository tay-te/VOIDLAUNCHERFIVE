package dev.voidmc.fray.combat;

import dev.voidmc.fray.api.TelegraphService;
import dev.voidmc.fray.api.TellColour;
import dev.voidmc.fray.combat.net.CombatPayloads;
import java.util.Optional;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * {@link TelegraphService}: sends the tell to the targeted player and remembers it on the attacker, so the strike that
 * follows (a vanilla {@code doHurtTarget} too) carries the tell's hit flags: red can't be blocked, orange parries only
 * with a shield.
 */
public final class Telegraphs implements TelegraphService {
	public static final Telegraphs INSTANCE = new Telegraphs();

	private record Live(TellColour colour, long until) {
	}

	private final WeakHashMap<LivingEntity, Live> live = new WeakHashMap<>();

	private Telegraphs() {
	}

	@Override
	public void tell(LivingEntity attacker, LivingEntity target, TellColour colour, int windupTicks, @Nullable Component subtitle) {
		this.live.put(attacker, new Live(colour, attacker.level().getGameTime() + windupTicks + 12));
		if (target instanceof ServerPlayer p) {
			ServerPlayNetworking.send(p, new CombatPayloads.Tell(attacker.getId(), (byte) colour.ordinal(), windupTicks, Optional.ofNullable(subtitle)));
		}
	}

	/** The hit flags of the attacker's live tell, 0 when none. */
	public int flags(LivingEntity attacker) {
		Live l = this.live.get(attacker);
		if (l == null) {
			return 0;
		}
		if (attacker.level().getGameTime() > l.until()) {
			this.live.remove(attacker);
			return 0;
		}
		return l.colour().hitFlags();
	}

	public void clear() {
		this.live.clear();
	}
}
