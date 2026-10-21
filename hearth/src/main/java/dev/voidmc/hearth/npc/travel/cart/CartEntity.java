package dev.voidmc.hearth.npc.travel.cart;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * {@code hearth:cart} (docs/LIVING_WORLD_2.md §1.3): a handcart pushed by its pedlar, a two-wheel cart behind a mule or
 * an ox, a covered four-wheel wagon behind two horses. A trailer: it follows what draws it at the hitch distance, turns
 * toward it, tilts with the ground under its axles, and its wheels turn with the distance it rolls (on the client). Its
 * load is drawn from what it carries. Never saved (the party is rebuilt from the books), never carries anyone, never
 * breaks.
 */
public class CartEntity extends Entity {
	public enum Kind {
		HANDCART(1.4, 0.9F), CART(2.6, 1.3F), WAGON(3.4, 1.7F);

		/** Blocks from the cart's middle to what draws it. */
		public final double hitch;
		/** Half the distance between its axles (front and back), for the tilt. */
		public final float half;

		Kind(double hitch, float half) {
			this.hitch = hitch;
			this.half = half;
		}

		static Kind of(int i) {
			return values()[Math.floorMod(i, values().length)];
		}
	}

	/** What a cart carries, drawn on its bed. */
	public enum Load {
		NONE, SACKS, BARRELS, CRATES, LOGS, STONE, PRODUCE, BEDROLLS;

		static Load of(int i) {
			return values()[Math.floorMod(i, values().length)];
		}
	}

	private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(CartEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_LOAD = SynchedEntityData.defineId(CartEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_HITCH = SynchedEntityData.defineId(CartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_PITCH = SynchedEntityData.defineId(CartEntity.class, EntityDataSerializers.FLOAT);

	/** Client: how far the wheels have turned, in blocks rolled. */
	public double rolled;
	public double rolledO;
	private @Nullable Vec3 lastPos;

	public CartEntity(EntityType<? extends CartEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(DATA_KIND, (byte) 1);
		entityData.define(DATA_LOAD, (byte) 0);
		entityData.define(DATA_HITCH, 0);
		entityData.define(DATA_PITCH, 0F);
	}

	public Kind kind() {
		return Kind.of(this.entityData.get(DATA_KIND));
	}

	public Load load() {
		return Load.of(this.entityData.get(DATA_LOAD));
	}

	public float tilt() {
		return this.entityData.get(DATA_PITCH);
	}

	public void setup(Kind kind, Load load) {
		this.entityData.set(DATA_KIND, (byte) kind.ordinal());
		this.entityData.set(DATA_LOAD, (byte) load.ordinal());
	}

	/** What draws it (a draught animal, or the pedlar pushing a handcart). */
	public void hitchTo(@Nullable Entity puller) {
		this.entityData.set(DATA_HITCH, puller == null ? 0 : puller.getId());
	}

	public @Nullable Entity puller() {
		int id = this.entityData.get(DATA_HITCH);
		return id == 0 ? null : this.level().getEntity(id);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			// the wheels turn with the ground rolled over
			Vec3 p = position();
			this.rolledO = this.rolled;
			if (this.lastPos != null) {
				double moved = Math.hypot(p.x - this.lastPos.x, p.z - this.lastPos.z);
				if (moved < 2) {
					this.rolled += moved;
				}
			}
			this.lastPos = p;
			return;
		}
		Entity puller = puller();
		if (puller == null || !puller.isAlive()) {
			// unhitched: it stands where it was left, settled on the ground
			settle();
			return;
		}
		follow(puller);
	}

	/** Trails the puller at the hitch distance, facing it, on the ground under it. */
	void follow(Entity puller) {
		Vec3 me = position();
		Vec3 to = puller.position();
		double dx = to.x - me.x;
		double dz = to.z - me.z;
		double dist = Math.hypot(dx, dz);
		double hitch = kind().hitch;
		if (dist > 24) {
			// far behind (a teleport of its party): catch up at once, behind the puller
			float yaw = puller.getYRot() * Mth.DEG_TO_RAD;
			setPos(to.x + Math.sin(yaw) * hitch, to.y, to.z - Math.cos(yaw) * hitch);
			return;
		}
		if (dist > 1e-3) {
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			this.setYRot(Mth.rotLerp(0.35F, this.getYRot(), yaw));
			if (dist > hitch) {
				double step = Math.min(dist - hitch, 0.9);
				double nx = me.x + dx / dist * step;
				double nz = me.z + dz / dist * step;
				setPos(nx, groundY(nx, nz, me.y), nz);
			} else {
				setPos(me.x, groundY(me.x, me.z, me.y), me.z);
			}
		}
		// the tilt: the ground under the front axle against the ground under the back
		float yaw = this.getYRot() * Mth.DEG_TO_RAD;
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		float half = kind().half;
		double front = groundY(getX() + fx * half, getZ() + fz * half, getY());
		double back = groundY(getX() - fx * half, getZ() - fz * half, getY());
		float pitch = (float) (Mth.atan2(front - back, half * 2) * Mth.RAD_TO_DEG);
		this.entityData.set(DATA_PITCH, Mth.clamp(pitch, -35F, 35F));
	}

	private void settle() {
		double y = groundY(getX(), getZ(), getY());
		if (Math.abs(y - getY()) > 1e-3) {
			setPos(getX(), y, getZ());
		}
	}

	/**
	 * The standing height near {@code nearY} at a point: the top of the first solid block at most a step up and a few down
	 * with room over it for the cart (two blocks clear), so a cart passing under eaves or beside a stair stays on the
	 * ground and never climbs onto a roof (lane A4's work tour).
	 */
	double groundY(double x, double z, double nearY) {
		return groundY(this.level(), x, z, nearY);
	}

	/** As {@link #groundY(double, double, double)}, on any block getter (the tests). */
	public static double groundY(net.minecraft.world.level.BlockGetter level, double x, double z, double nearY) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		// a step up at most: the block the cart stands in now, or the one above it
		int top = Mth.floor(nearY + 0.05);
		for (int y = top; y > top - 6; y--) {
			p.set(bx, y, bz);
			BlockState s = level.getBlockState(p);
			net.minecraft.world.phys.shapes.VoxelShape shape = s.getCollisionShape(level, p);
			if (shape.isEmpty()) {
				continue;
			}
			double h = Math.min(1.0, shape.max(net.minecraft.core.Direction.Axis.Y));
			boolean room = true;
			for (int k = 1; k <= 2 && room; k++) {
				q.set(bx, y + k, bz);
				room = level.getBlockState(q).getCollisionShape(level, q).isEmpty();
			}
			if (room || y + h <= nearY + 0.05) {
				// (a solid block at or below the cart's feet is the ground, whatever hangs over it)
				return y + h;
			}
		}
		return nearY;
	}

	@Override
	public void move(MoverType type, Vec3 delta) {
		// moved only by its puller (follow); pushed by nothing
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
