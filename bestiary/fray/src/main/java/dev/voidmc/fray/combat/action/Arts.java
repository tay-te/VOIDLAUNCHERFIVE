package dev.voidmc.fray.combat.action;

import dev.voidmc.fray.Fray;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** The Arts Fray runs (sneak + C); Mettle's Masteries decide who has which (an {@link dev.voidmc.fray.api.ArtProvider}). */
public final class Arts {
	public static final Identifier WHIRLWIND = Fray.id("whirlwind");
	public static final Identifier EARTHSHAKER = Fray.id("earthshaker");
	public static final List<Identifier> ALL = List.of(WHIRLWIND, EARTHSHAKER);

	private Arts() {
	}

	public static @Nullable ActionKind kind(Identifier art) {
		if (art.getPath().equals("whirlwind")) {
			return ActionKind.WHIRLWIND;
		}
		if (art.getPath().equals("earthshaker")) {
			return ActionKind.EARTHSHAKER;
		}
		return null;
	}

	public static ActionDef def(ActionKind kind) {
		return kind == ActionKind.EARTHSHAKER ? Movesets.EARTHSHAKER : Movesets.WHIRLWIND;
	}

	public static int cooldown(ActionKind kind) {
		return kind == ActionKind.EARTHSHAKER ? Movesets.EARTHSHAKER_COOLDOWN : Movesets.WHIRLWIND_COOLDOWN;
	}

	/** Index in {@link #ALL} (network form), -1 none. */
	public static int index(@Nullable Identifier art) {
		return art == null ? -1 : ALL.indexOf(art);
	}
}
