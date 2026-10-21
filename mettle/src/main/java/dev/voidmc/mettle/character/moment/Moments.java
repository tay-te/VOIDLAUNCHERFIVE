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
			case ROOT, PATH, CURSED -> MomentKind.LEARNED;
			case MASTERY -> MomentKind.MASTERY;
			case FEAT -> MomentKind.FEAT;
			case SYNERGY -> MomentKind.SYNERGY;
			case TEACHING -> MomentKind.TEACHING;
		};
		send(player, new MettlePayloads.Moment(kind, Optional.of(perk.skill()), 0, 0, Optional.of(key.identifier()), rank));
	}

	/**
	 * A build feel milestone (docs/BUILD_FEEL.md §2.4): {@code subject} is the card's perk, the keyword, the Mythic; the
	 * words are the server's (translated on the client).
	 */
	public static void milestone(ServerPlayer player, MomentKind kind, Optional<net.minecraft.resources.Identifier> subject, int tier,
		net.minecraft.network.chat.Component text, net.minecraft.network.chat.Component sub) {
		send(player, new MettlePayloads.Moment(kind, Optional.empty(), 0, tier, subject, 0, Optional.of(text), Optional.ofNullable(sub)));
	}

	private static void send(ServerPlayer player, MettlePayloads.Moment moment) {
		if (ServerPlayNetworking.canSend(player, MettlePayloads.Moment.TYPE)) {
			ServerPlayNetworking.send(player, moment);
		}
	}
}
