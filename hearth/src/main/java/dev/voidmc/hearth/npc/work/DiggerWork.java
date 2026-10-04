package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Clay diggers and salters when no pit or pan is open to them as an excavation: they dig at the bank or rake the pan by
 * their workplace — clay and sand along water, salt in the pans — with shovel or rake, the dust of it, and a load
 * carried to the store every few bouts. Nothing is dug out of the world here (only excavations change the land); the
 * yield is the sim's from their working time.
 */
final class DiggerWork implements WorkBehaviour {
	private final JobType job;
	private int bouts;

	DiggerWork(JobType job) {
		this.job = job;
	}

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		ResidentPlaces p = brain.places(level);
		this.bouts++;
		boolean salt = this.job == JobType.SALTER;
		ItemStack load = new ItemStack(salt ? Items.SUGAR : Items.CLAY_BALL);
		if (this.bouts % 3 == 0 && p.workStorage != null) {
			return new Tasks.Linger(p.workStorage.pos(), p.workStorage.pos().relative(p.workStorage.facing()), FolkPose.CRAFT, 50, load, load.copy(),
				Tasks.every(40, SoundEvents.BARREL_OPEN, 0.4F, null, true), salt ? "storing salt" : "storing clay");
		}
		BlockPos bank = bank(level, p.work, salt);
		BlockPos stand = bank != null ? FolkSpots.standableNear(level, bank.above(), 2) : null;
		BlockPos at = stand != null ? stand : p.work;
		BlockPos look = bank != null ? bank : p.work.below();
		BlockState ground = level.isLoaded(look) ? level.getBlockState(look) : Blocks.CLAY.defaultBlockState();
		BlockState dust = ground.isAir() ? (salt ? Blocks.CONCRETE_POWDER.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState() : Blocks.CLAY.defaultBlockState()) : ground;
		Tasks.Effect dig = Tasks.every(salt ? 26 : 20, salt ? SoundEvents.SAND_STEP : SoundEvents.GRAVEL_HIT, 0.7F, (b, lvl, e, x) -> {
			Vec3 c = Vec3.atCenterOf(look).add(0, 0.5, 0);
			lvl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, dust), c.x, c.y, c.z, 6, 0.3, 0.1, 0.3, 0.05);
		}, true);
		return new Tasks.Linger(at, look, salt ? FolkPose.TEND : FolkPose.HAMMER, 500 + brain.random.nextInt(300), new ItemStack(Items.IRON_SHOVEL),
			ItemStack.EMPTY, dig, salt ? "raking the salt pans" : "digging clay at the bank");
	}

	/** Clay, sand or mud by water near the workplace (for salters: sand or anything white by the water). */
	private static @Nullable BlockPos bank(ServerLevel level, BlockPos at, boolean salt) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int r = 2; r <= 14; r += 3) {
			for (int dx = -r; dx <= r; dx += 2) {
				for (int dz = -r; dz <= r; dz += 2) {
					for (int dy = -3; dy <= 1; dy++) {
						m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
						if (!level.isLoaded(m)) {
							continue;
						}
						BlockState s = level.getBlockState(m);
						boolean fits = salt ? s.is(Blocks.SAND) || s.is(Blocks.CONCRETE_POWDER.pick(net.minecraft.world.item.DyeColor.WHITE)) || s.is(Blocks.CALCITE)
							: s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(Blocks.SAND) || s.is(Blocks.GRAVEL);
						if (fits && level.getBlockState(m.above()).isAir()) {
							return m.immutable();
						}
					}
				}
			}
		}
		return null;
	}
}
