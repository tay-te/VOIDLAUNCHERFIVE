package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.repair.fire.FireLeash;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A night raid (docs/LIVING_WORLD_2.md §3.1, §3.3): at 02:00 the band's party comes in from its road, makes for the
 * storehouse and a barn; the town's alarm is raised and folk hide (the npc module's alarm), the guards fight
 * ({@link RaidDefence}), raiders carry sacks out to the road, and one sets the barn alight (live fire rules). The
 * outcome is the books' unless players fight: every raider a player kills weakens the band in the outcome, and the
 * defenders earn {@code defense:raid_repelled}.
 */
final class RaidScene implements SceneDirector {
	static final RaidScene INSTANCE = new RaidScene();
	/** The most raiders shown (the band's strength beyond it is in the books). */
	static final int MAX_SHOWN = 6;

	private RaidScene() {
	}

	enum Step {
		COME, LOOT, OFF, FIGHT
	}

	/** One raider: where it comes from, what it is after, what it carries. */
	static final class Raider implements Folk.Controller {
		final SceneRun run;
		final BlockPos entry;
		final BlockPos goal;
		final boolean torch;
		Step step = Step.COME;
		long since;
		boolean sack;
		@Nullable LivingEntity foe;
		boolean dead;
		boolean escaped;

		Raider(SceneRun run, BlockPos entry, BlockPos goal, boolean torch) {
			this.run = run;
			this.entry = entry;
			this.goal = goal;
			this.torch = torch;
		}

		@Override
		public void tick(ServerLevel level, Folk folk) {
			if (this.run.ended) {
				folk.discard();
				return;
			}
			long t = level.getGameTime();
			if (this.foe != null && (!this.foe.isAlive() || this.foe.distanceToSqr(folk) > 16 * 16)) {
				this.foe = null;
				if (this.step == Step.FIGHT) {
					this.step = this.sack ? Step.OFF : Step.COME;
				}
			}
			if (this.foe == null && (t & 15) == 0) {
				// a guard or an armed player close by is fought; the rest of the town is ignored
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, folk.getBoundingBox().inflate(4), x -> x != folk && x.isAlive())) {
					if (e instanceof Player p && !p.isCreative() && !p.isSpectator() || e instanceof Folk f && f.job() == JobType.GUARD) {
						this.foe = e;
						this.step = Step.FIGHT;
						break;
					}
				}
			}
			switch (this.step) {
				case FIGHT -> Strangers.fight(level, folk, this.foe);
				case COME -> {
					if (folk.blockPosition().distSqr(this.goal) <= 3 * 3) {
						this.step = Step.LOOT;
						this.since = t;
						folk.getNavigation().stop();
					} else if (t % 30 == 0) {
						go(level, folk, this.goal, 0.95);
					}
				}
				case LOOT -> {
					if (t - this.since > 160) {
						if (this.torch) {
							light(level, this.run);
						} else {
							this.sack = true;
							folk.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BUNDLE));
						}
						this.step = Step.OFF;
					} else if (t % 20 == 0) {
						folk.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
					}
				}
				case OFF -> {
					if (folk.blockPosition().distSqr(this.entry) <= 3 * 3) {
						this.escaped = true;
						folk.discard();
					} else if (t % 30 == 0) {
						go(level, folk, this.entry, 1.0);
					}
				}
			}
		}

		@Override
		public void interact(ServerPlayer player, Folk folk) {
			folk.getLookControl().setLookAt(player);
			this.foe = player;
			this.step = Step.FIGHT;
		}

		@Override
		public void hurt(ServerLevel level, Folk folk, DamageSource source) {
			if (source.getEntity() instanceof LivingEntity e) {
				this.foe = e;
				this.step = Step.FIGHT;
			}
		}

		@Override
		public void died(ServerLevel level, Folk folk, DamageSource source) {
			this.dead = true;
			this.run.event.args().putDouble("raiders_lost", this.run.event.args().getDoubleOr("raiders_lost", 0) + 1);
			if (source.getEntity() instanceof ServerPlayer p) {
				// a player's kill weakens the band in the outcome (the guards' own are in the town's defence already)
				this.run.iv.add(p.getUUID(), "raiders_killed", 1);
				Map<UUID, Integer> kills = this.run.get("kills");
				if (kills == null) {
					kills = new HashMap<>();
					this.run.put("kills", kills);
				}
				kills.merge(p.getUUID(), 1, Integer::sum);
			}
		}
	}

	/**
	 * A raider makes for a place across the town: the path's search reaches as far as the place is (the folk's own follow
	 * range is a street's length), and where no whole path is found they make for a point a dozen blocks on toward it.
	 */
	static void go(ServerLevel level, Folk folk, BlockPos to, double speed) {
		var nav = folk.getNavigation();
		double d = Math.sqrt(folk.blockPosition().distSqr(to));
		nav.setRequiredPathLength((float) Math.min(96.0, d * 1.5 + 16.0));
		// a whole path, or part of the way (the next try goes on from nearer)
		if (nav.moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, speed) && nav.getPath() != null && nav.getPath().getNodeCount() > 1) {
			return;
		}
		if (d > 14) {
			BlockPos at = folk.blockPosition();
			BlockPos step = at.offset((int) Math.round((to.getX() - at.getX()) / d * 12), 0, (int) Math.round((to.getZ() - at.getZ()) / d * 12));
			BlockPos g = EventDressings.ground(level, step);
			if (g != null && Math.abs(g.getY() - at.getY()) <= 4) {
				nav.moveTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, speed);
			}
		}
	}

	@Override
	public void begin(SceneRun run, Settlement s) {
		ServerLevel level = run.level();
		long t0 = System.nanoTime();
		BlockPos entry = entry(run.world, s, run.event.args().getLongOr("band", 0));
		Scenes.COSTS.merge("raid entry", System.nanoTime() - t0, Math::max);
		if (entry == null) {
			return;
		}
		// fewer bodies on a slow server (the band's full strength is in the books either way)
		int cap = dev.voidmc.hearth.sim.LoadCaps.cap(MAX_SHOWN, 2, dev.voidmc.hearth.sim.LoadCaps.mspt(level.getServer()),
			dev.voidmc.hearth.sim.LoadCaps.highMspt());
		run.put("entry", entry);
		run.put("to_come", Math.min(cap, Math.max(2, run.event.args().getIntOr("strength", 3))));
		run.put("raiders", new ArrayList<Folk>());
		run.put("controllers", new ArrayList<Raider>());
		run.put("rng", RandomSource.create(run.event.seed()));
		long t1 = System.nanoTime();
		comeIn(run, s, 1);
		Scenes.COSTS.merge("raid first raider", System.nanoTime() - t1, Math::max);
		// the alarm (the town's folk run for cover) is raised on the next pass: a scene's start is one short tick
	}

	/** Up to {@code most} more raiders come in from the road (one a pass: a party spawning at once is a long tick). */
	private static void comeIn(SceneRun run, Settlement s, int most) {
		Integer left = run.get("to_come");
		BlockPos entry = run.get("entry");
		if (left == null || entry == null || left <= 0) {
			return;
		}
		List<Folk> raiders = run.get("raiders");
		List<Raider> controllers = run.get("controllers");
		RandomSource r = run.get("rng");
		for (int k = 0; k < most && left > 0; k++, left--) {
			int i = controllers.size();
			boolean torch = i == 1 && run.event.plot() >= 0;
			Raider c = new Raider(run, entry, torch ? run.venue("barn") : run.venue("storehouse"), torch);
			Folk f = Strangers.spawn(run.level(), entry, s.culture(), JobType.BANDIT, r, c, "");
			if (f == null) {
				continue;
			}
			Strangers.arm(f, false, r);
			if (torch) {
				f.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH));
			}
			raiders.add(f);
			controllers.add(c);
			run.spawned(f);
		}
		run.put("to_come", left);
	}

	/** Where the band comes in: the town's edge on the side of its road, on open ground. */
	static @Nullable BlockPos entry(HearthWorld w, Settlement s, long band) {
		ServerLevel level = w.level();
		BlockPos from = RoadBands.road(w, band);
		BlockPos c = s.center();
		double dx = from == null ? 1 : from.getX() - c.getX();
		double dz = from == null ? 0 : from.getZ() - c.getZ();
		double len = Math.max(1, Math.hypot(dx, dz));
		// the gate only if known already: working out the town's exits is a long tick of its own, and the side the band's
		// road comes in on is as good a way in
		BlockPos gate = SceneVenues.gateIfKnown(s);
		BlockPos any = null;
		for (int r = 44; r >= 16; r -= 4) {
			BlockPos p = gate != null && r == 44 ? gate : c.offset((int) Math.round(dx / len * r), 0, (int) Math.round(dz / len * r));
			BlockPos g = EventDressings.ground(level, p);
			if (g == null) {
				continue;
			}
			// open ground, not a roof (the top block of a column in a town is often one): near the land's own height
			if (gate != null && r == 44 || street(w, g)) {
				return g;
			}
			if (any == null) {
				any = g;
			}
		}
		return any;
	}

	/** Whether a column's top is about the land's own height (a street or a yard, not a roof). */
	static boolean street(HearthWorld w, BlockPos g) {
		try {
			return Math.abs(g.getY() - w.terrain().groundY(g.getX(), g.getZ())) <= 3;
		} catch (RuntimeException e) {
			return true;
		}
	}

	private static void alarm(ServerLevel level, Settlement s, BlockPos at) {
		try {
			NpcModule.embodiment().town(s).raiseAlarm(level.getGameTime(), at, 2400);
		} catch (RuntimeException | LinkageError e) {
			Hearth.LOG.debug("events: the alarm could not be raised", e);
		}
		Scenes.toll(level, s, 1);
		for (ServerPlayer p : EventDirector.playersNear(level, at, 96)) {
			p.sendSystemMessage(Component.translatable("hearth.events.raid.alarm", s.name()));
		}
	}

	/** The torch-bearer sets the barn alight: a real fire on the leash if live fires are on, else smoke over it. */
	static void light(ServerLevel level, SceneRun run) {
		Settlement s = run.settlement();
		if (s == null || run.event.plot() < 0 || run.get("barn_fire") != null) {
			return;
		}
		var p = s.plot(run.event.plot());
		if (p == null) {
			return;
		}
		BoundingBox box = FireScene.box(p);
		run.put("barn_fire", box);
		if (TownEvents.liveFires(level.getServer()) && FireLeash.light(level, box, 2) > 0) {
			run.put("barn_leash", FireLeash.hold(level, box, level.getGameTime() + 6000));
			run.event.args().putBoolean("barn_live", true);
		}
	}

	@Override
	public void tick(SceneRun run, Settlement s, long gameTime) {
		if (run.get("entry") != null && run.get("alarmed") == null) {
			run.put("alarmed", Boolean.TRUE);
			alarm(run.level(), s, run.venue("storehouse"));
			return;
		}
		comeIn(run, s, 1);
		BoundingBox barn = run.get("barn_fire");
		if (barn != null && run.get("barn_leash") == null) {
			BlockPos roof = run.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				new BlockPos(barn.getCenter().getX(), 0, barn.getCenter().getZ()));
			EventDressings.particles(run.level(), "smoke", roof, 5);
			EventDressings.particles(run.level(), "flame", roof.below(), 6);
		}
		if (gameTime % 200 == 0) {
			try {
				NpcModule.embodiment().town(s).raiseAlarm(gameTime, run.venue("storehouse"), 400);
			} catch (RuntimeException | LinkageError ignored) {
				// the alarm is the npc module's
			}
		}
	}

	@Override
	public boolean done(SceneRun run, Settlement s) {
		List<Raider> cs = run.get("controllers");
		Integer left = run.get("to_come");
		if (cs == null || cs.isEmpty() || left != null && left > 0 || run.level().getGameTime() - run.startedAt < 1200) {
			return false;
		}
		for (Raider c : cs) {
			if (!c.dead && !c.escaped) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void end(SceneRun run, Settlement s) {
		// the defenders are thanked by the town: +15 for a raider, up to +25
		Map<UUID, Integer> kills = run.get("kills");
		if (kills != null) {
			for (Map.Entry<UUID, Integer> k : kills.entrySet()) {
				ServerPlayer p = run.level().getServer().getPlayerList().getPlayer(k.getKey());
				if (p != null) {
					Hooks.deed(p, s, "defense:raid_repelled", Math.min(25, 10 + 5 * k.getValue()), null, run.anchor, true);
				}
			}
		}
		FireLeash.Leash leash = run.get("barn_leash");
		if (leash != null) {
			// the barn burns on a while after the raiders are gone; the leash keeps it to its box until it is out
			run.put("barn_leash", null);
		}
		List<Folk> raiders = run.get("raiders");
		if (raiders != null) {
			for (Folk f : raiders) {
				if (!f.isRemoved()) {
					f.markFolded();
					f.setController(null);
					f.discard();
				}
			}
		}
	}

	static BlockPos middle(Settlement s, int plot) {
		var p = s.plot(plot);
		return p == null ? s.center() : Venues.middle(p.spec());
	}

	/** Whether a folk is one of a running raid's raiders (the guards' fight looks for them). */
	static boolean raider(net.minecraft.world.entity.Entity e) {
		return e instanceof Folk f && f.controller() instanceof Raider r && !r.run.ended;
	}

	/** The nearest raider within reach of a guard, or null. */
	static @Nullable Folk nearestRaider(NpcService.FolkHandle guard, double reach) {
		ServerLevel level = (ServerLevel) guard.entity().level();
		Folk best = null;
		double bd = reach * reach;
		for (Folk f : level.getEntitiesOfClass(Folk.class, guard.entity().getBoundingBox().inflate(reach), RaidScene::raider)) {
			double d = f.distanceToSqr(guard.entity());
			if (d < bd) {
				bd = d;
				best = f;
			}
		}
		return best;
	}
}
