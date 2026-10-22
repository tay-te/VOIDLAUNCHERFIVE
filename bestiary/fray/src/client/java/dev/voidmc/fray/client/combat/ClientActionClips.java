package dev.voidmc.fray.client.combat;

import dev.voidmc.fray.client.ClientClips;
import dev.voidmc.fray.combat.action.ActionClips;
import dev.voidmc.fray.combat.action.ActionKind;
import dev.voidmc.fray.combat.action.ActionTiming;
import dev.voidmc.fray.combat.action.WeaponFamily;
import dev.voidmc.fray.combat.math.Phases;
import dev.voidmc.fray.motion.MotionServer;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Which clip a predicted player action plays on this client (the animation contract's id when the client has it, else
 * the prototype alias, {@link ActionClips}) and at what speed ({@link ActionTiming}, the server's own function).
 */
public final class ClientActionClips {
	private ClientActionClips() {
	}

	public static @Nullable Identifier clip(ActionKind kind, int variant) {
		return ActionClips.pick(kind, variant, id -> ClientClips.get(id) != null);
	}

	/** Playback speed: 1 for an action's own clip, a stand-in clip retimed so its strike lands on the action's ACTIVE phase. */
	public static float speed(ActionKind kind, WeaponFamily family, Phases def, @Nullable Identifier clip) {
		return ActionTiming.of(kind, family, def, clip, clip == null ? null : MotionServer.timeline(clip), 1).clipSpeed();
	}
}
