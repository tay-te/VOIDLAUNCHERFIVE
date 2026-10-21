package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.npc.caravan.Caravans;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.life.Venues;
import dev.voidmc.hearth.social.office.Office;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The lord's tournament (docs/LIVING_WORLD_2.md §3.1, §3.3): the gentry joust at the tilt yard — two mounted folk
 * charging along the barrier, three passes, the herald crying them — and then the <b>melee for all comers</b>: a ring of
 * fences, the town's champion in mail, three rounds against a player who entered ("Enter the melee"). Both yield at a
 * quarter of their health, so nobody dies; two rounds of three win the purse, a horse and the lord's favour.
 */
final class TourneyScene implements SceneDirector {
	static final TourneyScene INSTANCE = new TourneyScene();
	/** A pass every 30 seconds, three of them. */
	static final int PASS_TICKS = 600;

	private TourneyScene() {
	}

	// ---------------------------------------------------------------- the joust

	@Override
	public void begin(SceneRun run, Settlement s) {
		BlockPos[] ends = lists(run, s);
		if (ends == null) {
			return;
		}
		run.put("ends", ends);
		List<UUID> knights = run.role("knights");
		List<AbstractHorse> horses = new ArrayList<>();
		RandomSource r = RandomSource.create(run.event.seed());
		for (int i = 0; i < Math.min(2, knights.size()); i++) {
			var f = EventDirector.folk(knights.get(i));
			BlockPos g = EventDressings.ground(run.level(), ends[i]);
			if (f == null || g == null) {
				continue;
			}
			Horse h = EntityTypes.HORSE.create(run.level(), EntitySpawnReason.EVENT);
			if (h == null) {
				continue;
			}
			h.addTag(Caravans.TAG);
			h.setTamed(true);
			h.setPersistenceRequired();
			h.snapTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, r.nextFloat() * 360, 0);
			if (!run.level().addFreshEntity(h)) {
				continue;
			}
			run.spawned(h);
			horses.add(h);
			f.entity().teleportTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
			f.entity().startRiding(h, true, false);
			f.hold(new ItemStack(Items.STICK)); // the lance
		}
		run.put("horses", horses);
	}

	/** The two ends of the lists, either side of the barrier: along the tilt yard's long side, four blocks in from its ends. */
	static BlockPos @Nullable [] lists(SceneRun run, Settlement s) {
		PlotView yard = SceneVenues.first(s, "civic/tilt_yard");
		BlockPos mid = run.venue("tilt_yard");
		if (yard == null) {
			return null;
		}
		boolean alongX = yard.spec().sizeX() >= yard.spec().sizeZ();
		int half = Math.max(6, (alongX ? yard.spec().sizeX() : yard.spec().sizeZ()) / 2 - 4);
		Direction along = alongX ? Direction.EAST : Direction.SOUTH;
		Direction side = along.getClockWise();
		return new BlockPos[]{mid.relative(along, half).relative(side, 2), mid.relative(along, -half).relative(side, -2)};
	}

	@Override
	public void tick(SceneRun run, Settlement s, long gameTime) {
		joust(run, s, gameTime);
		melee(run, s, gameTime);
	}

	private void joust(SceneRun run, Settlement s, long gameTime) {
		List<AbstractHorse> horses = run.get("horses");
		BlockPos[] ends = run.get("ends");
		if (horses == null || ends == null || horses.size() < 2) {
			return;
		}
		long t = gameTime - run.startedAt;
		int pass = (int) (t / PASS_TICKS);
		if (pass >= 3) {
			if (run.get("dismounted") == null) {
				run.put("dismounted", Boolean.TRUE);
				for (AbstractHorse h : horses) {
					h.ejectPassengers();
					h.discard();
				}
			}
			return;
		}
		boolean charging = t % PASS_TICKS > 60;
		for (int i = 0; i < 2; i++) {
			AbstractHorse h = horses.get(i);
			BlockPos to = ends[(i + pass + (charging ? 1 : 0)) % 2];
			if (gameTime % 20 == 0) {
				h.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, charging ? 2.2 : 1.0);
			}
		}
		Integer clashed = run.get("clash");
		if (charging && (clashed == null || clashed != pass) && horses.get(0).distanceToSqr(horses.get(1)) < 3.5 * 3.5) {
			run.put("clash", pass);
			ServerLevel level = run.level();
			BlockPos at = horses.get(0).blockPosition();
			level.playSound(null, at, SoundEvents.SHIELD_BLOCK.value(), SoundSource.NEUTRAL, 1.6F, 0.8F);
			level.playSound(null, at, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.NEUTRAL, 1.0F, 1.3F);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, at.getX() + 0.5, at.getY() + 2, at.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.2);
			for (UUID id : run.role("herald")) {
				var f = EventDirector.folk(id);
				if (f != null) {
					EventBarks.say(f, "event_tournament_herald");
				}
			}
		}
	}

	// ---------------------------------------------------------------- the melee

	/** The champion's controller: fights the player whose round it is, and only them. */
	static final class Champion implements Folk.Controller {
		final SceneRun run;
		@Nullable UUID foe;

		Champion(SceneRun run) {
			this.run = run;
		}

		@Override
		public void tick(ServerLevel level, Folk folk) {
			if (this.run.ended) {
				folk.discard();
				return;
			}
			ServerPlayer p = this.foe == null ? null : level.getServer().getPlayerList().getPlayer(this.foe);
			BlockPos ring = this.run.venue("ring");
			if (p == null || p.blockPosition().distSqr(ring) > 7 * 7 || Boolean.TRUE.equals(this.run.get("pause"))) {
				if (folk.blockPosition().distSqr(ring) > 4 && level.getGameTime() % 30 == 0) {
					folk.getNavigation().moveTo(ring.getX() + 0.5, ring.getY(), ring.getZ() + 0.5, 0.7);
				}
				return;
			}
			Strangers.fight(level, folk, p);
		}

		@Override
		public void interact(ServerPlayer player, Folk folk) {
			player.sendSystemMessage(Component.translatable("hearth.events.melee.enter_first"), true);
		}
	}

	private void melee(SceneRun run, Settlement s, long gameTime) {
		if (run.minute < run.rule.param("melee_from", 60)) {
			return;
		}
		Folk champ = run.get("champion");
		ServerLevel level = run.level();
		if (champ == null) {
			BlockPos g = EventDressings.ground(level, run.venue("ring"));
			if (g == null) {
				return;
			}
			Champion c = new Champion(run);
			RandomSource r = RandomSource.create(run.event.seed() * 3);
			champ = Strangers.spawn(level, g, s.culture(), JobType.GUARD, r, c, "");
			if (champ == null) {
				return;
			}
			Strangers.arm(champ, true, r);
			run.spawned(champ);
			run.put("champion", champ);
			run.put("champion_ctl", c);
		}
		Champion c = run.get("champion_ctl");
		if (c == null) {
			return;
		}
		Long pauseUntil = run.get("pause_until");
		run.put("pause", pauseUntil != null && gameTime < pauseUntil);
		if (c.foe == null) {
			// the next entrant standing in the ring takes their turn
			List<UUID> queue = run.get("melee_queue");
			if (queue == null || queue.isEmpty()) {
				return;
			}
			for (UUID id : queue) {
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
				if (p != null && p.blockPosition().distSqr(run.venue("ring")) <= 6 * 6) {
					c.foe = id;
					champ.setHealth(champ.getMaxHealth());
					p.sendSystemMessage(Component.translatable("hearth.events.melee.begin", rounds(run, id) + 1).withStyle(ChatFormatting.GOLD), true);
					break;
				}
			}
		}
	}

	static int rounds(SceneRun run, UUID player) {
		java.util.Map<UUID, int[]> m = run.get("melee_rounds");
		int[] r = m == null ? null : m.get(player);
		return r == null ? 0 : r[0] + r[1];
	}

	/** A player enters the melee (the fee paid in the conversation). */
	static boolean enter(SceneRun run, ServerPlayer player) {
		List<UUID> queue = run.get("melee_queue");
		if (queue == null) {
			queue = new ArrayList<>();
			run.put("melee_queue", queue);
		}
		if (queue.contains(player.getUUID()) || queue.size() >= 4) {
			return false;
		}
		queue.add(player.getUUID());
		return true;
	}

	/**
	 * Yielding (§3.3: a quarter of their health): a blow that would take the duelling player or the champion below a
	 * quarter ends the round instead of landing. Returns whether the damage may land.
	 */
	static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		for (SceneRun run : Scenes.allRuns()) {
			if (run.event.kind() != EventKind.TOURNAMENT) {
				continue;
			}
			Folk champ = run.get("champion");
			Champion c = run.get("champion_ctl");
			if (champ == null || c == null) {
				continue;
			}
			if (victim == champ) {
				if (!(attacker instanceof ServerPlayer p) || !p.getUUID().equals(c.foe)) {
					return false; // nobody else may set on the champion
				}
				if (champ.getHealth() - amount < champ.getMaxHealth() / 4) {
					round(run, c, p, true);
					return false;
				}
				return true;
			}
			if (victim instanceof ServerPlayer p && p.getUUID().equals(c.foe) && attacker == champ && p.getHealth() - amount < p.getMaxHealth() / 4) {
				round(run, c, p, false);
				return false;
			}
		}
		return true;
	}

	private static void round(SceneRun run, Champion c, ServerPlayer p, boolean won) {
		java.util.Map<UUID, int[]> m = run.get("melee_rounds");
		if (m == null) {
			m = new java.util.HashMap<>();
			run.put("melee_rounds", m);
		}
		int[] r = m.computeIfAbsent(p.getUUID(), k -> new int[2]);
		r[won ? 0 : 1]++;
		run.level().playSound(null, p.blockPosition(), won ? SoundEvents.PLAYER_LEVELUP : SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 0.8F,
			1.0F);
		p.sendSystemMessage(Component.translatable(won ? "hearth.events.melee.round_won" : "hearth.events.melee.round_lost", r[0], r[1])
			.withStyle(won ? ChatFormatting.GREEN : ChatFormatting.RED));
		run.iv.best(p.getUUID(), p.getName().getString(), "melee", r[0]);
		run.put("pause_until", run.level().getGameTime() + 100);
		if (r[0] >= 2 || r[1] >= 2 || r[0] + r[1] >= 3) {
			c.foe = null;
			List<UUID> queue = run.get("melee_queue");
			if (queue != null) {
				queue.remove(p.getUUID());
			}
			Settlement s = run.settlement();
			if (r[0] >= 2 && s != null) {
				prize(run, s, p);
			}
			ContestLines.result(p, ContestLines.melee(run, p.getUUID(), r[0] >= 2 ? "won" : "lost"));
		}
	}

	/** The purse, a horse and the lord's favour. */
	private static void prize(SceneRun run, Settlement s, ServerPlayer p) {
		long purse = (long) run.rule.param("purse", 40);
		Hooks.pay(p, purse);
		Hooks.deed(p, s, "social:tourney_won", 5, null, p.blockPosition(), true);
		UUID lord = s.data(SocialModule.LEDGER).offices.get(Office.LORD.id());
		StandingService standing = HearthServices.standing();
		if (lord != null && standing != null) {
			standing.remember(lord, p.getUUID(), "tourney_won", 20);
		}
		Horse h = EntityTypes.HORSE.create(run.level(), EntitySpawnReason.EVENT);
		if (h != null) {
			h.setTamed(true);
			h.setOwner(p);
			h.snapTo(p.getX() + 1, p.getY(), p.getZ() + 1, 0, 0);
			run.level().addFreshEntity(h);
		}
		p.sendSystemMessage(Component.translatable("hearth.events.melee.won", purse).withStyle(ChatFormatting.GOLD));
	}

	@Override
	public void end(SceneRun run, Settlement s) {
		List<AbstractHorse> horses = run.get("horses");
		if (horses != null) {
			for (AbstractHorse h : horses) {
				h.ejectPassengers();
			}
		}
		for (UUID id : run.role("knights")) {
			var f = EventDirector.folk(id);
			if (f != null) {
				f.entity().stopRiding();
			}
		}
	}

	static BlockPos middle(PlotView p) {
		return Venues.middle(p.spec());
	}
}
