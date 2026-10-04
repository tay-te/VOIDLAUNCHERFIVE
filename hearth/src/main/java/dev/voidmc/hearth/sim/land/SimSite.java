package dev.voidmc.hearth.sim.land;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.ResourceSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A resource site as the sim keeps it: mutable, saved with the settlement, handed to other modules as an immutable
 * {@link ResourceSite}. Units of {@link #capacity} and {@link #remaining} are the site's own goods (logs for a wood,
 * rock for a quarry face, harvests for fertile land).
 */
public final class SimSite {
	/** {@link #flags}: the depletion event has fired and not been re-armed by regrowth. */
	static final int EXHAUSTED = 1;

	final int id;
	final ResourceSite.Kind kind;
	BlockPos center;
	BoundingBox area;
	double capacity;
	double remaining;
	/** Units regrown a sim day. */
	double regen;
	/** How rich the ground is, 0–1+: scales what a worker-day yields. */
	float density;
	/** The primary trade's yields per worker-day at full richness (by goods ordinal). */
	float[] yields;
	/** The block or item it gives: a wood's log, a quarry's rock, a rarity's item. */
	@Nullable Identifier material;
	int plot = PlotView.NONE;
	int cells;
	/** Ore and coal per block of rock cut here (the survey's estimate from depth, biome and caverns). */
	float ore;
	float fuel;
	int flags;
	/** Logs taken abstractly from a natural wood that are still standing as trees in the world (felled as chunks load). */
	double fellingDebt;
	/** Transient: worker-days spent here today (for the prosperity of the sites in use, and commands). */
	double worked;
	double workedYesterday;

	SimSite(int id, ResourceSite.Kind kind, BlockPos center, BoundingBox area, double capacity, double remaining, double regen, float density,
		float[] yields, @Nullable Identifier material, int plot, int cells, float ore, float fuel, int flags, double fellingDebt) {
		this.id = id;
		this.kind = kind;
		this.center = center;
		this.area = area;
		this.capacity = capacity;
		this.remaining = remaining;
		this.regen = regen;
		this.density = density;
		this.yields = yields.length == Goods.VALUES.length ? yields : java.util.Arrays.copyOf(yields, Goods.VALUES.length);
		this.material = material;
		this.plot = plot;
		this.cells = cells;
		this.ore = ore;
		this.fuel = fuel;
		this.flags = flags;
		this.fellingDebt = fellingDebt;
	}

	public static final Codec<SimSite> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("id").forGetter(s -> s.id),
		ResourceSite.Kind.CODEC.fieldOf("kind").forGetter(s -> s.kind),
		BlockPos.CODEC.fieldOf("center").forGetter(s -> s.center),
		Codec.INT_STREAM.xmap(st -> {
			int[] a = st.toArray();
			return a.length == 6 ? new BoundingBox(a[0], a[1], a[2], a[3], a[4], a[5]) : new BoundingBox(0, 0, 0, 0, 0, 0);
		}, b -> java.util.stream.IntStream.of(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())).fieldOf("area").forGetter(s -> s.area),
		Codec.DOUBLE.fieldOf("capacity").forGetter(s -> s.capacity),
		Codec.DOUBLE.fieldOf("remaining").forGetter(s -> s.remaining),
		Codec.DOUBLE.optionalFieldOf("regen", 0.0).forGetter(s -> s.regen),
		Codec.FLOAT.optionalFieldOf("density", 1F).forGetter(s -> s.density),
		Codec.FLOAT.listOf().optionalFieldOf("yields", List.of()).forGetter(s -> {
			List<Float> l = new ArrayList<>();
			for (float f : s.yields) {
				l.add(f);
			}
			return l;
		}),
		Identifier.CODEC.optionalFieldOf("material").forGetter(s -> Optional.ofNullable(s.material)),
		Codec.INT.optionalFieldOf("plot", PlotView.NONE).forGetter(s -> s.plot),
		Codec.INT.optionalFieldOf("cells", 1).forGetter(s -> s.cells),
		Codec.FLOAT.optionalFieldOf("ore", 0F).forGetter(s -> s.ore),
		Codec.FLOAT.optionalFieldOf("fuel", 0F).forGetter(s -> s.fuel),
		Codec.INT.optionalFieldOf("flags", 0).forGetter(s -> s.flags),
		Codec.DOUBLE.optionalFieldOf("felling", 0.0).forGetter(s -> s.fellingDebt)
	).apply(i, (id, kind, center, area, cap, rem, regen, density, yields, material, plot, cells, ore, fuel, flags, felling) -> {
		float[] y = new float[Goods.VALUES.length];
		for (int g = 0; g < yields.size() && g < y.length; g++) {
			y[g] = yields.get(g);
		}
		return new SimSite(id, kind, center, area, cap, rem, regen, density, y, material.orElse(null), plot, cells, ore, fuel, flags, felling);
	}));

	public int id() {
		return this.id;
	}

	public ResourceSite.Kind kind() {
		return this.kind;
	}

	public BlockPos center() {
		return this.center;
	}

	public BoundingBox area() {
		return this.area;
	}

	public double capacity() {
		return this.capacity;
	}

	public double remaining() {
		return this.remaining;
	}

	public double regen() {
		return this.regen;
	}

	public float density() {
		return this.density;
	}

	public @Nullable Identifier material() {
		return this.material;
	}

	public int plot() {
		return this.plot;
	}

	public float ore() {
		return this.ore;
	}

	public float fuel() {
		return this.fuel;
	}

	public double fellingDebt() {
		return this.fellingDebt;
	}

	/** Trees standing in the world for logs already counted: felled where the wood's chunks load ({@code Woodcutter}). */
	public void felled(double logs) {
		this.fellingDebt = Math.max(0, this.fellingDebt - logs);
	}

	/** Forget what is owed (the world cannot pay: no natural trees left where the chunks load). */
	public void forgive() {
		this.fellingDebt = 0;
	}

	public double workedYesterday() {
		return this.workedYesterday;
	}

	/** Worker-days were spent here (today). */
	public void worked(double days) {
		this.worked += days;
	}

	/** The primary trade's yields per worker-day, as doubles (a copy). */
	public double[] yieldsView() {
		double[] d = new double[this.yields.length];
		for (int g = 0; g < d.length; g++) {
			d[g] = this.yields[g];
		}
		return d;
	}

	/** How full it is, 0–1. */
	public double fill() {
		return this.capacity <= 0 ? 0 : Math.max(0, Math.min(1, this.remaining / this.capacity));
	}

	/**
	 * What a worker-day yields here compared with a full site: a wood thinned to a quarter still gives most of its timber
	 * near the edge, so yields fall slower than the stock (square root); an exhausted site gives nothing.
	 */
	public double richness() {
		return this.remaining <= 0 ? 0 : Math.sqrt(fill());
	}

	public boolean renewable() {
		return this.regen > 0;
	}

	/** Distance from the settlement's heart to the site's centre, in blocks (horizontal). */
	public double distanceTo(BlockPos heart) {
		double dx = this.center.getX() - heart.getX();
		double dz = this.center.getZ() - heart.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** The api's view. */
	public ResourceSite view() {
		return new ResourceSite(this.id, this.kind, this.center, this.area, (int) Math.round(this.capacity), (int) Math.floor(Math.max(0, this.remaining)),
			(float) this.regen, this.yields.clone(), this.material, this.plot);
	}

	@Override
	public String toString() {
		return String.format(java.util.Locale.ROOT, "Site[%d %s at %s, %.0f/%.0f, %s]", this.id, this.kind.getSerializedName(), this.center.toShortString(),
			this.remaining, this.capacity, this.material);
	}
}
