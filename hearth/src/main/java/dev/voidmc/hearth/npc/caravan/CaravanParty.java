package dev.voidmc.hearth.npc.caravan;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.npc.NpcConfig;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkEntities;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.person.Genome;
import dev.voidmc.hearth.npc.person.NameBank;
import dev.voidmc.hearth.npc.person.Office;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A caravan met on the road (docs/LIVING_WORLD.md §7.6): guards at the front and the rear, the traders, and the pack
 * animals in a line between them — mules and donkeys with their chests, llamas in the cold lands, camels in the dry —
 * walking toward the town they are bound for at the caravan's pace. The guards fight monsters (and a player who attacks
 * the caravan); the traders speak to passers-by about where they are going and what they carry.
 *
 * <p>None of it is saved: the people are {@code hearth:folk} (never saved) driven by this party rather than a resident's
 * brain, and the animals carry the {@link Caravans#TAG} tag, which keeps them out of chunk saves. When no player is near
 * any more the party is folded away and the caravan goes on as a journey in time.
 */
public final class CaravanParty implements Folk.Controller {
	/** Bits a unit of cargo is reckoned at, for the fine of a robbery. */
	private static final long BITS_PER_UNIT = 4;
	private static final double SPACING = 3.0;

	final Journey journey;
	private final List<Folk> people = new ArrayList<>();
	private final List<Mob> animals = new ArrayList<>();
	private final Set<UUID> reported = new HashSet<>();
	private @Nullable UUID hostile;
	private long nextLead;
	private @Nullable Vec3 lastLeaderPos;
	private int stuck;
	private boolean robbed;

	private CaravanParty(Journey journey) {
		this.journey = journey;
	}

	/**
	 * Puts the caravan on the road at {@code at}. Returns null if there is nowhere to stand (open water, unloaded ground).
	 */
	static @Nullable CaravanParty spawn(ServerLevel level, Journey journey, BlockPos at, Culture culture, NpcConfig.Caravans cfg) {
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
		BlockPos stand = FolkSpots.standableNear(level, ground, 4);
		if (stand == null || !level.isPositionEntityTicking(stand)) {
			return null;
		}
		CaravanParty party = new CaravanParty(journey);
		RandomSource r = RandomSource.create(journey.id());
		int guards = cfg.guards();
		int traders = cfg.traders();
		int animals = Math.min(cfg.animals(), Math.max(1, journey.units() / 48));
		// front guard, traders, animals, rear guard
		if (guards > 0) {
			party.addPerson(level, stand, culture, JobType.GUARD, r);
		}
		for (int i = 0; i < traders; i++) {
			party.addPerson(level, stand, culture, JobType.TRADER, r);
		}
		for (int i = 0; i < animals; i++) {
			party.addAnimal(level, stand, culture, r);
		}
		for (int i = 1; i < guards; i++) {
			party.addPerson(level, stand, culture, JobType.GUARD, r);
		}
		if (party.people.isEmpty()) {
			party.fold();
			return null;
		}
		return party;
	}

	private void addPerson(ServerLevel level, BlockPos at, Culture culture, JobType job, RandomSource r) {
		Folk folk = FolkEntities.FOLK.create(level, EntitySpawnReason.EVENT);
		if (folk == null) {
			return;
		}
		boolean female = job == JobType.TRADER && r.nextInt(3) == 0;
		NameBank.Names names = NameBank.of(culture);
		String name = names.given(female, r) + " " + names.family(r);
		folk.setIdentity(new UUID(r.nextLong(), r.nextLong()), name, Genome.random(culture, r), culture, job, AgeStage.ADULT, female,
			job == JobType.GUARD ? Station.OFFICIAL : Station.MERCHANT, Office.NONE);
		folk.setController(this);
		BlockPos p = FolkSpots.standableNear(level, at.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2), 3);
		BlockPos spot = p != null ? p : at;
		folk.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, r.nextFloat() * 360.0F, 0.0F);
		if (job == JobType.GUARD) {
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			folk.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		} else {
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
		}
		if (level.addFreshEntity(folk)) {
			this.people.add(folk);
		}
	}

	private void addAnimal(ServerLevel level, BlockPos at, Culture culture, RandomSource r) {
		EntityType<? extends Mob> type = switch (culture) {
			case DESERT, MESA, STEPPE -> EntityTypes.CAMEL;
			case ALPINE, TUNDRA, NORTHERN -> EntityTypes.LLAMA;
			default -> r.nextInt(3) == 0 ? EntityTypes.DONKEY : EntityTypes.MULE;
		};
		Mob animal = type.create(level, EntitySpawnReason.EVENT);
		if (animal == null) {
			return;
		}
		animal.addTag(Caravans.TAG);
		if (animal instanceof AbstractChestedHorse chested) {
			chested.setChest(true);
		}
		if (type == EntityTypes.LLAMA) {
			// the caravan's colours on the llamas' backs
			animal.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.CARPET.pick(DyeColor.values()[r.nextInt(DyeColor.values().length)])));
		}
		BlockPos p = FolkSpots.standableNear(level, at.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2), 3);
		BlockPos spot = p != null ? p : at;
		animal.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, r.nextFloat() * 360.0F, 0.0F);
		if (level.addFreshEntity(animal)) {
			this.animals.add(animal);
		}
	}

	// ---------------------------------------------------------------- the road

	/** One tick of the whole party: the leader makes for the caravan's place on the road, the rest follow in a line. */
	void tick(ServerLevel level, double progress) {
		this.people.removeIf(f -> !f.isAlive() || f.isRemoved());
		this.animals.removeIf(a -> !a.isAlive() || a.isRemoved());
		if (this.people.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		List<Mob> line = new ArrayList<>(this.people.size() + this.animals.size());
		line.add(this.people.get(0));
		for (int i = 1; i < this.people.size(); i++) {
			Folk f = this.people.get(i);
			if (f.job() == JobType.TRADER) {
				line.add(f);
			}
		}
		line.addAll(this.animals);
		for (int i = 1; i < this.people.size(); i++) {
			Folk f = this.people.get(i);
			if (f.job() != JobType.TRADER) {
				line.add(f);
			}
		}
		// Guards first: anything that threatens the caravan.
		boolean fighting = false;
		for (Folk f : this.people) {
			if (f.job() == JobType.GUARD && fight(level, f)) {
				fighting = true;
			}
		}
		Mob leader = line.get(0);
		if (!fighting && now >= this.nextLead) {
			this.nextLead = now + 20;
			double len = Math.max(1.0, this.journey.length());
			BlockPos target = this.journey.at(progress + 10.0 / len);
			if (level.isLoaded(target)) {
				BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target);
				if (leader.position().distanceToSqr(Vec3.atBottomCenterOf(ground)) > 4.0) {
					leader.getNavigation().moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, 0.5);
				}
				// Stuck on the way (a ravine, a river) and nobody close enough to see: on to where the road goes on.
				Vec3 pos = leader.position();
				if (this.lastLeaderPos != null && pos.distanceToSqr(this.lastLeaderPos) < 0.5) {
					this.stuck += 20;
				} else {
					this.stuck = 0;
				}
				this.lastLeaderPos = pos;
				if (this.stuck > 200 && nearestPlayer(level, leader) > 32) {
					BlockPos spot = FolkSpots.standableNear(level, ground, 3);
					if (spot != null) {
						for (Mob m : line) {
							m.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
						}
						this.stuck = 0;
					}
				}
			}
		}
		// The rest walk in a line behind the one ahead.
		if ((now & 7) == 0) {
			for (int i = 1; i < line.size(); i++) {
				Mob m = line.get(i);
				Mob ahead = line.get(i - 1);
				if (m instanceof Folk f && f.job() == JobType.GUARD && fighting) {
					continue;
				}
				double d2 = m.distanceToSqr(ahead);
				if (d2 > 24 * 24 && nearestPlayer(level, m) > 16) {
					m.teleportTo(ahead.getX(), ahead.getY(), ahead.getZ());
				} else if (d2 > (SPACING + 0.5) * (SPACING + 0.5)) {
					m.getNavigation().moveTo(ahead, m instanceof Folk ? 0.55 : 1.1);
				} else {
					m.getNavigation().stop();
				}
			}
		}
	}

	/** A guard deals with what threatens the caravan: monsters near it, or the player who attacked it. */
	private boolean fight(ServerLevel level, Folk guard) {
		LivingEntity target = guard.getTarget();
		if (target == null || !target.isAlive() || target.distanceToSqr(guard) > 20 * 20) {
			target = null;
			if (this.hostile != null) {
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(this.hostile);
				if (p != null && p.level() == level && p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceToSqr(guard) < 16 * 16) {
					target = p;
				}
			}
			if (target == null) {
				List<Monster> near = level.getEntitiesOfClass(Monster.class, guard.getBoundingBox().inflate(12, 4, 12),
					m -> m.isAlive() && m instanceof Enemy && guard.getSensing().hasLineOfSight(m));
				double best = Double.MAX_VALUE;
				for (Monster m : near) {
					double d = m.distanceToSqr(guard);
					if (d < best) {
						best = d;
						target = m;
					}
				}
			}
			guard.setTarget(target);
		}
		if (target == null) {
			return false;
		}
		guard.getLookControl().setLookAt(target, 30.0F, 30.0F);
		double reach = guard.getBbWidth() * 2.0F * guard.getBbWidth() * 2.0F + target.getBbWidth() + 1.5;
		if (guard.distanceToSqr(target) <= reach) {
			guard.getNavigation().stop();
			if (guard.tickCount % 20 == 0) {
				guard.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				guard.doHurtTarget(level, target);
			}
		} else if (guard.tickCount % 10 == 0) {
			guard.getNavigation().moveTo(target, 0.85);
		}
		return true;
	}

	private static double nearestPlayer(ServerLevel level, Mob m) {
		var p = level.getNearestPlayer(m, -1.0);
		return p == null ? Double.MAX_VALUE : p.distanceTo(m);
	}

	/** Distance from the party to the nearest player (spectators too), for folding. */
	double nearestPlayer(ServerLevel level) {
		double best = Double.MAX_VALUE;
		for (Folk f : this.people) {
			for (ServerPlayer p : level.players()) {
				best = Math.min(best, p.distanceTo(f));
			}
		}
		return best;
	}

	boolean alive() {
		for (Folk f : this.people) {
			if (f.isAlive() && !f.isRemoved()) {
				return true;
			}
		}
		return false;
	}

	/** Whether a player overcame the caravan (everyone of it dead by their hand). */
	/** Who overcame the caravan (the last player who turned on it), or null. */
	@org.jspecify.annotations.Nullable UUID robber() {
		return this.hostile;
	}

	boolean robbed() {
		return this.robbed;
	}

	/** Takes everyone off the road (no player near, the caravan arrived, the server stopping). */
	void fold() {
		for (Folk f : this.people) {
			if (!f.isRemoved()) {
				f.markFolded();
				f.setController(null);
				f.discard();
			}
		}
		for (Mob a : this.animals) {
			if (!a.isRemoved()) {
				a.discard();
			}
		}
		this.people.clear();
		this.animals.clear();
	}

	int size() {
		return this.people.size() + this.animals.size();
	}

	List<Folk> people() {
		return this.people;
	}

	// ---------------------------------------------------------------- Folk.Controller

	@Override
	public void tick(ServerLevel level, Folk folk) {
		// the party is ticked as a whole by the caravan manager
	}

	@Override
	public void interact(ServerPlayer player, Folk folk) {
		folk.getNavigation().stop();
		folk.getLookControl().setLookAt(player, 30.0F, 30.0F);
		folk.setFolkPose(FolkPose.TALK);
		Component line;
		if (this.hostile != null && this.hostile.equals(player.getUUID())) {
			line = Component.translatable("hearth.npc.caravan.hostile");
		} else if (folk.job() == JobType.GUARD) {
			line = Component.translatable("hearth.npc.caravan.guard", this.journey.toName());
		} else {
			line = Component.translatable("hearth.npc.caravan.trader", this.journey.toName(), cargoWords());
		}
		player.sendSystemMessage(Component.translatable("hearth.npc.say", Component.literal(folk.folkName()).withStyle(ChatFormatting.GOLD), line));
	}

	/** "timber and salt": the two largest kinds of cargo, named by the economy's goods names. */
	private Component cargoWords() {
		int a = -1;
		int b = -1;
		int[] c = this.journey.cargo();
		for (int i = 0; i < c.length && i < Goods.VALUES.length; i++) {
			if (c[i] <= 0) {
				continue;
			}
			if (a < 0 || c[i] > c[a]) {
				b = a;
				a = i;
			} else if (b < 0 || c[i] > c[b]) {
				b = i;
			}
		}
		if (a < 0) {
			return Component.translatable("hearth.npc.caravan.cargo.none");
		}
		Component first = Component.translatable(Goods.VALUES[a].translationKey());
		return b < 0 ? first : Component.translatable("hearth.npc.caravan.cargo.two", first, Component.translatable(Goods.VALUES[b].translationKey()));
	}

	@Override
	public void hurt(ServerLevel level, Folk folk, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player && !player.isCreative()) {
			this.hostile = player.getUUID();
			report(level, player, LawService.Offence.ASSAULT, folk.blockPosition(), 0);
		}
	}

	@Override
	public void died(ServerLevel level, Folk folk, DamageSource source) {
		folk.markFolded();
		if (source.getEntity() instanceof ServerPlayer player && !player.isCreative()) {
			report(level, player, LawService.Offence.MURDER, folk.blockPosition(), 0);
			boolean anyoneLeft = false;
			for (Folk f : this.people) {
				if (f != folk && f.isAlive() && !f.isRemoved()) {
					anyoneLeft = true;
				}
			}
			if (!anyoneLeft) {
				this.robbed = true;
				report(level, player, LawService.Offence.ROBBERY, folk.blockPosition(), this.journey.units() * BITS_PER_UNIT);
			}
		}
	}

	/** A pack animal was killed: by a player, that is robbery of the caravan. */
	void animalKilled(ServerLevel level, Mob animal, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player && !player.isCreative()) {
			this.hostile = player.getUUID();
			report(level, player, LawService.Offence.ROBBERY, animal.blockPosition(), this.journey.units() * BITS_PER_UNIT / Math.max(1, this.animals.size()));
			// what it carried is the home town's, stolen: its merchants will know it (the fence will not mind)
			LawService law = HearthServices.law();
			if (law != null) {
				LawService.Owner owner = new LawService.Owner(this.journey.fromId(), null, null, LawService.Owner.Kind.SETTLEMENT);
				for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					animal.getBoundingBox().inflate(2.5), e -> e.tickCount < 5)) {
					net.minecraft.world.item.ItemStack stack = item.getItem().copy();
					law.markStolen(stack, owner, player.getUUID());
					item.setItem(stack);
				}
			}
		}
	}

	@Override
	public void removed(ServerLevel level, Folk folk) {
		folk.markFolded();
	}

	/**
	 * Tells the law (or, without a law module, the standing of the caravan's home town) what a player did to the caravan.
	 * Each kind of crime is reported once per player per encounter.
	 */
	private void report(ServerLevel level, ServerPlayer player, LawService.Offence offence, BlockPos pos, long value) {
		if (!this.reported.add(new UUID(player.getUUID().getMostSignificantBits(), player.getUUID().getLeastSignificantBits() ^ offence.ordinal()))) {
			return;
		}
		LawService law = HearthServices.law();
		if (law != null) {
			law.report(new LawService.Crime(offence, player.getUUID(), this.journey.fromId(), pos, null, value, true, level.getGameTime()));
			return;
		}
		StandingService standing = HearthServices.standing();
		if (standing != null) {
			int weight = switch (offence) {
				case MURDER -> -100;
				case ROBBERY -> -40;
				default -> -10;
			};
			standing.deed(new StandingService.Deed(player.getUUID(), this.journey.fromId(), "npc:caravan_" + offence.getSerializedName(), weight, null, pos, true));
		}
	}
}
