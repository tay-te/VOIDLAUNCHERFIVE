package dev.voidmc.mettle.character.moment;

import dev.voidmc.mettle.api.Perk;
import dev.voidmc.mettle.api.Skill;
import dev.voidmc.mettle.core.net.MettlePayloads;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/** Sends progress moments (UI.md §6b) to the player they happened to. Server thread. */
public final class Moments {
	private Moments() {
	}

	public static void level(ServerPlayer player, ResourceKey<Skill> skill, int level, int tierOpened) {
		send(player, new MettlePayloads.Moment(tierOpened > 0 ? MomentKind.TIER : MomentKind.LEVEL, Optional.of(skill), level, tierOpened, Optional.empty(), 0));
	}

	public static void character(ServerPlayer player, int level) {
		send(player, new MettlePayloads.Moment(MomentKind.CHARACTER, Optional.empty(), level, 0, Optional.empty(), 0));
	}

	public static void perk(ServerPlayer player, ResourceKey<Perk> key, Perk perk, int rank) {
		MomentKind kind = switch (perk.kind()) {
			case ROOT, PATH -> MomentKind.LEARNED;
			case MASTERY -> MomentKind.MASTERY;
			case FEAT -> MomentKind.FEAT;
			case SYNERGY -> MomentKind.SYNERGY;
			case TEACHING -> MomentKind.TEACHING;
		};
		send(player, new MettlePayloads.Moment(kind, Optional.of(perk.skill()), 0, 0, Optional.of(key.identifier()), rank));
	}

	private static void send(ServerPlayer player, MettlePayloads.Moment moment) {
		if (ServerPlayNetworking.canSend(player, MettlePayloads.Moment.TYPE)) {
			ServerPlayNetworking.send(player, moment);
		}
	}
}
