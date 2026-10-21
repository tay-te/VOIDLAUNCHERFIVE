package dev.voidmc.mettle.character.cards.legend;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.cards.CardCooldowns;
import dev.voidmc.mettle.character.cards.CardHooks;
import dev.voidmc.mettle.character.cards.Mythics;
import dev.voidmc.mettle.mixin.legend.LegendMobAccess;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AgeableWaterCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** The Ferryman's Stride, the Cat's Way, the Beastspeaker's Torc and the Greenway: the ways of the land and its beasts. */
public final class LegendWild {
	public static final String CATS_WAY = "cats_way";
	/** The Cat's Way: 8 s between runs, 2 s a run (the client's own clock: client.legend.CatsWay). */
	public static final int CATS_WAY_TICKS = 160;
	public static final int WALL_RUN_TICKS = 40;
	static final int FERRY_RANGE = 4;
	static final long FOLLOW_TICKS = 24000;

	/** The Ferryman's Stride: an ally within 4 blocks of a holder, so the water bears them too while they sprint. */
	public static final AttachmentType<Boolean> BORNE = AttachmentRegistry.create(Mettle.id("legend/borne"), b -> b
		.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	/** The Beastspeaker's Torc: whom a fed animal follows, and until when (game time). Saved with the animal. */
	public record Follow(UUID player, long until) {
		static final Codec<Follow> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(Follow::player),
			Codec.LONG.fieldOf("until").forGetter(Follow::until)).apply(i, Follow::new));
	}

	public static final AttachmentType<Follow> FOLLOWS = AttachmentRegistry.create(Mettle.id("legend/follows"), b -> b.persistent(Follow.CODEC));

	private static final Set<Block> VINES = Set.of(Blocks.VINE, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT, Blocks.WEEPING_VINES, Blocks.WEEPING_VINES_PLANT,
		Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT);
	/** Animals following a holder, with their follow goal added (live, loaded ones only). */
	private static final Map<Mob, FollowGoal> FOLLOWERS = new WeakHashMap<>();
	/** The Cat's Way: the game time a holder's fall is forgiven until. */
	private static final Map<UUID, Long> WALL_RUN = new HashMap<>();

	private LegendWild() {
	}

	private static boolean has(Player player, String id) {
		return Mythics.active(player, id);
	}

	static void init() {
		BORNE.toString();
		FOLLOWS.toString();
		ServerTickEvents.END_SERVER_TICK.register(LegendWild::tick);
		ServerPlayerEvents.LEAVE.register(p -> WALL_RUN.remove(p.getUUID()));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.hasAttached(FOLLOWS)) {
				follow(mob);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(LegendPayloads.WallRun.TYPE, (payload, context) -> wallRun(context.player()));
	}

	// ---------------------------------------------------------------- the Ferryman's Stride (both sides)

	/** LivingEntity#canStandOnFluid: while a holder (or an ally near one) sprints, still water bears them like ground. */
	public static boolean standsOnWater(LivingEntity entity, FluidState fluid, boolean vanilla) {
		if (vanilla || !(entity instanceof Player p) || !p.isSprinting() || !fluid.is(FluidTags.WATER) || p.isSpectator() || p.getAbilities().flying
			|| p.isUnderWater()) {
			return vanilla;
		}
		return has(p, "ferrymans_stride") || Boolean.TRUE.equals(p.getAttached(BORNE));
	}

	/** Where the water bears a runner: just under its surface, so they run on it rather than wade (a strider stands at half). */
	private static final VoxelShape SURFACE = Block.column(16.0, 0.0, 14.0);

	/** LivingEntity#getLiquidCollisionShape: the floor still water gives a runner the stride bears (LiquidBlock asks canStandOnFluid too). */
	public static VoxelShape waterFloor(LivingEntity entity, VoxelShape vanilla) {
		if (!vanilla.isEmpty() || !(entity instanceof Player p) || !p.isSprinting() || p.isUnderWater() || p.isSpectator()) {
			return vanilla;
		}
		return has(p, "ferrymans_stride") || Boolean.TRUE.equals(p.getAttached(BORNE)) ? SURFACE : vanilla;
	}

	/** On the surface of still water the stride bears: feet in its upper half, no water above (Entity#updateFluidInteraction). */
	public static boolean borneOnWater(Entity entity) {
		if (!(entity instanceof Player p) || !p.isSprinting()) {
			return false;
		}
		BlockPos feet = BlockPos.containing(p.getX(), p.getY(), p.getZ());
		FluidState fluid = p.level().getFluidState(feet);
		return p.getY() - feet.getY() >= 0.45 && fluid.is(FluidTags.WATER) && fluid.isSource() && !p.level().getFluidState(feet.above()).is(FluidTags.WATER)
			&& standsOnWater(p, fluid, false);
	}

	private static void ferry(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			boolean borne = false;
			for (ServerPlayer holder : server.getPlayerList().getPlayers()) {
				if (holder.level() == p.level() && holder.distanceToSqr(p) <= FERRY_RANGE * FERRY_RANGE && CardHooks.helps(holder, p) && has(holder, "ferrymans_stride")) {
					borne = true;
					break;
				}
			}
			if (borne != Boolean.TRUE.equals(p.getAttached(BORNE))) {
				if (borne) {
					p.setAttached(BORNE, true);
				} else {
					p.removeAttached(BORNE);
				}
			}
		}
	}

	// ---------------------------------------------------------------- the Cat's Way

	/** The client began a wall run: the persisted cooldown starts, and the fall that follows is forgiven while it lasts. */
	private static void wallRun(ServerPlayer player) {
		if (!has(player, "cats_way") || CardCooldowns.remaining(player, CATS_WAY) > 10) {
			return;
		}
		CardCooldowns.start(player, CATS_WAY, CATS_WAY_TICKS);
		WALL_RUN.put(player.getUUID(), player.level().getGameTime() + WALL_RUN_TICKS + 5);
		player.resetFallDistance();
	}

	// ---------------------------------------------------------------- the Beastspeaker's Torc

	/** An animal (never a hostile) that wolves', bears', bees' and llamas' ways count for. */
	static boolean beast(Entity entity) {
		return !(entity instanceof Enemy) && (entity instanceof Animal || entity instanceof AgeableWaterCreature || entity instanceof WaterAnimal);
	}

	/** Mob#setTarget: a provoked animal never turns on a holder; its anger at them is let go. */
	public static boolean spared(Mob mob, LivingEntity target) {
		if (!(target instanceof Player player) || !beast(mob) || !has(player, "beastspeakers_torc")) {
			return false;
		}
		if (mob instanceof NeutralMob neutral && neutral.getPersistentAngerTarget() != null && neutral.getPersistentAngerTarget().matches(player)) {
			neutral.setPersistentAngerTarget(null);
			neutral.setPersistentAngerEndTime(-1L);
		}
		if (mob.getLastHurtByMob() == player) {
			mob.setLastHurtByMob(null);
		}
		return true;
	}

	/** Mob#usePlayerItem, AbstractHorse#fedFood: a holder fed this animal; it follows them for an in-game day. */
	public static void fed(Mob mob, Player player) {
		if (!(player instanceof ServerPlayer sp) || !beast(mob) || !has(sp, "beastspeakers_torc")) {
			return;
		}
		Follow before = mob.getAttached(FOLLOWS);
		mob.setAttached(FOLLOWS, new Follow(sp.getUUID(), sp.level().getGameTime() + FOLLOW_TICKS));
		follow(mob);
		if (before == null || !before.player().equals(sp.getUUID())) {
			LegendEffects.chip(sp, "beastspeakers_torc", Component.translatable("mettle.legend.torc", mob.getDisplayName()));
		}
	}

	private static void follow(Mob mob) {
		if (!(mob instanceof PathfinderMob pm) || FOLLOWERS.containsKey(mob)) {
			return;
		}
		FollowGoal goal = new FollowGoal(pm);
		((LegendMobAccess) mob).mettle$goals().addGoal(2, goal);
		FOLLOWERS.put(mob, goal);
	}

	/** Who this animal follows now (a holder online in its level, within its day), or null. */
	public static @Nullable ServerPlayer leader(Mob mob) {
		Follow f = mob.getAttached(FOLLOWS);
		if (f == null || mob.level().isClientSide()) {
			return null;
		}
		if (mob.level().getGameTime() > f.until()) {
			mob.removeAttached(FOLLOWS);
			return null;
		}
		ServerPlayer p = mob.level().getServer().getPlayerList().getPlayer(f.player());
		return p != null && p.level() == mob.level() && p.isAlive() && !p.isSpectator() && has(p, "beastspeakers_torc") ? p : null;
	}

	/** Walks after its leader, keeping two to four blocks off, as a tamed wolf does; idle when there is none. */
	private static final class FollowGoal extends Goal {
		private final PathfinderMob mob;
		private @Nullable ServerPlayer leader;
		private int repath;

		FollowGoal(PathfinderMob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			this.leader = leader(this.mob);
			return this.leader != null && !this.mob.isLeashed() && !this.mob.isPassenger() && this.mob.distanceToSqr(this.leader) > 16
				&& this.mob.distanceToSqr(this.leader) < 48 * 48;
		}

		@Override
		public boolean canContinueToUse() {
			return this.leader != null && this.leader == leader(this.mob) && !this.mob.isLeashed() && this.mob.distanceToSqr(this.leader) > 6
				&& this.mob.distanceToSqr(this.leader) < 48 * 48;
		}

		@Override
		public void start() {
			this.repath = 0;
		}

		@Override
		public void stop() {
			this.leader = null;
			this.mob.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (this.leader == null) {
				return;
			}
			this.mob.getLookControl().setLookAt(this.leader, 10.0f, this.mob.getMaxHeadXRot());
			if (--this.repath <= 0) {
				this.repath = 10;
				this.mob.getNavigation().moveTo(this.leader, 1.2);
			}
		}
	}

	// ---------------------------------------------------------------- the Greenway (both sides)

	/** SweetBerryBushBlock#entityInside: berry bushes neither slow nor scratch a holder. */
	public static boolean throughBushes(Entity entity) {
		return entity instanceof Player p && has(p, "greenway");
	}

	/**
	 * BlockBehaviour#getCollisionShape for leaves: natural leaves never hold a holder back. They walk through a canopy or a
	 * hedge from the side and still stand on one from above (sneak to drop through, as on scaffolding); leaves a player
	 * placed stay solid, so no wall of them lets anyone in.
	 */
	// The server's movement check asks with a "placement" context at the old position (getPreMoveCollisions): it must
	// get the same answer as the move itself, so placement is not excluded (leaves a player places are persistent anyway).
	public static boolean throughLeaves(BlockState state, CollisionContext context, BlockPos pos) {
		if (!(state.getBlock() instanceof LeavesBlock) || !state.hasProperty(LeavesBlock.PERSISTENT) || state.getValue(LeavesBlock.PERSISTENT)
			|| !(context instanceof EntityCollisionContext entityContext) || !(entityContext.getEntity() instanceof Player p)
			|| !has(p, "greenway")) {
			return false;
		}
		return !context.isAbove(Shapes.block(), pos, false) || context.isDescending();
	}

	/** LivingEntity#handleOnClimbable: in vines a holder keeps their pace (vanilla holds you to 0.15 a tick there). */
	public static boolean throughVines(LivingEntity entity) {
		return entity instanceof Player p && VINES.contains(entity.getInBlockState().getBlock()) && has(p, "greenway");
	}

	/** TargetGoal#canContinueToUse: a hostile that cannot see a holder forgets them twice as fast. */
	public static boolean losesTrail(Mob mob, @Nullable LivingEntity target) {
		return target instanceof Player p && mob instanceof Enemy && has(p, "greenway");
	}

	// ---------------------------------------------------------------- every tick

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (server.getTickCount() % 4 == 0) {
			ferry(server);
		}
		if (!WALL_RUN.isEmpty()) {
			WALL_RUN.entrySet().removeIf(e -> {
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p == null || now > e.getValue()) {
					return true;
				}
				p.resetFallDistance();
				return false;
			});
		}
		if (server.getTickCount() % 100 == 0) {
			// The goal stays with a loaded animal (it idles once the day is over); only the removed are let go.
			FOLLOWERS.keySet().removeIf(m -> {
				leaderGone(m);
				return m.isRemoved();
			});
		}
	}

	private static void leaderGone(Mob mob) {
		Follow f = mob.getAttached(FOLLOWS);
		if (f != null && mob.level().getGameTime() > f.until()) {
			mob.removeAttached(FOLLOWS);
		}
	}

	/** Whether a Cat's Way run of this player's is forgiven now (tests). */
	public static boolean wallRunning(ServerPlayer player) {
		Long until = WALL_RUN.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until;
	}
}
