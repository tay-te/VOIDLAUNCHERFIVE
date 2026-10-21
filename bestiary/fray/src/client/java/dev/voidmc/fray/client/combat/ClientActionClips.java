package dev.voidmc.fray.client.combat;

import dev.voidmc.fray.client.ClientClips;
import dev.voidmc.fray.combat.action.ActionClips;
import dev.voidmc.fray.combat.action.ActionKind;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Which clip a predicted or observed player action plays on this client: the animation contract's id when the client
 * has it, else the prototype alias ({@link ActionClips}).
 */
public final class ClientActionClips {
	private ClientActionClips() {
	}

	public static @Nullable Identifier clip(ActionKind kind, int variant) {
		return ActionClips.pick(kind, variant, id -> ClientClips.get(id) != null);
	}

	/** Playback speed so the clip's strike lines up with the action's ACTIVE phase (only the prototype alias is retimed). */
	public static float speed(ActionKind kind, int windup, @Nullable Identifier clip) {
		return clip != null && !clip.equals(ActionClips.SWING_ALIAS) ? 1 : ActionKind.clipSpeed(kind, windup);
	}
}
