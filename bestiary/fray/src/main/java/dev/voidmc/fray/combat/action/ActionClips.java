package dev.voidmc.fray.combat.action;

import java.util.List;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Which clip each player action plays: the ids of the animation contract (docs/ANIM_EVENTS.md §1.1, branch
 * bestiary-anim), each with the prototype alias it falls back to while the stage 1 clip set isn't in the jar. Common
 * code: the server names the clip when it starts a player action on Fray Motion, the client when it predicts one.
 */
public final class ActionClips {
	private static final String NS = "bestiary";
	public static final Identifier SWING_ALIAS = Identifier.fromNamespaceAndPath(NS, "player/heavy_swing");
	public static final Identifier ROLL_ALIAS = Identifier.fromNamespaceAndPath(NS, "player/dodge_roll");

	private ActionClips() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(NS, path);
	}

	/**
	 * The clip ids to try, best first: the contract's id, then the prototype alias. {@code variant} is the light's combo
	 * index or the dodge's compass direction (0 none, 1 forward, clockwise to 8).
	 */
	public static List<Identifier> candidates(ActionKind kind, int variant) {
		return switch (kind) {
			case LIGHT -> List.of(id("player/sword/light_" + Math.min(3, variant + 1)), SWING_ALIAS);
			case RUNNING, GUARD_COUNTER, ROLL_ATTACK, RIPOSTE -> List.of(id("player/sword/light_1"), SWING_ALIAS);
			case HEAVY, EXECUTE, WHIRLWIND, EARTHSHAKER -> List.of(id("player/sword/heavy"), SWING_ALIAS);
			case CHARGED -> List.of(id("player/sword/charge_release"), id("player/sword/heavy"), SWING_ALIAS);
			case PLUNGE -> List.of(id("player/sword/plunge"), SWING_ALIAS);
			case DODGE -> {
				// Compass 3 (right) and 7 (left) side-dash; everything else rolls forward along the input.
				String path = variant == 3 ? "player/dodge_right" : variant == 7 ? "player/dodge_left" : "player/dodge_forward";
				yield List.of(id(path), id("player/dodge_forward"), ROLL_ALIAS);
			}
			case BACKSTEP -> List.of(id("player/backstep"));
			case PARRY -> List.of(id("player/parry"));
		};
	}

	/** The first candidate that {@code known} accepts, or null. */
	public static @Nullable Identifier pick(ActionKind kind, int variant, java.util.function.Predicate<Identifier> known) {
		for (Identifier id : candidates(kind, variant)) {
			if (known.test(id)) {
				return id;
			}
		}
		return null;
	}
}
