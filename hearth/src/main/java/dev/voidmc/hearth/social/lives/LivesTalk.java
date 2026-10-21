package dev.voidmc.hearth.social.lives;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.crowd.Barks;
import dev.voidmc.hearth.npc.crowd.Role;
import dev.voidmc.hearth.npc.talk.PlayerTalk;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.lives.Lives;
import dev.voidmc.hearth.sim.lives.LivesConfig;
import dev.voidmc.hearth.sim.lives.LivesView;
import dev.voidmc.hearth.sim.lives.TownLives;
import dev.voidmc.hearth.social.Hooks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Folk who talk where the player can hear (docs/LIVING_WORLD_2.md §4.1, §4.3): every {@code pass_ticks}, for each player
 * in an embodied town, a named character within range may say one of their role's remarks ({@code lives.<role>} barks,
 * the old soldier's war story, the gossip's latest), and two folk at leisure near each other, a few blocks off, may talk
 * about the player — after a deed the town is talking of ({@code lives.overheard.<deed>}, and an answer). Through the
 * crowds' bark cooldowns (§0.2), so a street never chatters. Cost: one folk search a player a pass.
 */
final class LivesTalk {
	static final LivingWorldStats.Timer TIMER = LivingWorldStats.timer("lives talk");
	private static long tick;

	private LivesTalk() {
	}

	static void tick(MinecraftServer server) {
		LivesConfig.Talk cfg = Lives.config().talk();
		if (!Lives.config().lives().enabled() || ++tick % cfg.passTicks() != 0) {
			return;
		}
		NpcService npc = HearthServices.npc();
		if (npc == null) {
			return;
		}
		long t0 = System.nanoTime();
		int players = 0;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator() || !(p.level() instanceof ServerLevel level)) {
				continue;
			}
			HearthWorld w = HearthWorld.of(level);
			Settlement s = w == null ? null : w.settlementAt(p.blockPosition());
			if (s == null || !s.embodied()) {
				continue;
			}
			pass(level, s, p, npc, cfg);
			players++;
		}
		long took = System.nanoTime() - t0;
		TIMER.add(took);
		PASSES.increment();
		NANOS.add(took);
		PLAYERS.add(players);
	}

	static final java.util.concurrent.atomic.LongAdder PASSES = new java.util.concurrent.atomic.LongAdder();
	static final java.util.concurrent.atomic.LongAdder NANOS = new java.util.concurrent.atomic.LongAdder();
	static final java.util.concurrent.atomic.LongAdder PLAYERS = new java.util.concurrent.atomic.LongAdder();

	private static void pass(ServerLevel level, Settlement s, ServerPlayer p, NpcService npc, LivesConfig.Talk cfg) {
		List<NpcService.FolkHandle> near = npc.folkNear(level, p.position(), cfg.radius());
		if (near.isEmpty()) {
			return;
		}
		var random = p.getRandom();
		var title = Hooks.title(p, s);
		// a named character's remark
		if (random.nextDouble() < cfg.remarkChance()) {
			for (NpcService.FolkHandle f : near) {
				TownLives.Notable n = LivesView.notable(s, f.resident().id);
				if (n != null && f.awake()) {
					String beatCat = "lives." + n.role + "." + n.beat;
					boolean said = !Barks.lines(beatCat).isEmpty() && random.nextInt(2) == 0
						&& Barks.say(f, beatCat, Role.of(f.resident(), false), p, title);
					if (!said) {
						Barks.say(f, "lives." + n.role, Role.of(f.resident(), false), p, title);
					}
					break;
				}
			}
		}
		// two folk talking about the player, a little way off
		if (random.nextDouble() < cfg.overheardChance()) {
			// the standing module's days (the world's), not the town's own calendar (a town run ahead keeps its own)
			long today = dev.voidmc.hearth.npc.NpcTime.today();
			PlayerTalk.Deed deed = PlayerTalk.townTalk(s, p.getUUID(), today, 10, level.getGameTime());
			if (deed == null) {
				return;
			}
			List<NpcService.FolkHandle> idle = new ArrayList<>();
			for (NpcService.FolkHandle f : near) {
				if (f.awake() && f.resident().age != AgeStage.CHILD && f.entity().distanceToSqr(p) >= 9 && !npc.onDuty(s, f.resident())) {
					idle.add(f);
				}
			}
			for (int i = 0; i < idle.size(); i++) {
				for (int j = i + 1; j < idle.size(); j++) {
					NpcService.FolkHandle a = idle.get(i);
					NpcService.FolkHandle b = idle.get(j);
					if (a.entity().distanceToSqr(b.entity()) <= 25) {
						String cat = "lives.overheard." + deed.slug();
						if (Barks.lines(cat).isEmpty()) {
							cat = "lives.overheard." + (deed.weight() >= 0 ? "good" : "bad");
						}
						a.lookAt(b.entity());
						b.lookAt(a.entity());
						if (Barks.say(a, cat, Role.of(a.resident(), false), p, title)) {
							Barks.say(b, "lives.overheard.answer." + (deed.weight() >= 0 ? "good" : "bad"), Role.of(b.resident(), false), null, null);
							LivesFolk.overheard(s, p, deed.slug().toLowerCase(Locale.ROOT));
						}
						return;
					}
				}
			}
		}
	}
}
