package dev.voidmc.expanse.entity.life;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Butterflies by day and moths by night. A butterfly drifts about a patch of flowers in a bobbing,
 * wandering flight, settles on a bloom with its wings slowly opening and closing, and flutters off when
 * someone walks up (sneak, and it will let you look). At dusk, or in the rain, it settles wherever it is
 * until the weather or the morning comes back. Moths keep the opposite hours and are drawn to lanterns
 * and torches.
 *
 * <p>Seven species: monarch, cabbage white, morpho, brimstone and red admiral fly by day; luna and
 * tiger moths by night. Which one turns up depends on the climate and the hour.
 */
public class Butterfly extends Critter {
	public static final int MONARCH = 0;
	public static final int CABBAGE_WHITE = 1;
	public static final int MORPHO = 2;
	public static final int BRIMSTONE = 3;
	public static final int RED_ADMIRAL = 4;
	public static final int LUNA_MOTH = 5;
	public static final int TIGER_MOTH = 6;
	public static final String[] VARIANTS = {"monarch", "cabbage_white", "morpho", "brimstone", "red_admiral", "luna_moth", "tiger_moth"};

	private @Nullable Vec3 target;
	private boolean landing;
	private @Nullable BlockPos home;
	private int restTicks;
	private int retarget;

	public Butterfly(EntityType<? extends Butterfly> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 2.0).add(Attributes.MOVEMENT_SPEED, 0.2);
	}

	public boolean isMoth() {
		return this.getVariant() >= LUNA_MOTH;
	}

	@Override
	protected int variantCount() {
		return VARIANTS.length;
	}

	@Override
	protected int pickVariant(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
		if (level.getLevel().isDarkOutside()) {
			return LUNA_MOTH + random.nextInt(2);
		}
		float t = temperature(level, pos);
		if (t >= 0.9F) {
			// monarch, white, morpho, brimstone, admiral
			return pick(random, 4, 1, 4, 3, 2);
		}
		if (t < 0.5F) {
			return pick(random, 1, 4, 0, 2, 3);
		}
		return pick(random, 4, 4, 0, 3, 3);
	}

	@Override
	protected boolean isOutOfHours() {
		return this.isMoth() ? this.level().isBrightOutside() : this.level().isDarkOutside();
	}

	/** Night for a butterfly, day for a moth, and rain for both. */
	private boolean wantsToSettle(ServerLevel level) {
		return this.isOutOfHours() || level.isRainingAt(this.blockPosition());
	}

	@Override
	public void tick() {
		super.tick();
		if (this.isResting()) {
			this.setDeltaMovement(Vec3.ZERO);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.home == null) {
			this.home = this.blockPosition();
		}
		boolean sleepy = this.wantsToSettle(level);
		if (this.isResting()) {
			if ((this.tickCount + this.getId()) % 4 == 0 && this.nearestUnsneaking(2.5, 0.0) != null) {
				this.takeOff();
			} else if (--this.restTicks <= 0 && !sleepy) {
				this.takeOff();
			} else if (this.tickCount % 20 == 0 && !this.supported(level)) {
				this.takeOff();
			}
			return;
		}
		this.setFlying(true);
		if (sleepy && !this.landing) {
			this.target = this.groundBelow(level);
			this.landing = this.target != null;
			this.retarget = 200;
		}
		Player near = (this.tickCount + this.getId()) % 5 == 0 ? this.nearestUnsneaking(2.0, 0.0) : null;
		if (near != null) {
			// Away from the hand that reached for it, and up.
			Vec3 away = this.position().subtract(near.position()).multiply(1, 0, 1).normalize().scale(4.0);
			this.target = this.position().add(away).add(0, 1.5, 0);
			this.landing = false;
			this.retarget = 30;
		}
		if (this.target == null || --this.retarget <= 0 || this.horizontalCollision && this.random.nextInt(3) == 0) {
			this.pickTarget(level);
		}
		if (this.target == null) {
			return;
		}
		// A bobbing, wandering flight rather than a straight line, calming as it comes in to land.
		double phase = (this.tickCount + this.getId() * 7) * 0.45;
		double wobble = this.landing ? Math.min(1.0, this.position().distanceTo(this.target) / 2.0) : 1.0;
		Vec3 to = this.target.add(Math.sin(phase * 0.7) * 0.5 * wobble, Math.sin(phase) * 0.4 * wobble, Math.cos(phase * 0.6) * 0.5 * wobble);
		this.steerTowards(to, this.landing ? 0.07 : 0.1, 0.12);
		double arrive = this.landing ? 0.12 : 0.8;
		if (this.position().distanceToSqr(this.target) < arrive * arrive) {
			if (this.landing) {
				this.settle(sleepy);
			} else {
				this.pickTarget(level);
			}
		}
	}

	private void pickTarget(ServerLevel level) {
		this.retarget = 60 + this.random.nextInt(80);
		this.landing = false;
		BlockPos here = this.blockPosition();
		if (this.isMoth() && level.isDarkOutside()) {
			// Moths: drawn to the brightest lamp nearby, circling it.
			BlockPos lamp = null;
			int best = 7;
			for (int i = 0; i < 8; i++) {
				BlockPos p = here.offset(this.random.nextInt(17) - 8, this.random.nextInt(7) - 3, this.random.nextInt(17) - 8);
				int light = level.getBrightness(LightLayer.BLOCK, p);
				if (light > best && level.getBlockState(p).isAir()) {
					best = light;
					lamp = p;
				}
			}
			if (lamp != null) {
				this.target = Vec3.atCenterOf(lamp).add(this.random.nextDouble() - 0.5, this.random.nextDouble() - 0.5, this.random.nextDouble() - 0.5);
				return;
			}
		}
		if (this.random.nextFloat() < 0.55F) {
			Vec3 flower = this.findFlower(level, here, 6);
			if (flower != null) {
				this.target = flower;
				this.landing = true;
				return;
			}
		}
		// Wander about home, low over the ground.
		BlockPos h = this.home != null && this.home.closerToCenterThan(this.position(), 20.0) ? this.home : here;
		double x = h.getX() + 0.5 + (this.random.nextDouble() * 2 - 1) * 6;
		double z = h.getZ() + 0.5 + (this.random.nextDouble() * 2 - 1) * 6;
		// (under the trees, not over them)
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
		this.target = new Vec3(x, ground + 0.6 + this.random.nextDouble() * 2.4, z);
	}

	/** The top of a bloom within reach, if one is found in a few looks. */
	private @Nullable Vec3 findFlower(LevelAccessor level, BlockPos around, int r) {
		for (int i = 0; i < 10; i++) {
			BlockPos p = around.offset(this.random.nextInt(2 * r + 1) - r, this.random.nextInt(5) - 2, this.random.nextInt(2 * r + 1) - r);
			BlockState state = level.getBlockState(p);
			if (!state.is(BlockTags.FLOWERS)) {
				continue;
			}
			if (state.hasProperty(DoublePlantBlock.HALF) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
				p = p.above();
				state = level.getBlockState(p);
			}
			double top = state.getShape(level, p).isEmpty() ? 0.6 : state.getShape(level, p).max(net.minecraft.core.Direction.Axis.Y);
			if (!level.getBlockState(p.above()).isAir() && top >= 0.95) {
				continue;
			}
			return new Vec3(p.getX() + 0.5, p.getY() + Math.min(top, 1.0) + 0.02, p.getZ() + 0.5);
		}
		return null;
	}

	/** Somewhere to sit out the night or the rain: the ground or leaves below, never the water. */
	private @Nullable Vec3 groundBelow(ServerLevel level) {
		int x = Mth.floor(this.getX());
		int z = Mth.floor(this.getZ());
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		BlockPos below = new BlockPos(x, top - 1, z);
		if (!level.getBlockState(below).getFluidState().isEmpty() || top > this.getY() + 1) {
			return null;
		}
		Vec3 flower = this.findFlower(level, this.blockPosition(), 3);
		return flower != null ? flower : new Vec3(this.getX(), top + 0.02, this.getZ());
	}

	private boolean supported(ServerLevel level) {
		BlockPos at = BlockPos.containing(this.getX(), this.getY() - 0.05, this.getZ());
		return !level.getBlockState(at).isAir() || !level.getBlockState(at.below()).isAir();
	}

	private void settle(boolean sleepy) {
		if (this.target != null) {
			this.setPos(this.target.x, this.target.y, this.target.z);
		}
		this.setDeltaMovement(Vec3.ZERO);
		this.setResting(true);
		this.setFlying(false);
		this.restTicks = sleepy ? 600 : 80 + this.random.nextInt(260);
		this.landing = false;
		this.target = null;
	}

	private void takeOff() {
		this.setResting(false);
		this.setFlying(true);
		this.landing = false;
		this.target = null;
		this.retarget = 0;
		this.setDeltaMovement(0.0, 0.12, 0.0);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (this.isResting()) {
			this.takeOff();
		}
		return super.hurtServer(level, source, damage);
	}

	@Override
	protected float getSoundVolume() {
		return 0.0F;
	}

	/** Butterflies come to flowers: there must be a bloom within a few blocks of where one appears. */
	public static boolean checkSpawn(EntityType<Butterfly> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (!nearAPlayer(level, reason, pos) || !outdoors(level, pos) || !open(level, pos) || level.getLevel().isRaining()) {
			return false;
		}
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = -2; dy <= 1; dy++) {
					if (level.getBlockState(p.setWithOffset(pos, dx, dy, dz)).is(BlockTags.FLOWERS)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.home != null) {
			output.store("Home", BlockPos.CODEC, this.home);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.home = input.read("Home", BlockPos.CODEC).orElse(null);
		this.restTicks = 40;
	}
}
