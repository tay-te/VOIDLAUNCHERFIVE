package dev.voidmc.hearth.npc.travel;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.sim.roads.Band;
import dev.voidmc.hearth.sim.roads.Roads;
import dev.voidmc.hearth.sim.roads.Traffic;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A band of robbers on its road near a player (docs/LIVING_WORLD_2.md §1.3, "Bandits"): by day asleep about its camp
 * off the road in cover (a tent, a fire, a chest of what it took, marked stolen); at dusk and by night lying in wait at
 * its spot. A player who comes within 48 of the spot meets them: the leader steps out and makes the demand (the stranger
 * conversation, {@link BanditTalk}) — pay, talk your way past, frighten them off, or fight. They flee below a third of
 * their number. Killing a bandit is no crime: the nearest town counts it a defence of the road. A caravan the books say
 * is robbed while a player is near is robbed in front of them ({@link #stage}), and can be saved.
 */
public final class BanditParty implements Folk.Controller {
	enum State {
		CAMP, WAIT, DEMAND, FIGHT, FLEE, LET_BY
	}

	final long bandId;
	final long road;
	final Culture culture;
	final List<Folk> men = new ArrayList<>();
	final int mustered;
	@Nullable Folk leader;
	@Nullable Camp camp;
	@Nullable BlockPos chest;
	int chestItems;
	final BlockPos campPos;
	final BlockPos spot;
	State state = State.CAMP;
	@Nullable UUID target;
	long since;
	long talkedAt = -1;
	/** A travelling party it falls on (a robbery staged in front of a player), or 0. */
	long victim;
	final Set<UUID> paid = new HashSet<>();
	int killed;
	private boolean flagged;

	private BanditParty(Band band, Culture culture, BlockPos spot) {
		this.bandId = band.id();
		this.road = band.road();
		this.culture = culture;
		this.campPos = band.camp();
		this.spot = spot;
		this.mustered = band.strength();
	}

	/** The band on the road: at its camp, or at its spot ({@code atSpot}). */
	static @Nullable BanditParty spawn(ServerLevel level, Traffic traffic, Band band, BlockPos spot, boolean atSpot) {
		BlockPos at = atSpot ? spot : band.camp();
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
		BlockPos stand = FolkSpots.standableNear(level, ground, 6);
		if (stand == null || !level.isPositionEntityTicking(stand)) {
			return null;
		}
		SimWorld w = traffic.world();
		Settlement near = w.nearest(spot, 4000);
		Culture culture = near == null ? Culture.TEMPERATE : near.culture();
		BanditParty p = new BanditParty(band, culture, spot);
		RandomSource r = RandomSource.create(band.id() * 0x9E3779B97F4A7C15L);
		for (int i = 0; i < band.strength(); i++) {
			Folk f = Wayfarers.stranger(level, stand, culture, JobType.BANDIT, r, p);
			if (f == null) {
				continue;
			}
			Wayfarers.arm(f, JobType.BANDIT, r);
			p.men.add(f);
			if (p.leader == null) {
				p.leader = f;
				f.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
			}
		}
		if (p.men.isEmpty()) {
			return null;
		}
		p.state = atSpot ? State.WAIT : State.CAMP;
		p.since = level.getGameTime();
		if (!atSpot) {
			p.makeCamp(level, band);
		}
		return p;
	}

	/** The camp: a tent, a fire and the chest of what the band has taken (marked stolen). */
	void makeCamp(ServerLevel level, Band band) {
		BlockPos ground = Camp.ground(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, this.campPos));
		if (ground == null) {
			return;
		}
		this.camp = new Camp(ground);
		this.camp.fire(level);
		this.camp.tent(level, Direction.from2DDataValue((int) Math.floorMod(this.bandId, 4)));
		this.camp.bedrolls(level, Math.min(4, this.men.size()), this.bandId);
		BlockPos c = Camp.ground(level, ground.offset(2, 0, -2));
		if (c != null && this.camp.put(level, c, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH))) {
			this.chest = c;
			fillChest(level, band, c);
		}
		Parties.remember(level, this.camp);
	}

	private void fillChest(ServerLevel level, Band band, BlockPos at) {
		BlockEntity be = level.getBlockEntity(at);
		if (!(be instanceof Container box)) {
			return;
		}
		LawService law = HearthServices.law();
		SimWorld w = Roads.state(dev.voidmc.hearth.sim.SimServer.world(level)) == null ? null
			: Roads.state(dev.voidmc.hearth.sim.SimServer.world(level)).world();
		Settlement near = w == null ? null : w.nearest(at, 4000);
		int slot = 0;
		int[] loot = band.loot();
		for (int g = 0; g < loot.length && g < Goods.VALUES.length && slot < box.getContainerSize(); g++) {
			int stacks = Math.min(4, loot[g] / 24);
			for (int k = 0; k < stacks && slot < box.getContainerSize(); k++) {
				ItemStack s = HearthServices.goodsItems().sample(Goods.VALUES[g], this.culture, this.bandId * 31 + g * 7 + k);
				s.setCount(Math.min(s.getMaxStackSize(), 4 + (int) Math.floorMod(this.bandId + g + k, 12)));
				if (law != null && near != null) {
					law.markStolen(s, new LawService.Owner(near.id(), null, null, LawService.Owner.Kind.SETTLEMENT), null);
				}
				box.setItem(slot++, s);
			}
		}
		if (band.coin() > 0 && slot < box.getContainerSize()) {
			box.setItem(slot++, new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET, (int) Math.min(64, 1 + band.coin() / 40)));
		}
		this.chestItems = count(box);
	}

	private static int count(Container box) {
		int n = 0;
		for (int i = 0; i < box.getContainerSize(); i++) {
			n += box.getItem(i).getCount();
		}
		return n;
	}

	int size() {
		return this.men.size();
	}

	boolean alive() {
		for (Folk f : this.men) {
			if (f.isAlive() && !f.isRemoved()) {
				return true;
			}
		}
		return false;
	}

	static boolean night(ServerLevel level) {
		long t = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return t >= 12000 && t < 23300;
	}

	// ---------------------------------------------------------------- the tick

	void tick(ServerLevel level, Traffic traffic, long now) {
		this.men.removeIf(f -> !f.isAlive() || f.isRemoved());
		if (this.men.isEmpty()) {
			return;
		}
		if (this.leader != null && (!this.leader.isAlive() || this.leader.isRemoved())) {
			this.leader = this.men.getFirst();
		}
		ServerPlayer target = this.target == null ? null : level.getServer().getPlayerList().getPlayer(this.target);
		switch (this.state) {
			case CAMP -> {
				if ((now & 31) == 0 && this.camp != null) {
					sitAbout(this.camp.centre(), night(level) ? FolkPose.SIT : FolkPose.SIT);
				}
				if (night(level) && Wayfarers.nearestPlayer(level, this.spot.getX(), this.spot.getZ()) < 64) {
					this.state = State.WAIT;
					this.since = now;
				}
			}
			case WAIT -> {
				if ((now & 15) == 0) {
					// in cover by the spot, low and quiet
					holdAbout(this.spot, 6, 0.6);
					ServerPlayer p = nearestFightable(level, this.spot, 48);
					if (p != null && !this.paid.contains(p.getUUID()) && night(level)) {
						this.target = p.getUUID();
						this.state = State.DEMAND;
						this.since = now;
						for (Folk f : this.men) {
							f.setFolkPose(FolkPose.NONE);
						}
					} else if (!night(level) && now - this.since > 600) {
						this.state = State.CAMP;
					}
				}
			}
			case DEMAND -> {
				if (!Wayfarers.fightable(target, level)) {
					this.state = State.WAIT;
					this.target = null;
					break;
				}
				// they step out: the leader in front, the rest fanning out round the player
				if ((now & 7) == 0) {
					fanOut(target);
				}
				if (this.leader != null && this.talkedAt < 0 && this.leader.distanceToSqr(target) < 5 * 5) {
					this.talkedAt = now;
					this.leader.getNavigation().stop();
					this.leader.getLookControl().setLookAt(target, 30F, 30F);
					this.leader.setFolkPose(FolkPose.TALK);
					dev.voidmc.hearth.npc.NpcModule.conversations().openStranger(target, this.leader, new BanditTalk(this, traffic));
				}
				// walked off without a word, or too long: they take it as an answer
				if (this.talkedAt >= 0 && this.leader != null && (this.leader.distanceToSqr(target) > 20 * 20 || now - this.talkedAt > 20 * 45)) {
					fight(target);
				}
				if (this.talkedAt < 0 && now - this.since > 20 * 30) {
					fight(target);
				}
			}
			case FIGHT -> {
				boolean any = false;
				for (Folk f : this.men) {
					LivingEntity t = f.getTarget();
					if (t == null || !t.isAlive()) {
						t = this.victim != 0 ? victimTarget(level, f) : Wayfarers.fightable(target, level) ? target : null;
						f.setTarget(t);
					}
					// a chase ends forty blocks from the spot
					if (t != null && t.distanceToSqr(Vec3.atCenterOf(this.spot)) > 60 * 60 && this.victim == 0) {
						f.setTarget(null);
						continue;
					}
					any |= Wayfarers.fight(level, f, t);
				}
				if (this.men.size() * 3 < this.mustered) {
					flee(level, traffic, now);
				} else if (!any && now - this.since > 100) {
					this.state = State.WAIT;
					this.target = null;
				}
			}
			case FLEE -> {
				if ((now & 7) == 0) {
					Vec3 from = Wayfarers.fightable(target, level) ? target.position() : Vec3.atCenterOf(this.spot);
					for (Folk f : this.men) {
						Vec3 away = f.position().subtract(from).normalize().scale(16);
						f.getNavigation().moveTo(f.getX() + away.x, f.getY(), f.getZ() + away.z, 0.9);
					}
				}
			}
			case LET_BY -> {
				if ((now & 15) == 0) {
					holdAbout(this.spot, 10, 0.5);
				}
				if (now - this.since > 20 * 40) {
					this.state = State.WAIT;
				}
			}
		}
	}

	/** Their target in a staged robbery: the nearest of the travelling party (its guards first). */
	private @Nullable LivingEntity victimTarget(ServerLevel level, Folk f) {
		TravelParty party = Parties.party(this.victim);
		if (party == null) {
			return null;
		}
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (Folk v : party.folk()) {
			double d = v.distanceToSqr(f);
			if (v.isAlive() && d < bestD) {
				bestD = d;
				best = v;
			}
		}
		return best;
	}

	private @Nullable ServerPlayer nearestFightable(ServerLevel level, BlockPos at, double within) {
		ServerPlayer best = null;
		double bestD = within * within;
		for (ServerPlayer p : level.players()) {
			double d = p.distanceToSqr(Vec3.atCenterOf(at));
			if (d < bestD && Wayfarers.fightable(p, level)) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	private void sitAbout(BlockPos c, FolkPose pose) {
		int i = 0;
		for (Folk f : this.men) {
			if (f.getTarget() != null) {
				continue;
			}
			double a = i++ * (Math.PI * 2 / Math.max(3, this.men.size()));
			double tx = c.getX() + 0.5 + Math.cos(a) * 2.4;
			double tz = c.getZ() + 0.5 + Math.sin(a) * 2.4;
			if (f.position().distanceToSqr(tx, f.getY(), tz) > 2) {
				f.getNavigation().moveTo(tx, c.getY(), tz, 0.5);
				f.setFolkPose(FolkPose.NONE);
			} else {
				f.getNavigation().stop();
				f.setFolkPose(pose);
			}
		}
	}

	private void holdAbout(BlockPos c, int radius, double speed) {
		int i = 0;
		for (Folk f : this.men) {
			double a = i++ * (Math.PI * 2 / Math.max(3, this.men.size())) + this.bandId;
			double tx = c.getX() + 0.5 + Math.cos(a) * radius;
			double tz = c.getZ() + 0.5 + Math.sin(a) * radius;
			if (f.position().distanceToSqr(tx, f.getY(), tz) > 4) {
				f.getNavigation().moveTo(tx, f.getY(), tz, speed);
			} else {
				f.getNavigation().stop();
			}
			f.setShiftKeyDown(this.state == State.WAIT);
		}
	}

	private void fanOut(ServerPlayer p) {
		int i = 0;
		for (Folk f : this.men) {
			f.setShiftKeyDown(false);
			if (f == this.leader) {
				if (this.talkedAt < 0) {
					f.getNavigation().moveTo(p, 0.7);
				}
				continue;
			}
			double a = Math.atan2(f.getZ() - p.getZ(), f.getX() - p.getX()) + (i++ - this.men.size() / 2.0) * 0.5;
			f.getNavigation().moveTo(p.getX() + Math.cos(a) * 5, p.getY(), p.getZ() + Math.sin(a) * 5, 0.75);
			f.getLookControl().setLookAt(p, 30F, 30F);
		}
	}

	/** They set on the player. */
	void fight(@Nullable ServerPlayer p) {
		this.state = State.FIGHT;
		if (p != null) {
			this.target = p.getUUID();
		}
		for (Folk f : this.men) {
			f.setFolkPose(FolkPose.NONE);
			f.setShiftKeyDown(false);
			f.setTarget(p);
		}
	}

	/** They let the player by (paid, or talked round). */
	void letBy(ServerPlayer p, long now) {
		this.paid.add(p.getUUID());
		this.state = State.LET_BY;
		this.since = now;
		this.target = null;
	}

	/** They break and run: the band loses those killed and much of its nerve. */
	void flee(ServerLevel level, Traffic traffic, long now) {
		if (this.state == State.FLEE) {
			return;
		}
		this.state = State.FLEE;
		this.since = now;
		if (this.leader != null && this.leader.isAlive()) {
			say(level, this.leader, Component.translatable("hearth.roads.bandit.scatter"));
		}
		if (this.victim != 0) {
			traffic.foiled(this.victim, this.bandId, true, this.killed);
		} else {
			traffic.hurtBand(this.bandId, Math.max(1, this.killed));
		}
		this.killed = 0;
	}

	static void say(ServerLevel level, Folk f, Component line) {
		Component name = Component.literal(f.folkName()).withStyle(net.minecraft.ChatFormatting.GOLD);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(f) < 16 * 16) {
				p.sendSystemMessage(Component.translatable("hearth.npc.say", name, line));
			}
		}
	}

	/** Falls on a travelling party in front of a player (the books' robbery, played out): they can be beaten off. */
	void stage(TravelParty party, long now) {
		this.victim = party.journeyId;
		this.state = State.FIGHT;
		this.since = now;
		if (this.leader != null) {
			say((ServerLevel) this.leader.level(), this.leader, Component.translatable("hearth.roads.bandit.demand." + (int) Math.floorMod(now, 4)));
		}
	}

	/** Takes the band off the road; whatever of its chest a player took is gone from its loot. */
	void fold(ServerLevel level, Traffic traffic) {
		if (this.chest != null && level.isLoaded(this.chest) && level.getBlockEntity(this.chest) instanceof Container box) {
			int left = count(box);
			if (left < this.chestItems) {
				traffic.lootTaken(this.bandId);
			}
			box.clearContent();
		}
		if (this.camp != null) {
			this.camp.strike(level);
			Parties.forget(level, this.camp);
			this.camp = null;
		}
		for (Folk f : this.men) {
			Wayfarers.fold(f);
		}
		this.men.clear();
	}

	List<Folk> folk() {
		return List.copyOf(this.men);
	}

	// ---------------------------------------------------------------- Folk.Controller

	@Override
	public void tick(ServerLevel level, Folk folk) {
	}

	@Override
	public void interact(ServerPlayer player, Folk folk) {
		if (this.state == State.DEMAND && folk == this.leader) {
			dev.voidmc.hearth.npc.NpcModule.conversations().openStranger(player, folk, new BanditTalk(this,
				Roads.state(dev.voidmc.hearth.sim.SimServer.world((ServerLevel) player.level())).traffic()));
			return;
		}
		say((ServerLevel) player.level(), folk, Component.translatable("hearth.roads.bandit.away." + (int) Math.floorMod(folk.getId(), 3)));
	}

	@Override
	public void hurt(ServerLevel level, Folk folk, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player && !player.isCreative() && this.state != State.FLEE) {
			fight(player);
		}
	}

	@Override
	public void died(ServerLevel level, Folk folk, DamageSource source) {
		folk.markFolded();
		this.killed++;
		if (source.getEntity() instanceof ServerPlayer player) {
			// a defence of the road: the nearest town thinks the better of whoever did it
			StandingService standing = HearthServices.standing();
			SimWorld w = dev.voidmc.hearth.sim.SimServer.world(level);
			Settlement near = w == null ? null : w.nearest(folk.blockPosition(), 3000);
			if (standing != null && near != null) {
				standing.deed(new StandingService.Deed(player.getUUID(), near.id(), "defense:bandit", folk == this.leader ? 8 : 3, null, folk.blockPosition(), true));
			}
		}
		// what they carried of the band's loot falls with them (marked stolen in the chest's way)
		if (this.chest == null && level.getRandom().nextInt(3) == 0) {
			ItemStack s = HearthServices.goodsItems().sample(Goods.CLOTH, this.culture, folk.getId());
			folk.spawnAtLocation(level, s);
		}
		Traffic t = Roads.state(dev.voidmc.hearth.sim.SimServer.world(level)) == null ? null
			: Roads.state(dev.voidmc.hearth.sim.SimServer.world(level)).traffic();
		long alive = this.men.stream().filter(f -> f != folk && f.isAlive() && !f.isRemoved()).count();
		if (t != null && alive * 3 < this.mustered && this.state != State.FLEE) {
			flee(level, t, level.getGameTime());
		}
	}

	@Override
	public void removed(ServerLevel level, Folk folk) {
		folk.markFolded();
	}

	// ---------------------------------------------------------------- the demand

	/** What the band asks of a player: a share of the coin they carry, at most the configured toll. */
	long toll(ServerPlayer player) {
		EconomyService eco = HearthServices.economy();
		long carried = eco == null ? 0 : eco.balance(player);
		var cfg = Roads.config().bandits();
		long share = Math.round(carried * cfg.toll());
		// a rich-looking traveller (gold or diamond on them) is asked for more
		boolean rich = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE)
			|| player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.GOLDEN_CHESTPLATE);
		long cap = rich ? cfg.maxToll() * 2 : cfg.maxToll();
		return Math.max(Math.min(10, carried), Math.min(cap, share));
	}

	/** The odds of talking them round: charm and a ready tongue against the size of the band. */
	double persuadeOdds(ServerPlayer player) {
		StandingService standing = HearthServices.standing();
		float charm = standing == null ? 0 : standing.charisma(player);
		int speech = HearthServices.playerSkills().level(player, dev.voidmc.hearth.api.PlayerSkills.SPEECH);
		return Mth.clamp(0.25 + 0.35 * charm + 0.03 * speech - 0.04 * this.men.size(), 0.05, 0.9);
	}

	/** The odds of frightening them off: armour and a drawn blade weigh heavily, and small bands scare easily. */
	double intimidateOdds(ServerPlayer player) {
		StandingService standing = HearthServices.standing();
		float menace = standing == null ? armour(player) : Math.max(standing.menace(player), armour(player));
		int speech = HearthServices.playerSkills().level(player, dev.voidmc.hearth.api.PlayerSkills.SPEECH);
		return Mth.clamp(0.1 + 0.75 * menace + 0.02 * speech - 0.07 * (this.men.size() - 3), 0.05, 0.95);
	}

	/** How fearsome a player's gear is, 0–1: plate and a blade in hand. */
	static float armour(ServerPlayer p) {
		float a = Mth.clamp(p.getArmorValue() / 20F, 0, 1);
		boolean blade = p.getMainHandItem().is(net.minecraft.tags.ItemTags.SWORDS) || p.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES);
		return Mth.clamp(a * 0.8F + (blade ? 0.25F : 0), 0, 1);
	}

	Mob leaderOr(Folk f) {
		return this.leader != null ? this.leader : f;
	}
}
