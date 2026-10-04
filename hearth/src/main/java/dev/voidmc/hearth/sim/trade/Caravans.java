package dev.voidmc.hearth.sim.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.sim.SimConfig;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Trade between towns (docs/LIVING_WORLD.md §7.6), abstract: a town with a surplus sends a caravan to the town within
 * reach that is shortest of it (daughter hamlets ship to their parent first), the buyer pays on arrival at a price
 * between the two towns' (so a player can run a merchant career on the difference), and the road takes time and has its
 * risks — rough land and long distances lose caravans to bandits and the wild. {@code CARAVAN} fires as one sets out
 * and as it arrives or is lost; the npc module meets caravans on the road near players ({@link #positionAt}), the quest
 * module makes escorts ({@link #escort}) and robberies ({@link #rob}) of them.
 *
 * <p>Cheap: each town considers a caravan once every few sim days (a pass over the towns within reach), arrivals are a
 * queue checked by its head each tick. State is world data ({@code hearth:sim/caravans}). Server thread only.
 */
public final class Caravans {
	/**
	 * A caravan on the road.
	 *
	 * @param cargo  goods by ordinal
	 * @param value  bits the buyer pays on arrival
	 * @param doomed decided at departure from the road's risk (the sim's own bandits): it will be lost unless escorted
	 */
	public record Caravan(long id, long from, long to, int[] cargo, long value, long departTick, long arriveTick, boolean doomed, BlockPos fromPos,
		BlockPos toPos) {
		public static final Codec<Caravan> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("id").forGetter(Caravan::id),
			Codec.LONG.fieldOf("from").forGetter(Caravan::from),
			Codec.LONG.fieldOf("to").forGetter(Caravan::to),
			Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of).fieldOf("cargo").forGetter(Caravan::cargo),
			Codec.LONG.fieldOf("value").forGetter(Caravan::value),
			Codec.LONG.fieldOf("depart").forGetter(Caravan::departTick),
			Codec.LONG.fieldOf("arrive").forGetter(Caravan::arriveTick),
			Codec.BOOL.optionalFieldOf("doomed", false).forGetter(Caravan::doomed),
			BlockPos.CODEC.fieldOf("from_pos").forGetter(Caravan::fromPos),
			BlockPos.CODEC.fieldOf("to_pos").forGetter(Caravan::toPos)
		).apply(i, Caravan::new));

		public int units() {
			int n = 0;
			for (int c : this.cargo) {
				n += c;
			}
			return n;
		}
	}

	/** The caravans on the road in one world, saved with it. */
	public static final class State {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Caravan.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(s -> s.active),
			Codec.LONG.optionalFieldOf("next_id", 1L).forGetter(s -> s.nextId),
			Codec.LONG.optionalFieldOf("arrived", 0L).forGetter(s -> s.arrived),
			Codec.LONG.optionalFieldOf("lost", 0L).forGetter(s -> s.lost)
		).apply(i, (active, next, arrived, lost) -> {
			State s = new State();
			s.active.addAll(active);
			s.nextId = next;
			s.arrived = arrived;
			s.lost = lost;
			return s;
		}));

		final List<Caravan> active = new ArrayList<>();
		long nextId = 1;
		long arrived;
		long lost;
	}

	public static final HearthDataKey<State> KEY = HearthDataKey.register(Hearth.id("sim/caravans"), HearthDataKey.Scope.WORLD, State.CODEC, State::new);

	private final SimWorld world;
	private long departed;

	public Caravans(SimWorld world) {
		this.world = world;
	}

	private State state() {
		return this.world.data(KEY);
	}

	/** Caravans on the road now, soonest arrival first. */
	public List<Caravan> active() {
		return Collections.unmodifiableList(this.state().active);
	}

	public @Nullable Caravan caravan(long id) {
		for (Caravan c : this.state().active) {
			if (c.id == id) {
				return c;
			}
		}
		return null;
	}

	/** Where a caravan is on its road at a game tick (straight between the towns, at its pace). */
	public static BlockPos positionAt(Caravan c, long tick) {
		double t = Mth.clamp((tick - c.departTick) / (double) Math.max(1, c.arriveTick - c.departTick), 0, 1);
		return new BlockPos(Mth.floor(Mth.lerp(t, c.fromPos.getX(), c.toPos.getX())), Mth.floor(Mth.lerp(t, c.fromPos.getY(), c.toPos.getY())),
			Mth.floor(Mth.lerp(t, c.fromPos.getZ(), c.toPos.getZ())));
	}

	/** An escort rides with the caravan: whatever the road held for it, it arrives. Returns whether it was on the road. */
	public boolean escort(long id) {
		List<Caravan> active = this.state().active;
		for (int i = 0; i < active.size(); i++) {
			Caravan c = active.get(i);
			if (c.id == id) {
				active.set(i, new Caravan(c.id, c.from, c.to, c.cargo, c.value, c.departTick, c.arriveTick, false, c.fromPos, c.toPos));
				this.world.markDirty();
				return true;
			}
		}
		return false;
	}

	/**
	 * The caravan is robbed now (by a player on a quest, or bandits the npc module staged): its cargo is gone, the buyer
	 * pays nothing, {@code CARAVAN ROBBED} fires. Returns the cargo taken (by goods ordinal), or null if it was not on the
	 * road. The law module hears of it through its own report.
	 */
	public int @Nullable [] rob(long id) {
		List<Caravan> active = this.state().active;
		for (int i = 0; i < active.size(); i++) {
			Caravan c = active.get(i);
			if (c.id == id) {
				active.remove(i);
				this.state().lost++;
				this.world.markDirty();
				fire(c, SettlementEvents.CaravanEvent.Kind.ROBBED);
				return c.cargo.clone();
			}
		}
		return null;
	}

	/** Arrivals and losses that are due (every tick: only the head of the queue is looked at). */
	public void tick(long now) {
		List<Caravan> active = this.state().active;
		while (!active.isEmpty() && active.getFirst().arriveTick <= now) {
			Caravan c = active.removeFirst();
			this.world.markDirty();
			arrive(c);
		}
	}

	private void arrive(Caravan c) {
		State st = this.state();
		SimSettlement from = this.world.settlement(c.from);
		SimSettlement to = this.world.settlement(c.to);
		if (c.doomed || to == null) {
			st.lost++;
			// bandits on the long roads, the wild on rough ones: either way the cargo is gone
			fire(c, (c.id & 1) == 0 ? SettlementEvents.CaravanEvent.Kind.ROBBED : SettlementEvents.CaravanEvent.Kind.LOST);
			return;
		}
		st.arrived++;
		for (int g = 0; g < c.cargo.length && g < Goods.VALUES.length; g++) {
			if (c.cargo[g] > 0) {
				to.stockpile().add(Goods.VALUES[g], c.cargo[g]);
			}
		}
		long paid = Math.min(c.value, to.treasury());
		if (paid > 0 && to.withdraw(paid)) {
			to.land().traded(0, paid);
			if (from != null) {
				from.deposit(paid);
				from.land().traded(paid, 0);
			}
		}
		to.markDirty();
		fire(c, SettlementEvents.CaravanEvent.Kind.ARRIVED);
	}

	private void fire(Caravan c, SettlementEvents.CaravanEvent.Kind kind) {
		SimSettlement from = this.world.settlement(c.from);
		SimSettlement to = this.world.settlement(c.to);
		if (from != null && to != null) {
			SettlementEvents.CARAVAN.invoker().onCaravan(this.world, from, to, kind, c.cargo.clone());
		}
	}

	/**
	 * Once a sim day per town: if it is time, and it has a surplus, it sends a caravan to the town within reach that
	 * wants it most (value to the buyer, nearness, kin).
	 */
	public void consider(SimSettlement s, long day) {
		SimRules r = this.world.rules();
		SimConfig.Caravans cfg = r.resources.caravans();
		if (!cfg.enabled() || s.population() < 4) {
			return;
		}
		long last = s.land().lastCaravanDay();
		if (last != Long.MIN_VALUE && day - last < cfg.intervalDays()) {
			return;
		}
		for (Caravan c : this.state().active) {
			if (c.from == s.id()) {
				return;
			}
		}
		int n = Goods.VALUES.length;
		int[] surplus = new int[n];
		boolean any = false;
		for (int g = 0; g < n; g++) {
			surplus[g] = surplus(s, g, r);
			any |= surplus[g] >= 16;
		}
		if (!any) {
			return;
		}
		SimSettlement best = null;
		double bestScore = 0;
		double bestValue = 0;
		long d2max = (long) cfg.maxDistance() * cfg.maxDistance();
		for (SimSettlement o : this.world.settlements()) {
			if (o == s || o.population() == 0) {
				continue;
			}
			long dx = o.center().getX() - s.center().getX();
			long dz = o.center().getZ() - s.center().getZ();
			long d2 = dx * dx + dz * dz;
			if (d2 > d2max) {
				continue;
			}
			double value = value(s, o, surplus, cfg.cargo(), r, null);
			boolean kin = o.clockState().parent() == s.id() || s.clockState().parent() == o.id();
			double score = value * (kin ? 1.5 : 1) / (1 + Math.sqrt(d2) / cfg.maxDistance());
			if (score > bestScore) {
				bestScore = score;
				best = o;
				bestValue = value;
			}
		}
		if (best == null || bestValue < cfg.minValue()) {
			return;
		}
		depart(s, best, surplus, cfg, r, day);
	}

	/** What a town can spare of a good: what it holds beyond a third of its store room and what its builds are waiting for. */
	static int surplus(SimSettlement s, int g, SimRules r) {
		int have = s.stockpile().get(Goods.VALUES[g]);
		int keep = (int) (s.stockpile().capacity(Goods.VALUES[g]) * 0.35);
		if (g == Goods.FOOD.ordinal()) {
			keep = Math.max(keep, (int) (s.population() * r.foodAdult * 20));
		}
		keep += waiting(s, g);
		return Math.max(0, have - keep);
	}

	/** What a town is short of: up to a third of its store room, and twice what its stalled builds lack. */
	static int shortage(SimSettlement s, int g, SimRules r) {
		int have = s.stockpile().get(Goods.VALUES[g]);
		int want = (int) (s.stockpile().capacity(Goods.VALUES[g]) * 0.3);
		if (g == Goods.FOOD.ordinal()) {
			want = Math.max(want, (int) (s.population() * r.foodAdult * 15));
		}
		want = Math.min(want, 2000) + 2 * waiting(s, g);
		return Math.max(0, want - have);
	}

	private static int waiting(SimSettlement s, int g) {
		int n = 0;
		for (SimPlot p : s.plots()) {
			if (p.isActive() && p.stalledOn() != null && p.stalledOn().ordinal() == g) {
				n += 64;
			}
		}
		return n;
	}

	/** A town's price for a good: dearer the shorter it is (up to three times), never below half. */
	static double price(SimSettlement s, int g, SimRules r) {
		int have = s.stockpile().get(Goods.VALUES[g]);
		double want = Math.max(1, s.stockpile().capacity(Goods.VALUES[g]) * 0.3);
		return r.prices[g] * Mth.clamp(want / (have + want * 0.25), 0.5, 3.0);
	}

	/** What the cargo {@code from} could send is worth to {@code to}; fills {@code cargo} if given. */
	static double value(SimSettlement from, SimSettlement to, int[] surplus, int capacity, SimRules r, int @Nullable [] cargo) {
		int n = surplus.length;
		Integer[] order = new Integer[n];
		double[] unit = new double[n];
		for (int g = 0; g < n; g++) {
			order[g] = g;
			unit[g] = price(to, g, r) - price(from, g, r) * 0.8;
		}
		java.util.Arrays.sort(order, (a, b) -> Double.compare(unit[b], unit[a]));
		int room = capacity;
		double value = 0;
		for (int g : order) {
			if (room <= 0 || unit[g] <= 0) {
				break;
			}
			int k = Math.min(room, Math.min(surplus[g], shortage(to, g, r)));
			if (k <= 0) {
				continue;
			}
			room -= k;
			value += k * price(to, g, r);
			if (cargo != null) {
				cargo[g] = k;
			}
		}
		return value;
	}

	private void depart(SimSettlement s, SimSettlement to, int[] surplus, SimConfig.Caravans cfg, SimRules r, long day) {
		int[] cargo = new int[surplus.length];
		value(s, to, surplus, cfg.cargo(), r, cargo);
		long bits = 0;
		for (int g = 0; g < cargo.length; g++) {
			if (cargo[g] <= 0) {
				continue;
			}
			cargo[g] = s.stockpile().take(Goods.VALUES[g], cargo[g]);
			double sell = price(s, g, r);
			double buy = price(to, g, r);
			bits += Math.round(cargo[g] * (Math.min(sell, buy) + Math.abs(buy - sell) * cfg.margin()));
		}
		State st = this.state();
		long id = st.nextId++;
		double rough = roughness(this.world.terrain(), s.center(), to.center());
		double dist = Math.sqrt(s.center().distSqr(to.center()));
		double days = dist * (1 + rough) / cfg.speed();
		long now = this.world.clock();
		long arrive = now + Math.max(200, (long) (days * SimRules.DAY));
		double risk = cfg.risk() * dist / 1000.0 * (1 + rough);
		if (s.jobSlots(JobType.GUARD) > 0) {
			// the town's guards ride along part of the way
			risk *= 0.6;
		}
		boolean doomed = unit(id ^ this.world.level().getSeed()) < Math.min(0.9, risk);
		Caravan c = new Caravan(id, s.id(), to.id(), cargo, bits, now, arrive, doomed, s.center(), to.center());
		List<Caravan> active = st.active;
		int at = 0;
		while (at < active.size() && active.get(at).arriveTick <= arrive) {
			at++;
		}
		active.add(at, c);
		s.land().caravanSent(day);
		s.markDirty();
		this.world.markDirty();
		this.departed++;
		fire(c, SettlementEvents.CaravanEvent.Kind.DEPARTED);
	}

	/** How hard the road is: water to ford or go round, and climbing, sampled along the way (0 easy, 1+ hard). */
	static double roughness(TerrainProbe t, BlockPos a, BlockPos b) {
		int samples = 16;
		int water = 0;
		double climb = 0;
		int last = t.groundY(a.getX(), a.getZ());
		for (int i = 1; i <= samples; i++) {
			double f = i / (double) samples;
			int x = (int) Mth.lerp(f, a.getX(), b.getX());
			int z = (int) Mth.lerp(f, a.getZ(), b.getZ());
			if (t.isWater(x, z)) {
				water++;
			}
			int g = t.groundY(x, z);
			climb += Math.abs(g - last);
			last = g;
		}
		double dist = Math.max(1, Math.sqrt(a.distSqr(b)));
		return water * 0.8 / samples + Math.min(2, climb / dist * 4);
	}

	private static double unit(long seed) {
		long z = seed * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return ((z ^ (z >>> 31)) >>> 11) / (double) (1L << 53);
	}

	public String describe() {
		State st = this.state();
		return String.format(java.util.Locale.ROOT, "%d caravans on the road, %d departed this session, %d arrived, %d lost (all time)", st.active.size(),
			this.departed, st.arrived, st.lost);
	}
}
