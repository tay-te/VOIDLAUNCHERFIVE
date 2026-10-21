package dev.voidmc.hearth.npc.travel;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.api.TravelService.TravelKind;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.travel.cart.CartEntity;
import dev.voidmc.hearth.sim.roads.Journey;
import dev.voidmc.hearth.sim.roads.Traffic;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A journey on the road near a player (docs/LIVING_WORLD_2.md §1.3): its people, beasts and cart where the books say
 * the journey is, walking on along its way — a guard in front, the master or the riders, the walkers, the beasts and the
 * cart, a guard behind — stopping at dusk to make camp by the road (a fire, bedrolls) and going on at dawn. It turns to
 * face a player who comes near; right-clicked, its people talk ({@link TravellerTalk}). Guards fight monsters and anyone
 * who attacks the party, and the law hears of what a player does to it. Nothing of it is saved: folded, it is a journey
 * in the books again. Stuck where nobody sees, it is moved on.
 */
public final class TravelParty implements Folk.Controller {
	private static final double SPACING = 2.6;
	private static final int LOOKAHEAD = 10;

	/** A member and their part. */
	record Member(Folk folk, JobType role, @Nullable UUID resident) {
	}

	final long journeyId;
	final TravelKind kind;
	final long from;
	final long to;
	final Culture culture;
	final String fromName;
	final String toName;
	final List<Member> members = new ArrayList<>();
	final List<Mob> beasts = new ArrayList<>();
	@Nullable CartEntity cart;
	@Nullable Mob mount;
	@Nullable Camp camp;
	private final Set<UUID> reported = new HashSet<>();
	@Nullable UUID hostile;
	private long nextLead;
	private @Nullable Vec3 lastLead;
	private int stuck;
	boolean robbedByPlayer;

	private TravelParty(Journey j, Culture culture, String fromName, String toName) {
		this.journeyId = j.id();
		this.kind = j.kind();
		this.from = j.from();
		this.to = j.to();
		this.culture = culture;
		this.fromName = fromName;
		this.toName = toName;
	}

	/** Puts a journey's party on the road at {@code at}; null if there is nowhere to stand. */
	static @Nullable TravelParty spawn(ServerLevel level, Traffic traffic, Journey j, BlockPos at) {
		BlockPos ground = Wayfarers.ground(level, at);
		BlockPos stand = ground == null ? null : FolkSpots.standableNear(level, ground, 4);
		if (stand == null || !level.isPositionEntityTicking(stand)) {
			return null;
		}
		SimSettlement home = traffic.world().settlement(j.kind() == TravelKind.SETTLERS ? j.to() : j.from());
		SimSettlement other = traffic.world().settlement(j.kind() == TravelKind.SETTLERS ? j.from() : j.to());
		Culture culture = home != null ? home.culture() : Culture.TEMPERATE;
		TravelParty p = new TravelParty(j, culture, home == null ? "" : home.name(), other == null ? "" : other.name());
		RandomSource r = RandomSource.create(j.id() * 0x9E3779B97F4A7C15L);
		List<Resident> people = new ArrayList<>();
		if (home != null) {
			for (UUID id : j.residents()) {
				Resident res = home.resident(id);
				// one already walking about at home is not on the road twice
				if (res != null && !home.isResidentEmbodied(id) && people.size() < 6) {
					people.add(res);
				}
			}
		}
		switch (j.kind()) {
			case CARAVAN -> {
				p.add(level, stand, JobType.GUARD, null, r);
				p.add(level, stand, JobType.TRADER, null, r);
				p.draught(level, stand, CartEntity.Kind.WAGON, cargoLoad(j), 2, r);
				p.add(level, stand, JobType.GUARD, null, r);
			}
			case CARTER, MARKET_GOERS, SETTLERS -> {
				for (Resident res : people) {
					p.add(level, stand, res.job, res, r);
				}
				if (p.members.isEmpty()) {
					p.add(level, stand, j.kind() == TravelKind.CARTER ? JobType.CARTER : JobType.NONE, null, r);
				}
				p.draught(level, stand, CartEntity.Kind.CART, j.kind() == TravelKind.SETTLERS ? CartEntity.Load.BEDROLLS
					: j.kind() == TravelKind.MARKET_GOERS ? CartEntity.Load.PRODUCE : CartEntity.Load.SACKS, 1, r);
			}
			case PEDLAR -> {
				Folk f = p.add(level, stand, JobType.PEDLAR, null, r);
				if (f != null) {
					p.cart = Wayfarers.cart(level, f, CartEntity.Kind.HANDCART, CartEntity.Load.CRATES);
				}
			}
			case MESSENGER, DIGNITARY -> {
				Folk f = people.isEmpty() ? p.add(level, stand, JobType.MESSENGER, null, r) : p.add(level, stand, people.getFirst().job, people.getFirst(), r);
				Mob horse = Wayfarers.beast(level, stand, net.minecraft.world.entity.EntityTypes.HORSE, r);
				if (horse != null) {
					p.mount = horse;
					if (horse instanceof AbstractHorse h) {
						h.setTamed(true);
					}
					if (f != null) {
						f.startRiding(horse, true, false);
					}
				}
				if (j.kind() == TravelKind.DIGNITARY) {
					p.add(level, stand, JobType.GUARD, null, r);
					p.add(level, stand, JobType.GUARD, null, r);
				}
			}
			case TAX_COLLECTOR -> {
				p.add(level, stand, JobType.GUARD, null, r);
				p.add(level, stand, JobType.BAILIFF, null, r);
				p.add(level, stand, JobType.GUARD, null, r);
				p.draught(level, stand, CartEntity.Kind.CART, CartEntity.Load.CRATES, 1, r);
			}
			default -> {
				// pilgrims, patrols, wedding guests, a troupe: the residents themselves, or strangers of the kind
				for (Resident res : people) {
					p.add(level, stand, j.kind() == TravelKind.PATROL ? JobType.GUARD : res.job, res, r);
				}
				if (p.members.isEmpty()) {
					JobType job = j.kind() == TravelKind.PILGRIMS ? JobType.PILGRIM : j.kind() == TravelKind.PATROL ? JobType.GUARD : JobType.NONE;
					p.add(level, stand, job, null, r);
					p.add(level, stand, job, null, r);
				}
			}
		}
		if (p.members.isEmpty()) {
			p.fold(level);
			return null;
		}
		return p;
	}

	private static CartEntity.Load cargoLoad(Journey j) {
		int[] cargo = j.details().getIntArray("cargo").orElse(new int[0]);
		int best = -1;
		for (int g = 0; g < cargo.length; g++) {
			if (best < 0 || cargo[g] > cargo[best]) {
				best = g;
			}
		}
		if (best < 0) {
			return CartEntity.Load.CRATES;
		}
		return switch (dev.voidmc.hearth.api.Goods.VALUES[best]) {
			case LOGS, WOOD -> CartEntity.Load.LOGS;
			case STONE, DRESSED_STONE, BRICK, ORE -> CartEntity.Load.STONE;
			case FOOD, GRAIN, FLOUR, SALT, WOOL -> CartEntity.Load.SACKS;
			case PRODUCE, FISH, MEAT -> CartEntity.Load.PRODUCE;
			case FUEL, WAX -> CartEntity.Load.BARRELS;
			default -> CartEntity.Load.CRATES;
		};
	}

	private @Nullable Folk add(ServerLevel level, BlockPos at, JobType role, @Nullable Resident res, RandomSource r) {
		Folk f = res != null ? Wayfarers.resident(level, at, this.culture, res, r, this) : Wayfarers.stranger(level, at, this.culture, role, r, this);
		if (f == null) {
			return null;
		}
		Wayfarers.arm(f, this.kind == TravelKind.PILGRIMS ? JobType.PILGRIM : role, r);
		this.members.add(new Member(f, role, res == null ? null : res.id));
		return f;
	}

	private void draught(ServerLevel level, BlockPos at, CartEntity.Kind kind, CartEntity.Load load, int count, RandomSource r) {
		Mob first = null;
		var type = Wayfarers.draught(this.culture, kind, r);
		for (int i = 0; i < count; i++) {
			Mob m = Wayfarers.beast(level, at, type, r);
			if (m == null) {
				continue;
			}
			if (m instanceof AbstractChestedHorse chested && kind != CartEntity.Kind.WAGON) {
				chested.setChest(true);
			}
			this.beasts.add(m);
			if (first == null) {
				first = m;
			}
		}
		if (first != null) {
			this.cart = Wayfarers.cart(level, first, kind, load);
		}
	}

	int size() {
		return this.members.size() + this.beasts.size() + (this.cart != null ? 1 : 0) + (this.mount != null ? 1 : 0);
	}

	boolean alive() {
		for (Member m : this.members) {
			if (m.folk.isAlive() && !m.folk.isRemoved()) {
				return true;
			}
		}
		return false;
	}

	@Nullable Folk lead() {
		for (Member m : this.members) {
			if (m.folk.isAlive() && !m.folk.isRemoved()) {
				return m.folk;
			}
		}
		return null;
	}

	/** Where the party is: its leader, or nowhere. */
	@Nullable Vec3 position() {
		Folk f = lead();
		return f == null ? null : f.position();
	}

	// ---------------------------------------------------------------- the road

	/** One tick of the whole party. */
	void tick(ServerLevel level, Traffic traffic, Journey j, long now) {
		this.members.removeIf(m -> !m.folk.isAlive() || m.folk.isRemoved());
		this.beasts.removeIf(b -> !b.isAlive() || b.isRemoved());
		if (this.members.isEmpty()) {
			return;
		}
		boolean fighting = false;
		for (Member m : this.members) {
			if (m.role == JobType.GUARD || m.role == JobType.BANDIT) {
				LivingEntity target = m.folk.getTarget();
				if (target == null || !target.isAlive()) {
					ServerPlayer p = this.hostile == null ? null : level.getServer().getPlayerList().getPlayer(this.hostile);
					target = Wayfarers.fightable(p, level) && p.distanceToSqr(m.folk) < 16 * 16 ? p : bandit(level, m.folk);
					if (target == null) {
						target = Wayfarers.monster(level, m.folk);
					}
					m.folk.setTarget(target);
				}
				fighting |= Wayfarers.fight(level, m.folk, target);
			}
		}
		if (j.resting(now)) {
			rest(level, traffic, j, now);
			return;
		}
		if (this.camp != null) {
			this.camp.strike(level);
			this.camp = null;
			for (Member m : this.members) {
				m.folk.setFolkPose(FolkPose.NONE);
			}
		}
		Folk lead = lead();
		Mob leader = this.mount != null && this.mount.isAlive() ? this.mount : lead;
		if (leader == null) {
			return;
		}
		if (!fighting && now >= this.nextLead) {
			this.nextLead = now + 20;
			double[] p = traffic.place(j, now);
			Traffic.Geometry g = traffic.geometry(j);
			double f = j.length() <= 0 ? 0 : j.along(now) / j.length();
			double ahead = j.returning(now) ? -LOOKAHEAD : LOOKAHEAD;
			double[] t = g.at(Mth.clamp(f * g.length() + ahead, 0, g.length()));
			BlockPos target = BlockPos.containing(t[0], p[1], t[2]);
			if (level.isLoaded(target)) {
				BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target);
				double speed = this.mount != null ? 1.2 : this.kind == TravelKind.MESSENGER ? 0.7 : 0.5;
				if (leader.position().distanceToSqr(Vec3.atBottomCenterOf(ground)) > 4.0) {
					leader.getNavigation().moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, speed);
				}
				Vec3 pos = leader.position();
				this.stuck = this.lastLead != null && pos.distanceToSqr(this.lastLead) < 0.5 ? this.stuck + 20 : 0;
				this.lastLead = pos;
				double behind = Math.hypot(pos.x - p[0], pos.z - p[2]);
				if ((this.stuck > 200 || behind > 40) && Wayfarers.nearestPlayer(level, pos.x, pos.z) > 32) {
					// stuck (a ravine, a river) or left behind, and nobody close enough to see: on to where the books have it
					BlockPos top = Wayfarers.ground(level, BlockPos.containing(p[0], p[1], p[2]));
					BlockPos at = top == null || !level.isPositionEntityTicking(top) ? null : FolkSpots.standableNear(level, top, 4);
					if (at != null) {
						for (Mob m : line()) {
							m.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
						}
						if (this.cart != null) {
							this.cart.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
						}
						this.stuck = 0;
					}
				}
			}
		}
		if ((now & 7) == 0) {
			List<Mob> line = line();
			for (int i = 1; i < line.size(); i++) {
				Mob m = line.get(i);
				Mob ahead = line.get(i - 1);
				if (m instanceof Folk f && fighting && (roleOf(f) == JobType.GUARD)) {
					continue;
				}
				double d2 = m.distanceToSqr(ahead);
				if (d2 > 24 * 24 && Wayfarers.nearestPlayer(level, m.getX(), m.getZ()) > 16) {
					m.teleportTo(ahead.getX(), ahead.getY(), ahead.getZ());
				} else if (d2 > (SPACING + 0.5) * (SPACING + 0.5)) {
					m.getNavigation().moveTo(ahead, m instanceof Folk ? 0.55 : 1.1);
				} else {
					m.getNavigation().stop();
				}
			}
			// a player close by: the nearest of them turns to look
			ServerPlayer near = nearestPlayer(level, 6);
			if (near != null && !fighting) {
				Folk f = lead();
				if (f != null && f.getVehicle() == null) {
					f.getLookControl().setLookAt(near, 30F, 30F);
				}
			}
		}
	}

	/** The nearest bandit within 16 blocks (a robbery played out in front of a player), or null. */
	private static @Nullable Folk bandit(ServerLevel level, Folk guard) {
		Folk best = null;
		double bestD = 16 * 16;
		for (Folk f : level.getEntitiesOfClass(Folk.class, guard.getBoundingBox().inflate(16, 4, 16), f -> f.isAlive() && f.controller() instanceof BanditParty)) {
			double d = f.distanceToSqr(guard);
			if (d < bestD) {
				bestD = d;
				best = f;
			}
		}
		return best;
	}

	/** The marching order: the leader (or the messenger's horse), people, beasts, the rear guard last. */
	List<Mob> line() {
		List<Mob> out = new ArrayList<>();
		if (this.mount != null && this.mount.isAlive()) {
			out.add(this.mount);
		}
		Member rear = null;
		for (int i = 0; i < this.members.size(); i++) {
			Member m = this.members.get(i);
			if (m.folk.getVehicle() != null) {
				continue;
			}
			if (i == this.members.size() - 1 && m.role == JobType.GUARD && this.members.size() > 1) {
				rear = m;
				continue;
			}
			out.add(m.folk);
		}
		out.addAll(this.beasts);
		if (rear != null) {
			out.add(rear.folk);
		}
		return out;
	}

	private JobType roleOf(Folk f) {
		for (Member m : this.members) {
			if (m.folk == f) {
				return m.role;
			}
		}
		return JobType.NONE;
	}

	/** Night: they make camp by the road — a fire, bedrolls round it — and sit about it till dawn. */
	private void rest(ServerLevel level, Traffic traffic, Journey j, long now) {
		if (this.camp == null) {
			double[] p = traffic.place(j, now);
			// beside the road, square to it
			double n = Math.max(1e-6, Math.hypot(p[3], p[4]));
			BlockPos side = BlockPos.containing(p[0] - p[4] / n * 5, p[1], p[2] + p[3] / n * 5);
			BlockPos top = Wayfarers.ground(level, side);
			BlockPos ground = top == null ? null : Camp.ground(level, top);
			this.camp = new Camp(ground == null ? BlockPos.containing(p[0], p[1], p[2]) : ground);
			if (ground != null && this.kind != TravelKind.PATROL) {
				this.camp.fire(level);
				this.camp.bedrolls(level, Math.min(4, this.members.size()), this.journeyId);
				Parties.remember(level, this.camp);
			}
		}
		if ((now & 15) != 0) {
			return;
		}
		BlockPos c = this.camp.centre();
		int i = 0;
		for (Member m : this.members) {
			if (m.folk.getTarget() != null) {
				continue;
			}
			double a = i++ * (Math.PI * 2 / Math.max(3, this.members.size()));
			double tx = c.getX() + 0.5 + Math.cos(a) * 2.2;
			double tz = c.getZ() + 0.5 + Math.sin(a) * 2.2;
			if (m.folk.getVehicle() != null) {
				m.folk.stopRiding();
			}
			if (m.folk.position().distanceToSqr(tx, m.folk.getY(), tz) > 1.5) {
				m.folk.getNavigation().moveTo(tx, c.getY(), tz, 0.5);
				m.folk.setFolkPose(FolkPose.NONE);
			} else {
				m.folk.getNavigation().stop();
				m.folk.getLookControl().setLookAt(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5);
				m.folk.setFolkPose(FolkPose.SIT);
			}
		}
		for (Mob b : this.beasts) {
			if (b.distanceToSqr(Vec3.atCenterOf(c)) > 8 * 8) {
				b.getNavigation().moveTo(c.getX() + 3, c.getY(), c.getZ() + 3, 1.0);
			}
		}
	}

	private @Nullable ServerPlayer nearestPlayer(ServerLevel level, double within) {
		Folk f = lead();
		if (f == null) {
			return null;
		}
		ServerPlayer best = null;
		double bestD = within * within;
		for (ServerPlayer p : level.players()) {
			double d = p.distanceToSqr(f);
			if (d < bestD && !p.isSpectator()) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	/** Takes everyone off the road and strikes the camp. */
	void fold(ServerLevel level) {
		if (this.camp != null) {
			this.camp.strike(level);
			Parties.forget(level, this.camp);
			this.camp = null;
		}
		for (Member m : this.members) {
			Wayfarers.fold(m.folk);
		}
		for (Mob b : this.beasts) {
			if (!b.isRemoved()) {
				b.discard();
			}
		}
		if (this.mount != null && !this.mount.isRemoved()) {
			this.mount.discard();
		}
		if (this.cart != null && !this.cart.isRemoved()) {
			this.cart.discard();
		}
		this.members.clear();
		this.beasts.clear();
		this.cart = null;
		this.mount = null;
	}

	List<Folk> folk() {
		List<Folk> out = new ArrayList<>();
		for (Member m : this.members) {
			out.add(m.folk);
		}
		return out;
	}

	// ---------------------------------------------------------------- Folk.Controller

	@Override
	public void tick(ServerLevel level, Folk folk) {
		// the party is ticked as a whole by Parties
	}

	@Override
	public void interact(ServerPlayer player, Folk folk) {
		folk.getNavigation().stop();
		folk.getLookControl().setLookAt(player, 30.0F, 30.0F);
		folk.setFolkPose(FolkPose.TALK);
		if (this.hostile != null && this.hostile.equals(player.getUUID())) {
			player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("hearth.npc.say", folk.folkName(),
				net.minecraft.network.chat.Component.translatable("hearth.npc.caravan.hostile")));
			return;
		}
		Member m = null;
		for (Member x : this.members) {
			if (x.folk == folk) {
				m = x;
			}
		}
		dev.voidmc.hearth.npc.NpcModule.conversations().openStranger(player, folk, new TravellerTalk(this, folk, m == null ? JobType.NONE : m.role,
			m != null && m.resident != null));
	}

	@Override
	public void hurt(ServerLevel level, Folk folk, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player && !player.isCreative()) {
			this.hostile = player.getUUID();
			report(level, player, LawService.Offence.ASSAULT, folk.blockPosition(), 0, residentOf(folk));
		}
	}

	@Override
	public void died(ServerLevel level, Folk folk, DamageSource source) {
		folk.markFolded();
		UUID res = residentOf(folk);
		SimSettlement home = Parties.home(level, this);
		if (res != null && home != null) {
			// a resident killed on the road is dead for good
			Resident r = home.resident(res);
			if (r != null) {
				r.extra("travel").putString("died", source.getEntity() instanceof ServerPlayer ? "player" : "road");
				home.setResidentAway(res, Long.MIN_VALUE, "");
				home.removeResident(res);
			}
		}
		if (source.getEntity() instanceof ServerPlayer player && !player.isCreative()) {
			report(level, player, LawService.Offence.MURDER, folk.blockPosition(), 0, res);
			if (!alive()) {
				this.robbedByPlayer = true;
				report(level, player, LawService.Offence.ROBBERY, folk.blockPosition(), 200, null);
			}
		}
	}

	private @Nullable UUID residentOf(Folk folk) {
		for (Member m : this.members) {
			if (m.folk == folk) {
				return m.resident;
			}
		}
		return null;
	}

	@Override
	public void removed(ServerLevel level, Folk folk) {
		folk.markFolded();
	}

	/** Tells the law (or, without it, the home town's standing) what a player did to the party, each kind once. */
	private void report(ServerLevel level, ServerPlayer player, LawService.Offence offence, BlockPos pos, long value, @Nullable UUID victim) {
		if (!this.reported.add(new UUID(player.getUUID().getMostSignificantBits(), player.getUUID().getLeastSignificantBits() ^ offence.ordinal()))) {
			return;
		}
		LawService law = HearthServices.law();
		if (law != null) {
			law.report(new LawService.Crime(offence, player.getUUID(), this.from, pos, victim, value, true, level.getGameTime()));
			return;
		}
		StandingService standing = HearthServices.standing();
		if (standing != null) {
			int weight = switch (offence) {
				case MURDER -> -100;
				case ROBBERY -> -40;
				default -> -10;
			};
			standing.deed(new StandingService.Deed(player.getUUID(), this.from, "roads:traveller_" + offence.getSerializedName(), weight, victim, pos, true));
		}
	}

	/** A pack or draught animal of the party was killed by a player: robbery. */
	void beastKilled(ServerLevel level, Mob beast, DamageSource source) {
		if (this.beasts.contains(beast) || beast == this.mount) {
			if (source.getEntity() instanceof ServerPlayer player && !player.isCreative()) {
				this.hostile = player.getUUID();
				report(level, player, LawService.Offence.ROBBERY, beast.blockPosition(), 60, null);
			}
		}
	}

	Direction heading(ServerLevel level) {
		Folk f = lead();
		return f == null ? Direction.NORTH : f.getDirection();
	}
}
