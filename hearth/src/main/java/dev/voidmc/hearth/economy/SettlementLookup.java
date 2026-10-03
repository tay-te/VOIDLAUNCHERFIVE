package dev.voidmc.hearth.economy;

import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.economy.sandbox.Sandbox;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** Finds settlements by id or name across every level the sim runs in, plus the dev sandbox's. */
public final class SettlementLookup {
	private SettlementLookup() {
	}

	public static @Nullable Settlement find(MinecraftServer server, long id) {
		for (ServerLevel level : server.getAllLevels()) {
			HearthWorld world = HearthWorld.of(level);
			if (world != null) {
				Settlement s = world.settlement(id);
				if (s != null) {
					return s;
				}
			}
		}
		return Sandbox.find(id);
	}

	/** Every live settlement (and sandbox ones), for commands. */
	public static List<Settlement> all(MinecraftServer server) {
		List<Settlement> out = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			HearthWorld world = HearthWorld.of(level);
			if (world != null) {
				out.addAll(world.settlements());
			}
		}
		out.addAll(Sandbox.all());
		return out;
	}

	/** By id (digits) or by name, ignoring case. */
	public static @Nullable Settlement byName(MinecraftServer server, String nameOrId) {
		String key = nameOrId.trim();
		if (key.chars().allMatch(Character::isDigit) && !key.isEmpty()) {
			try {
				Settlement s = find(server, Long.parseLong(key));
				if (s != null) {
					return s;
				}
			} catch (NumberFormatException ignored) {
				// fall through to the name
			}
		}
		for (Settlement s : all(server)) {
			if (s.name().toLowerCase(Locale.ROOT).equals(key.toLowerCase(Locale.ROOT))) {
				return s;
			}
		}
		return null;
	}
}
