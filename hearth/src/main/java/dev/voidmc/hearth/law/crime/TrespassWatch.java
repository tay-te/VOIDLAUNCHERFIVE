package dev.voidmc.hearth.law.crime;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.api.LawService.Owner;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.LawConfig;
import dev.voidmc.hearth.law.own.OwnerRules;
import dev.voidmc.hearth.law.own.Ownership;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Trespass (docs/LIVING_WORLD.md §4): being inside someone's home at night, or a shop or workshop after hours, or the
 * guardhouse at any time. The first folk to see the intruder tells them to get out; still there after a few seconds
 * (and still seen), it is a crime. Checked once a second for players in towns.
 */
public final class TrespassWatch {
	/** Seconds between the warning and the crime. */
	static final int GRACE_SECONDS = 6;
	private static final Map<UUID, Warning> WARNED = new HashMap<>();

	private TrespassWatch() {
	}

	private record Warning(long settlementId, int plot, long at) {
	}

	public static void check(ServerPlayer player) {
		if (!Law.config().crimes().trespass() || Law.exempt(player)) {
			return;
		}
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		Ownership.Place place = Ownership.place(level, pos);
		if (place == null || place.plot() == null || place.owner() == null || !closed(level, place) || level.canSeeSky(pos.above())) {
			WARNED.remove(player.getUUID());
			return;
		}
		Owner owner = place.owner();
		if (Ownership.isOwner(owner, player.getUUID()) || owner.kind() == Owner.Kind.PLAYER && !Law.config().protectPlayerProperty()) {
			return;
		}
		if (dev.voidmc.hearth.law.lock.Locks.keyFor(player.getInventory(), place.settlement().id(), place.plot().id()) != null) {
			return; // a key is leave to be here (or a good story)
		}
		List<Witnesses.Witness> seen = Witnesses.find(level, player, 0.0);
		if (seen.isEmpty()) {
			return;
		}
		Warning w = WARNED.get(player.getUUID());
		long now = level.getGameTime();
		if (w == null || w.settlementId != place.settlement().id() || w.plot != place.plot().id()) {
			WARNED.put(player.getUUID(), new Warning(place.settlement().id(), place.plot().id(), now));
			Witnesses.Witness first = seen.get(0);
			try {
				first.folk().lookAt(player);
				first.folk().say(Component.translatable("hearth.law.say.get_out"), player);
			} catch (RuntimeException e) {
				Hearth.LOG.debug("law: folk could not speak", e);
			}
			player.sendOverlayMessage(Component.translatable("hearth.law.trespass.warned").withStyle(ChatFormatting.GOLD));
			return;
		}
		if (now - w.at >= GRACE_SECONDS * 20L) {
			WARNED.put(player.getUUID(), new Warning(w.settlementId, w.plot, now + 20 * 60));
			Settlement s = place.settlement();
			CrimeDesk.commit(level, player, s, Offence.TRESPASS, pos, owner.resident(), 0, 0.0, null);
		}
	}

	/** Whether the building is closed to strangers right now. */
	static boolean closed(ServerLevel level, Ownership.Place place) {
		ArchetypeCategory category = OwnerRules.category(place.plot().spec().archetype());
		String path = place.plot().spec().archetype().getPath();
		LawConfig.Stealth cfg = Law.config().stealth();
		int minute = Law.minuteOfDay(level);
		return switch (category) {
			case HOUSING, NOBLE -> Law.within(minute, cfg.nightStart(), cfg.nightEnd());
			case LAW -> path.contains("jail") || path.contains("watch");
			case WORKPLACE, COMMERCE, STORAGE, CIVIC -> !open(path) && Law.within(minute, cfg.shopClose(), cfg.shopOpen());
			case UNDERGROUND -> path.contains("dwelling") && Law.within(minute, cfg.nightStart(), cfg.nightEnd());
			default -> false;
		};
	}

	private static boolean open(String path) {
		return path.endsWith("inn") || path.endsWith("tavern") || path.endsWith("hostel") || path.endsWith("chapel") || path.endsWith("temple")
			|| path.endsWith("cathedral") || path.endsWith("shrine") || path.endsWith("caravanserai") || path.endsWith("bathhouse");
	}

	public static void forget(UUID player) {
		WARNED.remove(player);
	}

	public static void clear() {
		WARNED.clear();
	}
}
