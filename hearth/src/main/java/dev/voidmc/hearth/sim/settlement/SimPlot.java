package dev.voidmc.hearth.sim.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A plot of a live (or historic) settlement: its spec and how far construction got. The progress counter
 * {@code (stage, step)} is the single source of truth for both abstract labour and embodied builders (see docs/SIM.md).
 * Mutated only through {@link SimSettlement} and the clock, on the server thread (or the one thread running a history).
 */
public final class SimPlot implements PlotView {
	public static final int NONE = -1;

	public static final Codec<SimPlot> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("id").forGetter(SimPlot::id),
		PlotSpec.CODEC.fieldOf("spec").forGetter(SimPlot::spec),
		PlotState.CODEC.fieldOf("state").forGetter(SimPlot::state),
		Codec.INT.optionalFieldOf("stage", 0).forGetter(SimPlot::stage),
		Codec.INT.optionalFieldOf("step", 0).forGetter(SimPlot::step),
		UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(p -> Optional.ofNullable(p.owner)),
		Codec.INT.optionalFieldOf("replaces", NONE).forGetter(p -> p.replaces),
		PlotSpec.CODEC.optionalFieldOf("replaced_spec").forGetter(p -> Optional.ofNullable(p.replacedSpec)),
		Codec.STRING.optionalFieldOf("reason", "").forGetter(p -> p.reason),
		Codec.BOOL.optionalFieldOf("funded", false).forGetter(p -> p.funded),
		Codec.DOUBLE.optionalFieldOf("labour", 0.0).forGetter(p -> p.labour),
		Codec.INT.optionalFieldOf("beds", 0).forGetter(p -> p.beds),
		Codec.INT_STREAM.xmap(s -> s.toArray(), java.util.Arrays::stream).optionalFieldOf("jobs", new int[0]).forGetter(p -> p.jobs),
		Codec.BOOL.optionalFieldOf("storage", false).forGetter(p -> p.storage),
		Goods.CODEC.optionalFieldOf("stalled").forGetter(p -> Optional.ofNullable(p.stalledOn)),
		Codec.LONG.optionalFieldOf("completed_day", Long.MIN_VALUE).forGetter(p -> p.completedDay)
	).apply(i, SimPlot::decode));

	private final int id;
	private final PlotSpec spec;
	PlotState state = PlotState.PLANNED;
	int stage;
	int step;
	@Nullable UUID owner;
	/** The plot this one rebuilds, while it stands; {@link #replacedSpec} remembers what stood there afterwards. */
	int replaces = NONE;
	@Nullable PlotSpec replacedSpec;
	String reason = "";
	/** Whether the current stage's goods have been taken from the stockpile. */
	boolean funded;
	/** Work units accrued against the current stage. */
	double labour;
	/** Beds and job slots (by {@link JobType#ordinal()}) once built, so a loaded settlement needs no blueprints for them. */
	int beds;
	int[] jobs = new int[0];
	boolean storage;
	/** The goods the current stage is waiting for, or null; the stall event fires once per episode. */
	@Nullable Goods stalledOn;
	long completedDay = Long.MIN_VALUE;

	/** Transient: waiting only because an earlier plot has first claim on the goods (no stall of its own was announced). */
	boolean heldBack;
	/** Transient: an excavation (its blueprint's cuts are yield; its own trade digs it). */
	boolean digs;
	/** Transient: when an embodied builder last advanced this plot (game time), and the blueprint facts. */
	long lastEmbodiedAdvance = Long.MIN_VALUE;
	@Nullable PlotFacts facts;

	public SimPlot(int id, PlotSpec spec) {
		this.id = id;
		this.spec = spec;
	}

	private static SimPlot decode(int id, PlotSpec spec, PlotState state, int stage, int step, Optional<UUID> owner, int replaces,
		Optional<PlotSpec> replacedSpec, String reason, boolean funded, double labour, int beds, int[] jobs, boolean storage,
		Optional<Goods> stalled, long completedDay) {
		SimPlot p = new SimPlot(id, spec);
		p.state = state;
		p.stage = stage;
		p.step = step;
		p.owner = owner.orElse(null);
		p.replaces = replaces;
		p.replacedSpec = replacedSpec.orElse(null);
		p.reason = reason;
		p.funded = funded;
		p.labour = labour;
		p.beds = beds;
		p.jobs = jobs;
		p.storage = storage;
		p.stalledOn = stalled.orElse(null);
		p.completedDay = completedDay;
		return p;
	}

	@Override
	public int id() {
		return this.id;
	}

	@Override
	public PlotSpec spec() {
		return this.spec;
	}

	@Override
	public PlotState state() {
		return this.state;
	}

	@Override
	public int stage() {
		return this.stage;
	}

	@Override
	public int step() {
		return this.step;
	}

	@Override
	public @Nullable UUID owner() {
		return this.owner;
	}

	@Override
	public boolean waitingForGoods() {
		return !this.funded && this.stalledOn != null;
	}

	/** The plot id this one rebuilds, or {@link #NONE}. */
	@Override
	public int replaces() {
		return this.replaces;
	}

	/** What stood here before this plot rebuilt it (kept after the old plot is gone, so its blocks count as ours). */
	public @Nullable PlotSpec replacedSpec() {
		return this.replacedSpec;
	}

	public String reason() {
		return this.reason;
	}

	public boolean funded() {
		return this.funded;
	}

	public double labour() {
		return this.labour;
	}

	public int beds() {
		return this.beds;
	}

	public int jobSlots(JobType job) {
		return job.ordinal() < this.jobs.length ? this.jobs[job.ordinal()] : 0;
	}

	public boolean isStorage() {
		return this.storage;
	}

	public @Nullable Goods stalledOn() {
		return this.stalledOn;
	}

	public long completedDay() {
		return this.completedDay;
	}

	public long lastEmbodiedAdvance() {
		return this.lastEmbodiedAdvance;
	}

	/** Whether construction still has work to do here. */
	public boolean isActive() {
		return this.state == PlotState.PLANNED || this.state == PlotState.BUILDING;
	}

	/** Whether its beds and workplaces count. */
	public boolean isStanding() {
		return this.state == PlotState.BUILT;
	}

	/**
	 * Whether its job sites employ folk now: a standing building, or an excavation being worked (a quarry or a mine is a
	 * workplace from the day it is staked out — its quarriers and miners are the ones who dig it).
	 */
	public boolean employs() {
		return this.state == PlotState.BUILT || this.digs && isActive();
	}

	/** The progress counter as one comparable number. */
	public long cursor() {
		return Cursor.pack(this.stage, this.step);
	}

	/** The footprint, one block wider all round (eaves and signs may overhang by one), from the floor's foundations to the roof. */
	public BoundingBox reach() {
		return reach(this.spec);
	}

	public static BoundingBox reach(PlotSpec s) {
		int y = s.origin().getY();
		return new BoundingBox(s.origin().getX() - 1, y - 48, s.origin().getZ() - 1, s.origin().getX() + s.sizeX(), y + s.maxHeight() + 1,
			s.origin().getZ() + s.sizeZ());
	}

	@Override
	public String toString() {
		return "Plot[" + this.id + " " + this.spec.archetype() + " " + this.state.getSerializedName() + " " + this.stage + ":" + this.step + "]";
	}
}
