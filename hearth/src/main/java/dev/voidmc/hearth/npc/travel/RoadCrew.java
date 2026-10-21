package dev.voidmc.hearth.npc.travel;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.travel.cart.CartEntity;
import dev.voidmc.hearth.sim.SimModule;
import dev.voidmc.hearth.sim.roads.LiveRoads;
import dev.voidmc.hearth.sim.roads.RoadBook;
import dev.voidmc.hearth.sim.roads.RoadSegments;
import dev.voidmc.hearth.sim.roads.Way;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * A road crew at the head of an unfinished road (docs/LIVING_WORLD_2.md §1.3): two to four builders of the nearer town,
 * away from it while they work, digging the bed and setting the stones of the plot being built, a cart of gravel beside
 * them, the foreman calling out now and then. Their work is the road's own (the same work the town's labour does in the
 * books, a little faster for being watched), and the applier writes the plot as far as it has gone, so the road is seen
 * to grow. Folded when nobody is near; the books go on.
 */
public final class RoadCrew implements Folk.Controller {
	/** Game ticks between the crew's reckonings of work done. */
	static final int WORK_TICKS = 40;

	final long roadId;
	final boolean fromA;
	final long town;
	final List<Folk> men = new ArrayList<>();
	final List<UUID> residents = new ArrayList<>();
	@Nullable Mob mule;
	@Nullable CartEntity cart;
	private final List<BlockPos> spots = new ArrayList<>();
	/** Bodies still to come, one a tick. */
	private final java.util.ArrayDeque<java.util.function.Consumer<ServerLevel>> pending = new java.util.ArrayDeque<>();
	private long nextWork;
	private long nextBark;

	private RoadCrew(long roadId, boolean fromA, long town) {
		this.roadId = roadId;
		this.fromA = fromA;
		this.town = town;
	}

	static @Nullable RoadCrew spawn(ServerLevel level, RoadBook.LiveRoad r, boolean fromA, SimSettlement s, long clock) {
		Way w = r.way();
		int head = r.head(fromA);
		if (w == null || head < 0) {
			return null;
		}
		RoadSegments.Segment seg = w.segments().get(head);
		int[] pts = seg.spec().params().getIntArray("points").orElse(new int[0]);
		if (pts.length < 6) {
			return null;
		}
		RoadCrew crew = new RoadCrew(r.id(), fromA, s.id());
		for (int i = 0; i < pts.length; i += 6) {
			crew.spots.add(new BlockPos(pts[i], pts[i + 1], pts[i + 2]));
		}
		BlockPos at = crew.spots.get(crew.spots.size() / 2);
		BlockPos top = Wayfarers.ground(level, at);
		BlockPos stand = top == null ? null : FolkSpots.standableNear(level, top, 5);
		if (stand == null || !level.isPositionEntityTicking(stand)) {
			return null;
		}
		RandomSource rnd = RandomSource.create(r.id() ^ clock / 24000);
		List<Resident> builders = new ArrayList<>();
		for (Resident res : s.residentList()) {
			if (res.age == AgeStage.ADULT && res.job == JobType.BUILDER && !s.isResidentOut(res.id)) {
				builders.add(res);
			}
		}
		int count = Math.min(builders.size(), 2 + rnd.nextInt(3));
		// one body a tick: the first builder now, the rest, the mule and its cart as the crew ticks
		for (int i = 0; i < count; i++) {
			Resident res = builders.get(i);
			boolean foreman = i == 0;
			crew.pending.add(lv -> {
				Folk f = Wayfarers.resident(lv, stand, s.culture(), res, rnd, crew);
				if (f == null) {
					return;
				}
				f.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(foreman ? Items.IRON_SHOVEL : rnd.nextBoolean() ? Items.IRON_PICKAXE : Items.STONE_SHOVEL));
				crew.men.add(f);
				crew.residents.add(res.id);
				s.setResidentAway(res.id, clock + 2400, "roads:crew");
			});
		}
		while (crew.men.isEmpty() && !crew.pending.isEmpty()) {
			crew.pending.poll().accept(level);
		}
		if (crew.men.isEmpty()) {
			crew.pending.clear();
			return null;
		}
		crew.pending.add(lv -> crew.mule = Wayfarers.beast(lv, stand, Wayfarers.draught(s.culture(), CartEntity.Kind.CART, rnd), rnd));
		crew.pending.add(lv -> {
			if (crew.mule != null && crew.mule.isAlive()) {
				crew.cart = Wayfarers.cart(lv, crew.mule, CartEntity.Kind.CART, CartEntity.Load.STONE);
			}
		});
		return crew;
	}

	int size() {
		return this.men.size() + (this.mule != null ? 1 : 0) + (this.cart != null ? 1 : 0) + this.pending.size();
	}

	@Nullable BlockPos centre() {
		return this.spots.isEmpty() ? null : this.spots.get(this.spots.size() / 2);
	}

	void tick(ServerLevel level, LiveRoads live, @Nullable SimSettlement s, long now, long clock) {
		java.util.function.Consumer<ServerLevel> next = this.pending.poll();
		if (next != null) {
			next.accept(level);
		}
		this.men.removeIf(f -> !f.isAlive() || f.isRemoved());
		if (this.men.isEmpty()) {
			return;
		}
		if ((now & 15) == 0) {
			for (int i = 0; i < this.men.size(); i++) {
				Folk f = this.men.get(i);
				BlockPos spot = this.spots.get((int) Math.floorMod(i * 2 + now / 600, this.spots.size()));
				if (f.position().distanceToSqr(spot.getX() + 0.5, f.getY(), spot.getZ() + 0.5) > 4) {
					f.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.5);
					f.setFolkPose(FolkPose.NONE);
				} else {
					f.getNavigation().stop();
					f.setFolkPose(FolkPose.HAMMER);
					if ((now & 31) == 0) {
						f.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
					}
				}
			}
			if (this.mule != null && this.mule.isAlive() && this.mule.distanceToSqr(this.men.getFirst()) > 6 * 6) {
				this.mule.getNavigation().moveTo(this.men.getFirst(), 1.0);
			}
		}
		if (now >= this.nextWork) {
			this.nextWork = now + WORK_TICKS;
			var l = SimModule.rules().config.labour();
			// a builder's hour of work every sim hour (a thousand ticks), each man of them
			double work = this.men.size() * l.workPerBuilderHour() * WORK_TICKS / 1000.0;
			live.crewWork(this.roadId, this.fromA, work);
			if (s != null) {
				for (UUID id : this.residents) {
					s.setResidentAway(id, clock + 2400, "roads:crew");
				}
			}
		}
		if (now >= this.nextBark && !this.men.isEmpty()) {
			this.nextBark = now + 20 * 50;
			Folk foreman = this.men.getFirst();
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(foreman) < 16 * 16) {
					BanditParty.say(level, foreman, Component.translatable("hearth.roads.bark.crew." + (int) Math.floorMod(now / 1000, Parties.BARKS)));
					break;
				}
			}
		}
	}

	void fold(ServerLevel level, @Nullable SimSettlement s) {
		this.pending.clear();
		for (Folk f : this.men) {
			Wayfarers.fold(f);
		}
		if (s != null) {
			for (UUID id : this.residents) {
				s.setResidentAway(id, Long.MIN_VALUE, "");
			}
		}
		if (this.mule != null && !this.mule.isRemoved()) {
			this.mule.discard();
		}
		if (this.cart != null && !this.cart.isRemoved()) {
			this.cart.discard();
		}
		this.men.clear();
	}

	@Override
	public void tick(ServerLevel level, Folk folk) {
	}

	@Override
	public void interact(ServerPlayer player, Folk folk) {
		folk.getLookControl().setLookAt(player, 30F, 30F);
		BanditParty.say((ServerLevel) player.level(), folk, Component.translatable("hearth.roads.bark.crew." + (int) Math.floorMod(folk.getId(), Parties.BARKS)));
	}

	@Override
	public void died(ServerLevel level, Folk folk, DamageSource source) {
		folk.markFolded();
	}

	@Override
	public void removed(ServerLevel level, Folk folk) {
		folk.markFolded();
	}
}
