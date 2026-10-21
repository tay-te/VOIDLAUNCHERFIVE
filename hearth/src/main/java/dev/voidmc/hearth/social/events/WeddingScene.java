package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.Hooks;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * A wedding (docs/LIVING_WORLD_2.md §3.1): the procession from the couple's home to the chapel with the musician in front
 * (the timeline), the ceremony and the feast (town life's gathering), dancing in pairs at the feast. This director keeps
 * the dance: a player who stands in the ring at the feast a while dances too, and is remembered for it.
 */
final class WeddingScene implements SceneDirector {
	static final WeddingScene INSTANCE = new WeddingScene();
	static final int DANCE_SECONDS = 15;

	private WeddingScene() {
	}

	@Override
	public void tick(SceneRun run, Settlement s, long gameTime) {
		if (run.minute < run.rule.param("feast_from", 90)) {
			return;
		}
		Map<UUID, Integer> near = run.get("dancing");
		if (near == null) {
			near = new HashMap<>();
			run.put("dancing", near);
		}
		for (ServerPlayer p : EventDirector.playersNear(run.level(), run.venue("feast"), 5)) {
			int secs = near.merge(p.getUUID(), 1, Integer::sum);
			if (secs == DANCE_SECONDS) {
				Hooks.deed(p, s, "social:danced", 1, run.event.principals().isEmpty() ? null : run.event.principals().get(0), p.blockPosition(), true);
				p.sendSystemMessage(Component.translatable("hearth.events.wedding.danced"), true);
				EventDressings.particles(run.level(), "notes", p.blockPosition(), 4);
			}
		}
	}
}
