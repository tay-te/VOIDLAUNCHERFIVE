package dev.voidmc.hearth.npc.travel;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.npc.caravan.Caravans;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkEntities;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.person.Genome;
import dev.voidmc.hearth.npc.person.NameBank;
import dev.voidmc.hearth.npc.person.Office;
import dev.voidmc.hearth.npc.travel.cart.CartEntity;
import dev.voidmc.hearth.npc.travel.cart.Carts;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Making the people, beasts and carts of the roads (docs/LIVING_WORLD_2.md §1.3), and the few things they all do: a
 * stranger or a resident away from home as a {@code hearth:folk} driven by its party, a draught animal that is never
 * saved and never herded ({@link Caravans#TAG}), a cart hitched to it, and a guard's or a bandit's fight.
 */
public final class Wayfarers {
	private Wayfarers() {
	}

	/** Someone nobody knows: a name from their culture, a face of their culture, the trade asked for. */
	static @Nullable Folk stranger(ServerLevel level, BlockPos at, Culture culture, JobType job, RandomSource r, Folk.Controller controller) {
		Folk folk = FolkEntities.FOLK.create(level, EntitySpawnReason.EVENT);
		if (folk == null) {
			return null;
		}
		boolean female = (job == JobType.PILGRIM || job == JobType.PEDLAR || job == JobType.NONE) && r.nextInt(3) == 0;
		NameBank.Names names = NameBank.of(culture);
		String name = names.given(female, r) + " " + names.family(r);
		folk.setIdentity(new UUID(r.nextLong(), r.nextLong()), name, Genome.random(culture, r), culture, job, AgeStage.ADULT, female, Station.of(job),
			Office.NONE);
		return place(level, folk, at, r, controller);
	}

	/** A resident on the road, as they look at home (their name, face, trade), driven by the party. */
	static @Nullable Folk resident(ServerLevel level, BlockPos at, Culture culture, Resident res, RandomSource r, Folk.Controller controller) {
		Folk folk = FolkEntities.FOLK.create(level, EntitySpawnReason.EVENT);
		if (folk == null) {
			return null;
		}
		// (a fresh entity id for the body: the resident's own is for the embodiment manager, which leaves the away alone)
		folk.setIdentity(new UUID(res.id.getMostSignificantBits() ^ 0x7EA7E1L, res.id.getLeastSignificantBits()), res.fullName(), res.appearance, culture,
			res.job, res.age, res.female, Station.of(res.job), Office.NONE);
		return place(level, folk, at, r, controller);
	}

	private static @Nullable Folk place(ServerLevel level, Folk folk, BlockPos at, RandomSource r, Folk.Controller controller) {
		folk.setController(controller);
		BlockPos p = FolkSpots.standableNear(level, at.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2), 3);
		BlockPos spot = p != null ? p : at;
		folk.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, r.nextFloat() * 360.0F, 0.0F);
		return level.addFreshEntity(folk) ? folk : null;
	}

	/** Arms a folk for their part: guards a sword and shield, bandits a blade (an axe for some), pilgrims a staff. */
	static void arm(Folk folk, JobType job, RandomSource r) {
		switch (job) {
			case GUARD -> {
				folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
				folk.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			}
			case BANDIT -> {
				folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(r.nextInt(3) == 0 ? Items.IRON_AXE : r.nextInt(2) == 0 ? Items.STONE_SWORD : Items.IRON_SWORD));
				folk.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
				folk.setDropChance(EquipmentSlot.MAINHAND, 0.15F);
				folk.setDropChance(EquipmentSlot.CHEST, 0.1F);
			}
			case PILGRIM, NONE, MONK, HERMIT -> folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
			default -> {
			}
		}
	}

	/** A draught or pack animal: a horse, a mule, a donkey or an ox (a cow under the yoke), never saved. */
	static @Nullable Mob beast(ServerLevel level, BlockPos at, EntityType<? extends Mob> type, RandomSource r) {
		Mob animal = type.create(level, EntitySpawnReason.EVENT);
		if (animal == null) {
			return null;
		}
		animal.addTag(Caravans.TAG);
		animal.setPersistenceRequired();
		BlockPos p = FolkSpots.standableNear(level, at.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2), 3);
		BlockPos spot = p != null ? p : at;
		animal.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, r.nextFloat() * 360.0F, 0.0F);
		return level.addFreshEntity(animal) ? animal : null;
	}

	/** What draws a cart in a culture: two horses or oxen for a wagon, a mule (an ox in the north, a camel in the dry lands) for a cart. */
	static EntityType<? extends Mob> draught(Culture culture, CartEntity.Kind kind, RandomSource r) {
		return switch (culture) {
			case DESERT, MESA, STEPPE -> EntityTypes.CAMEL;
			case NORTHERN, TUNDRA, ALPINE -> kind == CartEntity.Kind.WAGON ? EntityTypes.HORSE : EntityTypes.COW;
			default -> kind == CartEntity.Kind.WAGON ? (r.nextInt(3) == 0 ? EntityTypes.COW : EntityTypes.HORSE) : r.nextInt(3) == 0 ? EntityTypes.DONKEY
				: EntityTypes.MULE;
		};
	}

	/** A cart hitched to {@code puller}, behind it. */
	static @Nullable CartEntity cart(ServerLevel level, net.minecraft.world.entity.Entity puller, CartEntity.Kind kind, CartEntity.Load load) {
		CartEntity cart = Carts.CART.create(level, EntitySpawnReason.EVENT);
		if (cart == null) {
			return null;
		}
		cart.setup(kind, load);
		float yaw = puller.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
		cart.snapTo(puller.getX() + Math.sin(yaw) * kind.hitch, puller.getY(), puller.getZ() - Math.cos(yaw) * kind.hitch, puller.getYRot(), 0F);
		cart.hitchTo(puller);
		return level.addFreshEntity(cart) ? cart : null;
	}

	/** A fighter deals with its target: closes in and strikes (once a second), or picks the nearest threat in sight. */
	static boolean fight(ServerLevel level, Mob fighter, @Nullable LivingEntity target) {
		if (target == null || !target.isAlive() || target.distanceToSqr(fighter) > 24 * 24) {
			return false;
		}
		fighter.getLookControl().setLookAt(target, 30.0F, 30.0F);
		double reach = fighter.getBbWidth() * 2.0F * fighter.getBbWidth() * 2.0F + target.getBbWidth() + 1.5;
		if (fighter.distanceToSqr(target) <= reach) {
			fighter.getNavigation().stop();
			if (fighter.tickCount % 20 == 0) {
				fighter.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				fighter.doHurtTarget(level, target);
			}
		} else if (fighter.tickCount % 10 == 0) {
			fighter.getNavigation().moveTo(target, 0.85);
		}
		return true;
	}

	/** The nearest monster a guard sees within 12 blocks, or null. */
	static @Nullable Monster monster(ServerLevel level, Mob guard) {
		Monster best = null;
		double bestD = Double.MAX_VALUE;
		for (Monster m : level.getEntitiesOfClass(Monster.class, guard.getBoundingBox().inflate(12, 4, 12),
			m -> m.isAlive() && m instanceof Enemy && guard.getSensing().hasLineOfSight(m))) {
			double d = m.distanceToSqr(guard);
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}

	/** A player who may be fought (not creative, not a spectator, alive, here). */
	static boolean fightable(@Nullable ServerPlayer p, ServerLevel level) {
		return p != null && p.level() == level && p.isAlive() && !p.isCreative() && !p.isSpectator();
	}

	/** The distance from an entity to the nearest player (spectators too: someone watching sees the road). */
	static double nearestPlayer(ServerLevel level, double x, double z) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer p : level.players()) {
			best = Math.min(best, Math.hypot(p.getX() - x, p.getZ() - z));
		}
		return best;
	}

	/**
	 * The ground at a column if its chunk is loaded, else null: the roads never load (let alone generate) a chunk to
	 * find where to stand.
	 */
	static @Nullable BlockPos ground(ServerLevel level, BlockPos at) {
		if (!level.isLoaded(at)) {
			return null;
		}
		return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
	}

	/** Takes a folk off the road (no player near, the journey over, the server stopping). */
	static void fold(Folk f) {
		if (!f.isRemoved()) {
			f.markFolded();
			f.setController(null);
			f.discard();
		}
	}
}
