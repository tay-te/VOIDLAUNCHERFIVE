package dev.voidmc.hearth.repair.folk;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.NpcService.FolkBehaviour;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.repair.FireWatch;
import dev.voidmc.hearth.repair.RepairConfig;
import dev.voidmc.hearth.repair.RepairModule;
import dev.voidmc.hearth.repair.RepairRuntime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The bucket brigade (docs/LIVING_WORLD.md §2): while a fire burns in a settlement's buildings, awake adults within
 * {@code radius} of it (up to {@code max_folk}) drop what they are doing (priority 250, a reaction), raise the alarm, and
 * run between the nearest water and the nearest flame with buckets, dousing a few blocks of fire each trip. Where there is
 * no water near, they beat the flames out with shovels, slower. When the last flame is out they say so and go back to
 * their day; the builders then rebuild from the repair queue.
 */
public final class BrigadeBehaviour implements FolkBehaviour {
	public static final Identifier ID = RepairModule.id("brigade");

	private enum Phase { FETCH, FILL, CARRY, BEAT }

	private static final class Run {
		Phase phase = Phase.FETCH;
		@Nullable BlockPos target;
		int ticks;
		int legTicks;
	}

	private final Map<UUID, Run> runs = new HashMap<>();

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int priority() {
		return RepairModule.config().fire().priority();
	}

	@Override
	public boolean wants(FolkHandle f) {
		RepairConfig.Fire cfg = RepairModule.config().fire();
		RepairRuntime rt = RepairRuntime.current();
		if (rt == null || !cfg.enabled() || !cfg.brigade() || f.resident().age == AgeStage.CHILD || !f.awake()) {
			return false;
		}
		Settlement s = f.settlement();
		FireWatch.Episode e = rt.fires.episode(s.id());
		if (e == null || e.flames() == 0 || e.level() != f.entity().level()) {
			return false;
		}
		if (e.inBrigade(f.resident().id)) {
			return true;
		}
		return e.brigadeSize() < cfg.maxFolk() && f.entity().blockPosition().distSqr(e.origin) <= (double) cfg.radius() * cfg.radius();
	}

	@Override
	public void start(FolkHandle f) {
		RepairRuntime rt = RepairRuntime.current();
		FireWatch.Episode e = rt == null ? null : rt.fires.episode(f.settlement().id());
		if (e == null) {
			return;
		}
		e.join(f.resident().id);
		this.runs.put(f.resident().id, new Run());
		f.say(Component.translatable("hearth.repair.say.fire_alarm." + f.entity().getRandom().nextInt(3)), null);
	}

	@Override
	public boolean tick(FolkHandle f) {
		RepairRuntime rt = RepairRuntime.current();
		Run run = this.runs.get(f.resident().id);
		FireWatch.Episode e = rt == null ? null : rt.fires.episode(f.settlement().id());
		if (run == null || e == null || !(f.entity().level() instanceof ServerLevel level)) {
			return false;
		}
		if (e.flames() == 0) {
			f.say(Component.translatable("hearth.repair.say.fire_out." + f.entity().getRandom().nextInt(2)), null);
			return false;
		}
		RepairConfig.Fire cfg = RepairModule.config().fire();
		PathfinderMob folk = f.entity();
		run.ticks++;
		switch (run.phase) {
			case FETCH -> {
				f.hold(new ItemStack(Items.BUCKET));
				if (run.target == null) {
					run.target = nearest(e.water(cfg.waterSearch()), folk.blockPosition());
					run.legTicks = 0;
					if (run.target == null) {
						run.phase = Phase.BEAT;
						return true;
					}
				}
				if (walk(f, run, run.target, 1.0, 2.5)) {
					run.phase = Phase.FILL;
					run.ticks = 0;
					f.lookAt(run.target);
				}
			}
			case FILL -> {
				f.stop();
				if (run.ticks == 1) {
					f.swing();
					level.playSound(null, run.target, SoundEvents.BUCKET_FILL, SoundSource.NEUTRAL, 1.0F, 1.0F);
				}
				if (run.ticks >= 15) {
					f.hold(new ItemStack(Items.WATER_BUCKET));
					run.phase = Phase.CARRY;
					run.target = null;
					run.legTicks = 0;
				}
			}
			case CARRY -> {
				f.hold(new ItemStack(Items.WATER_BUCKET));
				BlockPos flame = e.nearestFlame(folk.blockPosition());
				if (flame == null) {
					return true;
				}
				if (run.target == null || !run.target.equals(flame)) {
					run.target = flame;
					run.legTicks = 0;
				}
				if (walk(f, run, flame, 1.1, 3.5)) {
					f.lookAt(flame);
					f.swing();
					douse(level, e, flame, cfg.douseRadius());
					f.hold(new ItemStack(Items.BUCKET));
					run.phase = Phase.FETCH;
					run.target = null;
					run.legTicks = 0;
				}
			}
			case BEAT -> {
				f.hold(new ItemStack(Items.IRON_SHOVEL));
				BlockPos flame = e.nearestFlame(folk.blockPosition());
				if (flame == null) {
					return true;
				}
				if (walk(f, run, flame, 1.0, 2.5) && run.ticks % 30 == 0) {
					f.lookAt(flame);
					f.swing();
					douse(level, e, flame, Math.max(0, cfg.douseRadius() - 1));
				}
			}
		}
		return true;
	}

	/** Walks toward a spot; true once within {@code reach} of it (or after a fair try, if close enough to throw). */
	private static boolean walk(FolkHandle f, Run run, BlockPos to, double speed, double reach) {
		PathfinderMob folk = f.entity();
		double d2 = folk.distanceToSqr(Vec3.atCenterOf(to));
		if (d2 <= reach * reach) {
			f.stop();
			return true;
		}
		if (run.legTicks % 30 == 0) {
			f.moveTo(to, speed);
		}
		run.legTicks++;
		// cannot get closer (the fire is up a wall, the well behind a fence): throw from where one stands
		return run.legTicks > 200 && d2 <= 8 * 8 || run.legTicks > 400;
	}

	private static void douse(ServerLevel level, FireWatch.Episode e, BlockPos at, int radius) {
		int n = e.douse(at, radius);
		Vec3 c = Vec3.atCenterOf(at);
		level.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 24, 0.8, 0.4, 0.8, 0.1);
		if (n > 0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.5, c.z, 8, 0.6, 0.4, 0.6, 0.02);
			level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 1.6F + level.getRandom().nextFloat() * 0.4F);
		}
		level.playSound(null, at, SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}

	private static @Nullable BlockPos nearest(List<BlockPos> list, BlockPos from) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : list) {
			double d = p.distSqr(from);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	@Override
	public void stop(FolkHandle f) {
		this.runs.remove(f.resident().id);
		RepairRuntime rt = RepairRuntime.current();
		FireWatch.Episode e = rt == null ? null : rt.fires.episode(f.settlement().id());
		if (e != null) {
			e.leave(f.resident().id);
		}
		f.stop();
		f.hold(ItemStack.EMPTY);
	}

	public int running() {
		return this.runs.size();
	}
}
