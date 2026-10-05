package dev.voidmc.hearth.build.draft;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.LocalMaterials;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.style.Style;
import dev.voidmc.hearth.build.style.StyleContext;
import dev.voidmc.hearth.build.terrain.Site;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.jspecify.annotations.Nullable;

/**
 * A building being drawn: the canvas an {@link dev.voidmc.hearth.build.Archetype} draws on, in the plot's local frame
 * (see {@link Dirs}). Placements are kept per position; a later placement replaces an earlier one at the same position,
 * so a generator can draw broad strokes first and details after (walls, then windows cut into them). When the archetype
 * is done the compiler resolves shapes, orders the steps and packs them into a blueprint.
 *
 * <p>Every placement belongs to a {@link StageKind}: the one set with {@link #stage(StageKind)} (a scope you close),
 * else the role's default carried by the paint ({@link Mat}), else a guess from the block (air above ground clears,
 * air below ground digs, doors and glass are openings, furniture is interior...).
 *
 * <p>Coordinates may leave the footprint by one row in front ({@code z = -1}) for overhangs the plot rules allow
 * (eaves, signs, balconies, at least 3 above the street); everything else must stay in
 * {@code [0, frontage) × [0, depth)} and below {@link #maxHeight()}. The blueprint checks enforce it.
 */
public final class Draft {
	private final PlotSpec spec;
	/** The style as this building wears it (see {@link Style#dress}). */
	private final Style style;
	private final Site site;
	private final LocalFrame frame;
	private final int tier;
	private final RandomSource random;
	private final CompoundTag params;
	/** Who and where the building is, for choosing its dress. */
	private final StyleContext context;
	/** The style variant this building wears (-1 the base dressing). */
	private int variant;
	/** Whether the building wears its town's dressing (walls and towers that must match), not its own. */
	private boolean pinned;
	private final Long2ObjectOpenHashMap<Cell> cells = new Long2ObjectOpenHashMap<>();
	private final List<LocalPoi> pois = new ArrayList<>();
	private final List<int[]> entrances = new ArrayList<>();
	private final Map<Role, Mat> mats = new EnumMap<>(Role.class);
	private final Set<String> missing = new java.util.TreeSet<>();
	private final it.unimi.dsi.fastutil.longs.LongOpenHashSet drained = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
	private @Nullable StageKind stage;
	/** The campaign placements go to while a {@link #campaign(int)} scope is open (-1: none open). */
	private int campaign = -1;
	/** The campaign each cell was drawn in, where one was open (a blueprint built in campaigns). */
	private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap campaigns = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
	/** The order key placements carry while an {@link #order(int)} scope is open (MIN_VALUE: none). */
	private int order = Integer.MIN_VALUE;
	private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap orders = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
	private int sequence;
	private int clipped;
	private int eaveY = Integer.MIN_VALUE;
	/** The lighting pass has run over this draft (docs/LIVING_WORLD.md §1; {@code kit.light.AutoLighter}). */
	private boolean lit;
	private int bodyX0;
	private int bodyZ0;
	private int bodyX1;
	private int bodyZ1;

	public Draft(PlotSpec spec, Style style, Site site) {
		this.spec = spec;
		this.site = site;
		this.frame = site.frame();
		this.tier = Math.clamp(spec.tier(), 0, 4);
		this.random = new LegacyRandomSource(Noise.hash(spec.seed(), 0x5EED));
		this.params = spec.params();
		// the dress: the culture's style chosen for who and where the building is, its region, local materials and station
		Style root = style.root();
		this.context = StyleContext.of(spec, site, root.culture());
		String pin = this.params.getStringOr("pin", "");
		this.pinned = pin.equals("town") || pin.isEmpty() && this.context.category() == ArchetypeCategory.DEFENSE;
		if (this.params.contains("variant")) {
			this.variant = this.params.getIntOr("variant", -1);
		} else if (this.pinned) {
			this.variant = townVariant(root);
		} else {
			this.variant = root.pickVariant(Noise.hash(spec.seed(), 0x7A1A), this.context);
		}
		this.style = root.dress(this.context, spec.seed(), LocalMaterials.of(this.params.getCompoundOrEmpty("local")));
		this.bodyX0 = 0;
		this.bodyZ0 = 0;
		this.bodyX1 = frontage() - 1;
		this.bodyZ1 = depth() - 1;
		this.campaigns.defaultReturnValue(-1);
		this.orders.defaultReturnValue(Integer.MIN_VALUE);
	}

	// ---------------------------------------------------------------------------------------------------- inputs

	public PlotSpec spec() {
		return this.spec;
	}

	/**
	 * The style as this building wears it: its culture's style with the building's region worn, its territory's
	 * materials blended in and its station applied ({@link Style#dress}). {@code style().root()} is the culture's style
	 * undressed.
	 */
	public Style style() {
		return this.style;
	}

	public Habits habits() {
		return this.style.habits(this.variant);
	}

	/**
	 * The style variant (-1 the base dressing): a fashion or a sub-style chosen for the building's station, category,
	 * tier, place and seed ({@link Style#pickVariant(long, StyleContext)}); forced with the param {@code variant}.
	 */
	public int variant() {
		return this.variant;
	}

	/** Who and where the building is, as its dress sees it (station, purposes, biome, landforms...). */
	public StyleContext context() {
		return this.context;
	}

	/** The household's station (the {@code station} param, else the one the archetype or district implies), or null. */
	public @Nullable Station station() {
		return this.context.station();
	}

	/**
	 * The tier the building's materials are chosen at: its tier moved by an explicit station's finish (a merchant's house
	 * at tier 2 dresses at 3, a hovel at 0). Sizes and plans keep reading {@link #tier()}.
	 */
	public int finish() {
		Station s = this.context.station();
		return this.context.explicitStation() && s != null ? this.style.station(s).tier(this.tier) : this.tier;
	}

	/** The regional style the building wears ({@code lumen}, {@code volcanic}...), or null. */
	public @Nullable String region() {
		Style r = this.style.worn();
		return r == null || r.region() == null ? null : r.region().name();
	}

	/** A label for the dress: the dressing's name and the region worn ("burgher · limewashed / lumen"). */
	public String dressing() {
		Style r = this.style.worn();
		String name = this.style.dressingName(this.variant);
		if (r == null || r.region() == null) {
			return name;
		}
		int wv = this.style.wornVariant();
		return name + " / " + r.region().name() + (wv >= 0 ? " " + r.dressingName(wv) : "");
	}

	/** Whether the building wears its town's dressing (see {@link #pinToTown()}). */
	public boolean pinned() {
		return this.pinned;
	}

	/**
	 * Wears the town's dressing instead of the building's own: the variant every pinned plot of the settlement wears and
	 * per-building materials chosen by the settlement's seed, so a town's walls, towers and gates agree, roof for roof.
	 * Defence archetypes are pinned unless the param {@code pin} is {@code none}; any plot is with {@code pin=town}; an
	 * archetype may ask for it by calling this first thing in {@code draw}. Does nothing once a material has been taken or
	 * when the param {@code variant} forces one. Returns whether the building is pinned.
	 */
	public boolean pinToTown() {
		if (this.pinned || !this.mats.isEmpty() || this.params.contains("variant")) {
			return this.pinned;
		}
		this.pinned = true;
		this.variant = townVariant(this.style.root());
		return true;
	}

	/**
	 * The settlement's seed for what it chooses once (its walls' dress): the param {@code town_seed} when the planner
	 * gives one, else one per culture.
	 */
	public long townSeed() {
		return townSeed(this.style.root());
	}

	private long townSeed(Style root) {
		Culture c = root.culture();
		return this.params.getLongOr("town_seed", 0xF0A7L + (c == null ? 0 : c.ordinal()) * 131L);
	}

	/**
	 * The dressing the settlement's pinned works wear (the param {@code town_variant} forces it): drawn from the
	 * {@code town_seed} when the planner gives one, else the culture's most likely dress for its works (a temperate town
	 * without a seed walls itself in Bohemian rubble, not in whichever manner a fixed seed happens to land on).
	 */
	public int townVariant() {
		return townVariant(this.style.root());
	}

	private int townVariant(Style root) {
		if (this.params.contains("town_variant")) {
			return this.params.getIntOr("town_variant", -1);
		}
		StyleContext c = this.context;
		StyleContext town = new StyleContext(c.culture(), "defense/wall", ArchetypeCategory.DEFENSE, Set.of("fort", "public"), c.tier(), Station.OFFICIAL,
			false, null, null, Set.of(), c.region());
		if (this.params.contains("town_seed")) {
			return root.pickVariant(Noise.hash(townSeed(root), 0x7A1A), town);
		}
		int best = -1;
		double bestW = dev.voidmc.hearth.build.style.When.holds(root.baseWhen(), town) ? root.baseWeight() : 0;
		double[] w = root.weights(town);
		for (int i = 0; i < w.length; i++) {
			if (w[i] > bestW + 1e-9) {
				bestW = w[i];
				best = i;
			}
		}
		return best;
	}

	/** A role as the settlement's pinned works wear it (its town dressing, chosen and mixed by the town's seed). */
	public Mat townMat(Role role) {
		return mat(role, townVariant(), Noise.hash(townSeed(), 0xA11 + role.ordinal()));
	}

	public Site site() {
		return this.site;
	}

	public LocalFrame frame() {
		return this.frame;
	}

	/** Width along the street (local x runs 0 .. frontage-1). */
	public int frontage() {
		return this.frame.frontage();
	}

	/** Depth from the street (local z runs 0 .. depth-1). */
	public int depth() {
		return this.frame.depth();
	}

	/** The highest local y anything may occupy is {@code maxHeight() - 1}. */
	public int maxHeight() {
		return this.spec.maxHeight();
	}

	public int tier() {
		return this.tier;
	}

	public long seed() {
		return this.spec.seed();
	}

	/** The draft's random: seeded from the spec, so the same choices come out every time. Draw in a fixed order. */
	public RandomSource random() {
		return this.random;
	}

	public boolean chance(double p) {
		return p > 0 && (p >= 1 || this.random.nextDouble() < p);
	}

	/** A uniform int in [lo, hi] inclusive. */
	public int between(int lo, int hi) {
		return hi <= lo ? lo : lo + this.random.nextInt(hi - lo + 1);
	}

	@SafeVarargs
	public final <T> T pick(T... options) {
		return options[this.random.nextInt(options.length)];
	}

	/**
	 * Whether the local-left side (the viewer's left, standing in the street facing the front) shares a wall with a
	 * neighbour. The plot params name the <b>building's own</b> sides, looking out of its front door (ARCHITECTURE.md
	 * §6.1), so the viewer's left is the building's right: always read party walls through these two methods.
	 */
	public boolean partyLocalLeft() {
		return param("party_right", false);
	}

	/** Whether the local-right side (the viewer's right) is a party wall; see {@link #partyLocalLeft()}. */
	public boolean partyLocalRight() {
		return param("party_left", false);
	}

	/** An integer param (see the archetype's Javadoc for its keys). */
	public int param(String key, int fallback) {
		return this.params.getIntOr(key, fallback);
	}

	public boolean param(String key, boolean fallback) {
		return this.params.getBooleanOr(key, fallback);
	}

	public String param(String key, String fallback) {
		return this.params.getStringOr(key, fallback);
	}

	public boolean hasParam(String key) {
		return this.params.contains(key);
	}

	public CompoundTag params() {
		return this.params.copy();
	}

	/**
	 * A style role bound to this draft (mixes by position, seeded per building and role). The palette is the dressed
	 * style's for the building's variant at its tier: sub-style, region, local materials and station all resolved there.
	 */
	public Mat mat(Role role) {
		return this.mats.computeIfAbsent(role, r -> {
			long seed = Noise.hash(this.pinned ? townSeed() : this.spec.seed(), 0xA11 + r.ordinal());
			// a thatched roof is laid in the thatch, its ridge too
			Role source = (r == Role.ROOF || r == Role.ROOF_TRIM) && habits().roof().thatch() ? Role.THATCH : r;
			var palette = this.style.palette(source, this.tier, this.variant);
			return new Mat(this, r, r.perBuilding() ? palette.chooseOne(seed) : palette, seed);
		});
	}

	/**
	 * A style role in a given variant (-1 the base dressing) chosen and mixed by the given seed rather than this
	 * building's: for works raised in pieces by several plots that must match one another (a town wall's stretches, its
	 * towers and its gates). Not cached.
	 */
	public Mat mat(Role role, int variant, long seed) {
		Role source = (role == Role.ROOF || role == Role.ROOF_TRIM) && this.style.habits(variant).roof().thatch() ? Role.THATCH : role;
		var palette = this.style.palette(source, this.tier, variant);
		return new Mat(this, role, role.perBuilding() ? palette.chooseOne(seed) : palette, seed);
	}

	/**
	 * Declares the main body of the building (local rectangle). Used to tell which way a wall faces when mixing materials
	 * (moss on the north side). Defaults to the whole footprint.
	 */
	public void body(int x0, int z0, int x1, int z1) {
		this.bodyX0 = Math.min(x0, x1);
		this.bodyZ0 = Math.min(z0, z1);
		this.bodyX1 = Math.max(x0, x1);
		this.bodyZ1 = Math.max(z0, z1);
	}

	/** The main body declared with {@link #body} as {x0, z0, x1, z1} (the whole footprint when none was declared). */
	public int[] bodyRect() {
		return new int[]{this.bodyX0, this.bodyZ0, this.bodyX1, this.bodyZ1};
	}

	double lowness(int x, int y, int z) {
		int above = y - this.site.ground(x, z);
		return Math.clamp(1.0 - above / 2.5, 0, 1);
	}

	double highness(int y) {
		int top = this.eaveY != Integer.MIN_VALUE ? this.eaveY : 8;
		return Math.clamp((y - (top - 3)) / 3.0, 0, 1);
	}

	double northness(int x, int z) {
		double cx = (this.bodyX0 + this.bodyX1) / 2.0;
		double cz = (this.bodyZ0 + this.bodyZ1) / 2.0;
		double hx = Math.max(1, (this.bodyX1 - this.bodyX0) / 2.0);
		double hz = Math.max(1, (this.bodyZ1 - this.bodyZ0) / 2.0);
		Direction out = Dirs.nearest((x - cx) / hx, (z - cz) / hz);
		return this.frame.toWorld(out) == Direction.NORTH ? 1 : 0;
	}

	void noteMissing(Role role, dev.voidmc.hearth.build.style.Form form) {
		synchronized (this.missing) {
			this.missing.add(role.key() + ":" + form.name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	/** Role/form pairs a component asked for that the style's materials could not cut (fell back to full blocks). */
	public Set<String> missingForms() {
		return Collections.unmodifiableSet(this.missing);
	}

	// ---------------------------------------------------------------------------------------------------- stages

	/**
	 * Places everything until the returned scope is closed in this stage:
	 * <pre>
	 *   try (var s = d.stage(StageKind.ROOF)) { ... }
	 * </pre>
	 */
	public StageScope stage(StageKind kind) {
		StageKind previous = this.stage;
		this.stage = kind;
		return () -> this.stage = previous;
	}

	/** Runs a block of drawing in a stage. */
	public void inStage(StageKind kind, Runnable body) {
		try (StageScope s = stage(kind)) {
			body.run();
		}
	}

	public @Nullable StageKind currentStage() {
		return this.stage;
	}

	/**
	 * Places everything until the returned scope is closed in campaign {@code n} (n ≥ 0) of a blueprint <b>built in
	 * campaigns</b>: sections raised or worked one after another, each a whole run of stages, so the work half done
	 * looks like what it is and no stage asks for more goods than a town can store. A great building raised over months
	 * is built so: a castle's terraces and keep (campaign 0), its inner curtain, its towers, its palace... An excavation
	 * worked for weeks is too: a quarry's opening works (campaign 0), then bench 1's cutting, bench 2's...; a mine's
	 * galleries and a shaft's lifts one by one (docs/LIVING_WORLD.md §8.5).
	 * <p>
	 * The compiler emits the campaigns in order, each with its own stages in {@link StageKind} order. Each campaign must
	 * stand on the ground or on what earlier campaigns built. A placement made outside any campaign scope keeps the
	 * campaign of the cell it replaces; a cell never drawn in one (what the lighting pass adds, a fixture hung from a
	 * gallery's cap or a hall's ceiling) goes with the latest campaign among the cells its fixture touches, else with
	 * campaign 0. A draft that never opens a campaign compiles exactly as before.
	 */
	public StageScope campaign(int n) {
		if (n < 0) {
			throw new IllegalArgumentException("campaign " + n);
		}
		int previous = this.campaign;
		this.campaign = n;
		return () -> this.campaign = previous;
	}

	/** The campaign a cell was drawn in, or -1 (not drawn, or drawn outside any campaign scope). */
	public int campaign(int x, int y, int z) {
		return this.campaigns.get(key(x, y, z));
	}

	/** Whether any placement was made in a campaign scope (the blueprint is built in campaigns). */
	public boolean inCampaigns() {
		return !this.campaigns.isEmpty();
	}

	/**
	 * Gives everything placed until the returned scope is closed an order key: in the {@code CLEAR} and
	 * {@code EARTHWORK} stages steps are made by key first (lowest first), and only within one key by the usual rule
	 * (cuts top-down before fills bottom-up, outward from the entrance). Draw an excavation's cuts slice by slice as the
	 * face advances, with the props and rails that go in behind each slice under the next key, and a worker meets them
	 * in that order. Unkeyed steps come first. No effect on the other stages.
	 */
	public StageScope order(int key) {
		int previous = this.order;
		this.order = key;
		return () -> this.order = previous;
	}

	/** The order key a cell was drawn with, or {@code Integer.MIN_VALUE}. */
	public int order(int x, int y, int z) {
		return this.orders.get(key(x, y, z));
	}

	/** A stage scope; closing it restores the stage that was current before. */
	@FunctionalInterface
	public interface StageScope extends AutoCloseable {
		@Override
		void close();
	}

	// ---------------------------------------------------------------------------------------------------- cells

	/** Packs a local position (each coordinate within ±2^20). */
	public static long key(int x, int y, int z) {
		return ((long) (x + (1 << 20)) << 42) | ((long) (y + (1 << 20)) << 21) | (z + (1 << 20));
	}

	public static int keyX(long k) {
		return (int) (k >>> 42) - (1 << 20);
	}

	public static int keyY(long k) {
		return (int) ((k >>> 21) & 0x1FFFFF) - (1 << 20);
	}

	public static int keyZ(long k) {
		return (int) (k & 0x1FFFFF) - (1 << 20);
	}

	/** Places a state (null removes nothing and places nothing). */
	public void set(int x, int y, int z, @Nullable BlockState state) {
		if (state != null) {
			put(x, y, z, state, null, null);
		}
	}

	public void set(int x, int y, int z, @Nullable BlockState state, @Nullable CompoundTag blockEntity) {
		if (state != null) {
			put(x, y, z, state, blockEntity, null);
		}
	}

	public void set(int x, int y, int z, Paint paint) {
		BlockState s = paint.at(x, y, z);
		if (s != null) {
			put(x, y, z, s, null, paint.stage());
		}
	}

	public void set(int x, int y, int z, Block block) {
		put(x, y, z, block.defaultBlockState(), null, null);
	}

	/** Places only where nothing has been placed yet. */
	public void setIfAbsent(int x, int y, int z, Paint paint) {
		if (!this.cells.containsKey(key(x, y, z))) {
			set(x, y, z, paint);
		}
	}

	/** Places only where nothing, or air, has been placed. */
	public void setIfAir(int x, int y, int z, Paint paint) {
		Cell c = this.cells.get(key(x, y, z));
		if (c == null || c.state.isAir()) {
			set(x, y, z, paint);
		}
	}

	public void air(int x, int y, int z) {
		put(x, y, z, Blocks.AIR.defaultBlockState(), null, null);
	}

	/** Forgets a placement (the world is left as it is there). */
	public void remove(int x, int y, int z) {
		long k = key(x, y, z);
		this.cells.remove(k);
		this.campaigns.remove(k);
		this.orders.remove(k);
	}

	/**
	 * Whether a local position may be built on: inside the footprint and under the plot's height, or one row out over
	 * the street at least three blocks up (eaves, signs, balconies). Placements elsewhere are dropped and counted.
	 */
	public boolean allowed(int x, int y, int z) {
		if (y >= maxHeight() || x < 0 || x >= frontage() || z >= depth() || z < -1) {
			return false;
		}
		return z >= 0 || y >= 3;
	}

	/** Placements dropped because they fell outside the plot (see {@link #allowed}); a generator should keep this at 0. */
	/**
	 * Marks a column as drained: water standing there is pumped out behind a dyke, so nothing built in it is
	 * waterlogged. The drafter must place air (or blocks) over every water cell it wants dry.
	 */
	public void drain(int x, int z) {
		this.drained.add(key(x, 0, z));
	}

	public boolean drained(int x, int z) {
		return this.drained.contains(key(x, 0, z));
	}

	public int clipped() {
		return this.clipped;
	}

	/** The first few placements dropped for falling outside the plot, as "x,y,z state (stage)" (for debugging). */
	public List<String> clippedSample() {
		return Collections.unmodifiableList(this.clippedSample);
	}

	private final List<String> clippedSample = new ArrayList<>();

	private void put(int x, int y, int z, BlockState state, @Nullable CompoundTag tag, @Nullable StageKind paintStage) {
		if (!allowed(x, y, z)) {
			this.clipped++;
			if (this.clippedSample.size() < 6) {
				this.clippedSample.add(x + "," + y + "," + z + " " + state + " (" + (this.stage != null ? this.stage : paintStage) + ")");
			}
			return;
		}
		StageKind s = this.stage != null ? this.stage : paintStage != null ? paintStage : guessStage(x, y, z, state);
		long k = key(x, y, z);
		this.cells.put(k, new Cell(state, tag == null ? null : tag.copy(), s, this.sequence++));
		// a campaign or an order key belongs to the position: a placement outside those scopes keeps what the cell had
		if (this.campaign >= 0) {
			this.campaigns.put(k, this.campaign);
		}
		if (this.order != Integer.MIN_VALUE) {
			this.orders.put(k, this.order);
		}
	}

	private StageKind guessStage(int x, int y, int z, BlockState s) {
		Block b = s.getBlock();
		if (s.isAir()) {
			return y < this.site.ground(x, z) ? StageKind.EARTHWORK : StageKind.CLEAR;
		}
		if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof IronBarsBlock || b == Blocks.GLASS) {
			return StageKind.OPENINGS;
		}
		if (b instanceof VegetationBlock || b instanceof FlowerBlock || b instanceof CropBlock || b instanceof DoublePlantBlock || b instanceof LeavesBlock
			|| b == Blocks.DIRT_PATH || b == Blocks.FARMLAND) {
			return StageKind.EXTERIOR;
		}
		if (b instanceof RotatedPillarBlock) {
			return StageKind.FRAME;
		}
		if (!s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
			return StageKind.INTERIOR;
		}
		return StageKind.WALLS;
	}

	public @Nullable BlockState get(int x, int y, int z) {
		Cell c = this.cells.get(key(x, y, z));
		return c == null ? null : c.state;
	}

	public @Nullable Cell cell(int x, int y, int z) {
		return this.cells.get(key(x, y, z));
	}

	public boolean isAbsent(int x, int y, int z) {
		return !this.cells.containsKey(key(x, y, z));
	}

	/** Nothing placed, or air placed. */
	public boolean isEmpty(int x, int y, int z) {
		Cell c = this.cells.get(key(x, y, z));
		return c == null || c.state.isAir();
	}

	/** A full, solid cube has been placed here. */
	public boolean isSolid(int x, int y, int z) {
		Cell c = this.cells.get(key(x, y, z));
		return c != null && c.state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	/** Every placement, keyed by {@link #key}. Read-only view for the compiler and checks. */
	public Long2ObjectMap<Cell> cells() {
		return it.unimi.dsi.fastutil.longs.Long2ObjectMaps.unmodifiable(this.cells);
	}

	public int size() {
		return this.cells.size();
	}

	/** Visits every placement (unordered). */
	public void forEach(CellVisitor visitor) {
		// the fast iterator reuses one entry (no allocation per cell); the order is the map's, as before
		var it = this.cells.long2ObjectEntrySet().fastIterator();
		while (it.hasNext()) {
			Long2ObjectMap.Entry<Cell> e = it.next();
			long k = e.getLongKey();
			visitor.visit(keyX(k), keyY(k), keyZ(k), e.getValue());
		}
	}

	/** The keys ({@link #key}) of every drawn cell, in {@link #forEach}'s order: a snapshot to visit while drawing. */
	public long[] keys() {
		return this.cells.keySet().toLongArray();
	}

	@FunctionalInterface
	public interface CellVisitor {
		void visit(int x, int y, int z, Cell cell);
	}

	/** Replaces the state of an existing cell, keeping its stage (used by shape resolution). */
	public void restate(int x, int y, int z, BlockState state) {
		long k = key(x, y, z);
		Cell c = this.cells.get(k);
		if (c != null) {
			this.cells.put(k, new Cell(state, c.tag, c.stage, c.sequence));
		}
	}

	/**
	 * Restates an existing cell, or places a new one in the stage of the cell below it (the upper half of a door goes
	 * with its lower half).
	 */
	public void restateOrSet(int x, int y, int z, BlockState state) {
		long k = key(x, y, z);
		Cell c = this.cells.get(k);
		if (c != null) {
			this.cells.put(k, new Cell(state, c.tag, c.stage, c.sequence));
			return;
		}
		Cell below = this.cells.get(key(x, y - 1, z));
		StageKind s = below != null ? below.stage : guessStage(x, y, z, state);
		this.cells.put(k, new Cell(state, null, s, this.sequence++));
		// and in the campaign open, else in its lower half's
		int camp = this.campaign >= 0 ? this.campaign : below != null ? campaign(x, y - 1, z) : -1;
		if (camp >= 0) {
			this.campaigns.put(k, camp);
		}
	}

	/**
	 * One placement.
	 *
	 * @param sequence order of placement (later wins); kept so equal sort keys stay in drawing order
	 */
	public record Cell(BlockState state, @Nullable CompoundTag tag, StageKind stage, int sequence) {
	}

	// ---------------------------------------------------------------------------------------------------- shapes

	/** Fills the box between two corners (inclusive, any order). */
	public void fill(int x0, int y0, int z0, int x1, int y1, int z1, Paint paint) {
		for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					set(x, y, z, paint);
				}
			}
		}
	}

	public void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		fill(x0, y0, z0, x1, y1, z1, Paint.of(state));
	}

	/** Fills only positions nothing has been placed at. */
	public void fillIfAbsent(int x0, int y0, int z0, int x1, int y1, int z1, Paint paint) {
		for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					setIfAbsent(x, y, z, paint);
				}
			}
		}
	}

	/** Air in a box (carving an interior), only where nothing else has been placed. */
	public void carve(int x0, int y0, int z0, int x1, int y1, int z1) {
		fillIfAbsent(x0, y0, z0, x1, y1, z1, Paint.of(Blocks.AIR.defaultBlockState()).inStage(this.stage != null ? this.stage : StageKind.WALLS));
	}

	/** Air in a box, replacing whatever was placed there. */
	public void clearBox(int x0, int y0, int z0, int x1, int y1, int z1) {
		fill(x0, y0, z0, x1, y1, z1, Paint.of(Blocks.AIR.defaultBlockState()));
	}

	/** The four walls of a box (its vertical faces), optionally filling the inside. */
	public void hollowBox(int x0, int y0, int z0, int x1, int y1, int z1, Paint walls, @Nullable Paint inside) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
			for (int z = az; z <= bz; z++) {
				for (int x = ax; x <= bx; x++) {
					boolean edge = x == ax || x == bx || z == az || z == bz;
					if (edge) {
						set(x, y, z, walls);
					} else if (inside != null) {
						set(x, y, z, inside);
					}
				}
			}
		}
	}

	/** The ring of a rectangle at one height. */
	public void outline(int x0, int z0, int x1, int z1, int y, Paint paint) {
		for (int[] c : ring(x0, z0, x1, z1)) {
			set(c[0], y, c[1], paint);
		}
	}

	/** The cells of a rectangle's ring, clockwise from the front-left corner (each as {x, z}). */
	public static List<int[]> ring(int x0, int z0, int x1, int z1) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		List<int[]> out = new ArrayList<>();
		if (ax == bx || az == bz) {
			for (int z = az; z <= bz; z++) {
				for (int x = ax; x <= bx; x++) {
					out.add(new int[]{x, z});
				}
			}
			return out;
		}
		for (int x = ax; x <= bx; x++) {
			out.add(new int[]{x, az});
		}
		for (int z = az + 1; z <= bz; z++) {
			out.add(new int[]{bx, z});
		}
		for (int x = bx - 1; x >= ax; x--) {
			out.add(new int[]{x, bz});
		}
		for (int z = bz - 1; z > az; z--) {
			out.add(new int[]{ax, z});
		}
		return out;
	}

	public void column(int x, int z, int y0, int y1, Paint paint) {
		for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
			set(x, y, z, paint);
		}
	}

	/** A 3D line between two points (inclusive), each cell placed once. */
	public void line(int x0, int y0, int z0, int x1, int y1, int z1, Paint paint) {
		int n = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
		for (int i = 0; i <= n; i++) {
			double t = n == 0 ? 0 : (double) i / n;
			set((int) Math.round(x0 + (x1 - x0) * t), (int) Math.round(y0 + (y1 - y0) * t), (int) Math.round(z0 + (z1 - z0) * t), paint);
		}
	}

	/**
	 * An ellipse at height y centred on (cx, cz) (half-block centres allowed), filled or as a 4-connected ring one block
	 * wide (round towers, curbs, round houses).
	 */
	public void ellipse(double cx, double cz, double rx, double rz, int y, boolean filled, Paint paint) {
		for (int[] c : ellipseCells(cx, cz, rx, rz, filled)) {
			set(c[0], y, c[1], paint);
		}
	}

	/** The cells of an ellipse (see {@link #ellipse}). */
	public static List<int[]> ellipseCells(double cx, double cz, double rx, double rz, boolean filled) {
		List<int[]> out = new ArrayList<>();
		int x0 = (int) Math.floor(cx - rx);
		int x1 = (int) Math.ceil(cx + rx);
		int z0 = (int) Math.floor(cz - rz);
		int z1 = (int) Math.ceil(cz + rz);
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (!insideEllipse(x, z, cx, cz, rx, rz)) {
					continue;
				}
				if (filled) {
					out.add(new int[]{x, z});
					continue;
				}
				boolean edge = !insideEllipse(x + 1, z, cx, cz, rx, rz) || !insideEllipse(x - 1, z, cx, cz, rx, rz)
					|| !insideEllipse(x, z + 1, cx, cz, rx, rz) || !insideEllipse(x, z - 1, cx, cz, rx, rz);
				if (edge) {
					out.add(new int[]{x, z});
				}
			}
		}
		return out;
	}

	public static boolean insideEllipse(int x, int z, double cx, double cz, double rx, double rz) {
		double dx = (x - cx) / (rx + 0.35);
		double dz = (z - cz) / (rz + 0.35);
		return dx * dx + dz * dz <= 1.0;
	}

	/** Visits every cell of a rectangle (inclusive). */
	public static void rect(int x0, int z0, int x1, int z1, Consumer<int[]> visitor) {
		for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				visitor.accept(new int[]{x, z});
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------- compound blocks

	/** Both halves of a door at (x, y..y+1, z). See {@link States#door} for {@code facing}. */
	public void door(int x, int y, int z, Block door, Direction facing, DoorHingeSide hinge) {
		set(x, y, z, States.door(door, facing, hinge, DoubleBlockHalf.LOWER));
		set(x, y + 1, z, States.door(door, facing, hinge, DoubleBlockHalf.UPPER));
	}

	/** A bed with its foot at (x, y, z) and its head one block toward {@code facing}. Returns the head position. */
	public int[] bed(int x, int y, int z, Block bed, Direction facing) {
		set(x, y, z, States.bed(bed, facing, BedPart.FOOT));
		int hx = x + Dirs.dx(facing);
		int hz = z + Dirs.dz(facing);
		set(hx, y, hz, States.bed(bed, facing, BedPart.HEAD));
		return new int[]{hx, y, hz};
	}

	/** A two-block plant (lilac, tall grass, large fern...): lower half at y, upper at y + 1. */
	public void tallPlant(int x, int y, int z, Block plant) {
		BlockState s = plant.defaultBlockState();
		if (s.hasProperty(DoublePlantBlock.HALF)) {
			set(x, y, z, s.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
			set(x, y + 1, z, s.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
		} else {
			set(x, y, z, s);
		}
	}

	// ---------------------------------------------------------------------------------------------------- what folk use

	/** A point of interest at a local position, facing a local direction (see {@link dev.voidmc.hearth.api.Poi}). */
	public void poi(PoiType type, int x, int y, int z, Direction facing, @Nullable JobType job) {
		this.pois.add(new LocalPoi(type, x, y, z, facing, job));
	}

	public void poi(PoiType type, int x, int y, int z, Direction facing) {
		poi(type, x, y, z, facing, null);
	}

	/**
	 * An entrance: the outside block in front of a door, where a person stands to go in. The first entrance declared is
	 * the main one; build order spreads outward from it.
	 */
	public void entrance(int x, int y, int z) {
		this.entrances.add(new int[]{x, y, z});
	}

	public List<int[]> entrances() {
		return Collections.unmodifiableList(this.entrances);
	}

	public List<LocalPoi> pois() {
		return Collections.unmodifiableList(this.pois);
	}

	/**
	 * Keeps only the points of interest the filter accepts: for passes over a finished drawing (an abandoned house is
	 * nobody's bed or workplace any more, {@code build.age.Decay}).
	 */
	public void retainPois(java.util.function.Predicate<LocalPoi> keep) {
		this.pois.removeIf(p -> !keep.test(p));
	}

	/**
	 * Takes in everything drawn on another draft whose plot lies inside this one with the same facing, its local origin
	 * at {@code (ox, oz)} here: cells (stage, drawing order, campaign, order key and block entity kept), points of interest, entrances, the declared
	 * body, the eave and what the style lacked. For a building drawn on part of its plot ({@code build.age.Accretion}:
	 * a house that will grow keeps a yard to grow into). Meant for an empty draft.
	 */
	public void absorb(Draft inner, int ox, int oz) {
		for (Long2ObjectMap.Entry<Cell> e : inner.cells.long2ObjectEntrySet()) {
			long k = e.getLongKey();
			int x = keyX(k) + ox;
			int y = keyY(k);
			int z = keyZ(k) + oz;
			if (!allowed(x, y, z)) {
				this.clipped++;
				continue;
			}
			Cell c = e.getValue();
			long nk = key(x, y, z);
			this.cells.put(nk, new Cell(c.state, c.tag, c.stage, this.sequence + c.sequence));
			int campaign = inner.campaigns.get(k);
			if (campaign >= 0) {
				this.campaigns.put(nk, campaign);
			}
			int order = inner.orders.get(k);
			if (order != Integer.MIN_VALUE) {
				this.orders.put(nk, order);
			}
		}
		this.sequence += inner.sequence;
		for (LocalPoi p : inner.pois) {
			this.pois.add(new LocalPoi(p.type(), p.x() + ox, p.y(), p.z() + oz, p.facing(), p.job()));
		}
		for (int[] e : inner.entrances) {
			this.entrances.add(new int[]{e[0] + ox, e[1], e[2] + oz});
		}
		boolean declared = !(inner.bodyX0 == 0 && inner.bodyZ0 == 0 && inner.bodyX1 == inner.frontage() - 1 && inner.bodyZ1 == inner.depth() - 1);
		if (declared) {
			body(inner.bodyX0 + ox, inner.bodyZ0 + oz, inner.bodyX1 + ox, inner.bodyZ1 + oz);
		}
		if (inner.eaveY != Integer.MIN_VALUE) {
			this.eaveY = inner.eaveY;
		}
		for (long k : inner.drained) {
			this.drained.add(key(keyX(k) + ox, 0, keyZ(k) + oz));
		}
		this.missing.addAll(inner.missing);
		this.clipped += inner.clipped;
		this.lit |= inner.lit;
	}

	/** The y where the walls meet the roof (for the far-away silhouette and the material mix). */
	public void eave(int y) {
		this.eaveY = y;
	}

	public int eaveY() {
		return this.eaveY;
	}

	/**
	 * Marks the draft as lit by the lighting pass, which runs once per draft (the compiler runs it; a test or a preview
	 * that ran it first is not lit twice). Returns whether it was already lit.
	 */
	public boolean markLit() {
		boolean was = this.lit;
		this.lit = true;
		return was;
	}

	/** A point of interest in local coordinates. */
	public record LocalPoi(PoiType type, int x, int y, int z, Direction facing, @Nullable JobType job) {
	}

	/** Stages any placement used, in order. */
	public EnumSet<StageKind> usedStages() {
		EnumSet<StageKind> s = EnumSet.noneOf(StageKind.class);
		for (Cell c : this.cells.values()) {
			s.add(c.stage);
		}
		return s;
	}
}
