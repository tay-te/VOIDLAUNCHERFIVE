package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.caravan.Caravans;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * The small events with beasts in them (docs/LIVING_WORLD_2.md §3.1, the smaller ones): a runaway pig chased through the
 * streets by children (catch it — lead it, or just stand in its way — and its owner thanks you), the horse fair's horses
 * in their pen, the pig led to the butcher on slaughter day. The beasts are views: never saved, gone when it ends.
 */
final class BeastScene implements SceneDirector {
	static final BeastScene INSTANCE = new BeastScene();

	private BeastScene() {
	}

	@Override
	public void begin(SceneRun run, Settlement s) {
		switch (run.event.kind()) {
			case RUNAWAY_PIG -> spawn(run, EntityTypes.PIG, run.venue("square"), 1);
			case HORSE_FAIR -> spawn(run, EntityTypes.HORSE, run.venue("square"), 3);
			case SLAUGHTER_DAY -> spawn(run, EntityTypes.PIG, run.venue("square"), 1);
			default -> {
			}
		}
	}

	private static void spawn(SceneRun run, EntityType<? extends Mob> type, BlockPos at, int count) {
		List<Mob> beasts = new ArrayList<>();
		RandomSource r = RandomSource.create(run.event.seed());
		for (int i = 0; i < count; i++) {
			BlockPos g = EventDressings.ground(run.level(), at.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2));
			if (g == null) {
				continue;
			}
			Mob m = type.create(run.level(), EntitySpawnReason.EVENT);
			if (m == null) {
				continue;
			}
			m.addTag(Caravans.TAG);
			m.setPersistenceRequired();
			m.snapTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, r.nextFloat() * 360, 0);
			if (run.level().addFreshEntity(m)) {
				beasts.add(m);
				run.spawned(m);
			}
		}
		run.put("beasts", beasts);
	}

	@Override
	public void tick(SceneRun run, Settlement s, long gameTime) {
		List<Mob> beasts = run.get("beasts");
		if (beasts == null || beasts.isEmpty()) {
			return;
		}
		Mob first = beasts.get(0);
		if (first.isRemoved()) {
			return;
		}
		switch (run.event.kind()) {
			case RUNAWAY_PIG -> {
				if (run.get("caught") != null) {
					return;
				}
				// it bolts this way and that; the children run after it
				if (gameTime % 60 == 0) {
					RandomSource r = first.getRandom();
					BlockPos to = run.venue("square").offset(r.nextInt(25) - 12, 0, r.nextInt(25) - 12);
					first.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.4);
				}
				for (UUID id : run.role("children")) {
					Scenes.Order o = Scenes.order(id);
					if (o != null) {
						o.target = first.blockPosition();
						o.focus = first.blockPosition();
						o.speed = 0.9;
						o.moved = false;
					}
				}
				// a player who gets a hand on it (leads it, or corners it) has caught it
				Player p = run.level().getNearestPlayer(first, 1.6);
				if (p == null && first.isLeashed() && first.getLeashHolder() instanceof Player holder) {
					p = holder;
				}
				if (p != null && !p.isSpectator()) {
					run.put("caught", p.getUUID());
					run.iv.set(p.getUUID(), "caught");
					first.getNavigation().stop();
					EventDressings.particles(run.level(), "happy", first.blockPosition(), 6);
					if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
						dev.voidmc.hearth.social.Hooks.deed(sp, s, "social:caught_the_pig", 1, null, first.blockPosition(), true);
						sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("hearth.events.pig.caught"), true);
					}
				}
			}
			case SLAUGHTER_DAY -> {
				if (run.minute >= run.rule.param("slaughter_at", 30)) {
					EventDressings.particles(run.level(), "dust", first.blockPosition(), 8);
					first.discard();
				} else if (gameTime % 80 == 0) {
					BlockPos to = run.venue("square");
					first.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0.8);
				}
			}
			default -> {
			}
		}
	}
}
