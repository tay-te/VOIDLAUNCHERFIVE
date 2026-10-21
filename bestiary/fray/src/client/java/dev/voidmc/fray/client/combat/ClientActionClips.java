package dev.voidmc.fray.client.combat;

import dev.voidmc.fray.combat.action.ActionKind;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Which clip each player action plays. Stage 1B uses the prototype's two player clips; the animation lane's
 * {@code docs/ANIM_EVENTS.md} names replace these in one place when they land.
 */
public final class ClientActionClips {
	public static final Identifier SWING = Identifier.fromNamespaceAndPath("bestiary", "player/heavy_swing");
	public static final Identifier ROLL = Identifier.fromNamespaceAndPath("bestiary", "player/dodge_roll");
	/** The swing clip's own length in ticks (its ACTIVE is ticks 11-13). */
	private static final float SWING_TICKS = 26;
	private static final float SWING_ACTIVE = 11;

	private ClientActionClips() {
	}

	public static @Nullable Identifier clip(ActionKind kind) {
		return switch (kind) {
			case DODGE -> ROLL;
			case BACKSTEP, PARRY -> null;
			default -> SWING;
		};
	}

	/** Playback speed so the clip's strike lines up with the action's ACTIVE phase. */
	public static float speed(ActionKind kind, int windup) {
		return ActionKind.clipSpeed(kind, windup);
	}
}
