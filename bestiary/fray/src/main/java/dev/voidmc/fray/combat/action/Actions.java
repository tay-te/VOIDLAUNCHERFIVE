package dev.voidmc.fray.combat.action;

import dev.voidmc.fray.api.ActionService;
import dev.voidmc.fray.api.ArtProvider;
import dev.voidmc.fray.api.InterruptReason;
import dev.voidmc.fray.combat.CombatState;
import dev.voidmc.fray.combat.net.CombatPayloads;
import dev.voidmc.fray.motion.MotionServer;
import java.util.Objects;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** {@link ActionService}: the players' actions (PlayerCombat) and interrupts of creature clips (Fray Motion). */
public final class Actions implements ActionService {
	public static final Actions INSTANCE = new Actions();
	private ArtProvider provider = ArtProvider.NONE;

	private Actions() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(CombatPayloads.Input.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (!p.isAlive() || p.isSpectator()) {
				return;
			}
			PlayerCombat pc = CombatState.of(p).player;
			if (pc != null) {
				pc.onInput(payload);
			}
		});
	}

	public ArtProvider provider() {
		return this.provider;
	}

	public static @Nullable PlayerCombat combat(ServerPlayer p) {
		return CombatState.of(p).player;
	}

	@Override
	public @Nullable Identifier current(LivingEntity entity) {
		if (entity instanceof ServerPlayer p) {
			PlayerCombat pc = combat(p);
			return pc == null || pc.run == null ? null : pc.run.kind.id();
		}
		MotionServer.Running r = MotionServer.running(entity);
		return r == null ? null : Identifier.fromNamespaceAndPath("fray", "motion");
	}

	@Override
	public boolean interrupt(LivingEntity entity, InterruptReason reason) {
		if (entity instanceof ServerPlayer p) {
			PlayerCombat pc = combat(p);
			return pc != null && pc.interrupt(reason);
		}
		if (MotionServer.running(entity) != null) {
			MotionServer.stop(entity, 3);
			return true;
		}
		return false;
	}

	@Override
	public @Nullable LivingEntity lockTarget(ServerPlayer player) {
		PlayerCombat pc = combat(player);
		return pc == null ? null : pc.lockEntity();
	}

	@Override
	public void setArtProvider(ArtProvider provider) {
		this.provider = Objects.requireNonNull(provider);
	}

	@Override
	public void setArt(ServerPlayer player, @Nullable Identifier art) {
		PlayerCombat pc = combat(player);
		if (pc != null) {
			pc.artOverride = art;
			pc.lastSentTick = Long.MIN_VALUE / 2;
		}
	}

	@Override
	public int artCooldown(ServerPlayer player) {
		PlayerCombat pc = combat(player);
		return pc == null ? 0 : (int) Math.max(0, pc.artReadyAt - pc.now());
	}

	@Override
	public boolean invulnerable(LivingEntity entity) {
		if (!(entity instanceof ServerPlayer p)) {
			return false;
		}
		PlayerCombat pc = combat(p);
		return pc != null && pc.invulnerable(pc.now());
	}
}
