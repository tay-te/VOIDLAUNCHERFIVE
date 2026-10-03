package dev.voidmc.hearth.gametest.plan;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.BlueprintService;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.catalogue.Catalogue;
import dev.voidmc.hearth.plan.road.Polyline;
import dev.voidmc.hearth.plan.testing.BoxBlueprint;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the previews draw: the land of an area (ground, water, temperature) and, over it, the settlement as coloured
 * blocks per column. Built plots are solid; plots under way stand half built; planned plots are only staked out. In box
 * mode a building is a box of its footprint and height in its category's colours; in block mode (a real build module
 * registered) every step of its blueprint is drawn in its block's map colour.
 */
public final class Scene {
	public final int x0;
	public final int z0;
	public final int w;
	public final int d;
	public final int sea;
	final int[] ground;
	final int[] water;
	final float[] temperature;
	/** Per column, blocks above the land: packed (y + 1024) << 24 | rgb. */
	final LongArrayList[] blocks;
	/** Per column, the land's top colour where something paves or tills it (0 = natural). */
	final int[] surface;
	/** Per column, the land's walking level where it was cut or filled (Integer.MIN_VALUE = natural). */
	final int[] level;
	public int blockCount;

	public Scene(TerrainProbe terrain, int x0, int z0, int w, int d) {
		this.x0 = x0;
		this.z0 = z0;
		this.w = w;
		this.d = d;
		this.sea = terrain.seaLevel();
		int n = w * d;
		this.ground = new int[n];
		this.water = new int[n];
		this.temperature = new float[n];
		this.blocks = new LongArrayList[n];
		this.surface = new int[n];
		this.level = new int[n];
		java.util.Arrays.fill(this.level, Integer.MIN_VALUE);
		for (int z = 0; z < d; z++) {
			for (int x = 0; x < w; x++) {
				int i = z * w + x;
				this.ground[i] = terrain.groundY(x0 + x, z0 + z);
				this.water[i] = terrain.waterY(x0 + x, z0 + z);
				this.temperature[i] = terrain.temperature(x0 + x, z0 + z);
			}
		}
	}

	private Scene(int x0, int z0, int w, int d, int sea) {
		this.x0 = x0;
		this.z0 = z0;
		this.w = w;
		this.d = d;
		this.sea = sea;
		int n = w * d;
		this.ground = new int[n];
		this.water = new int[n];
		this.temperature = new float[n];
		this.blocks = new LongArrayList[n];
		this.surface = new int[n];
		this.level = new int[n];
	}

	/**
	 * The scene turned a quarter at a time so its low side faces the isometric viewer (who looks from the +x +z
	 * corner): a town on a slope is seen from below, climbing away, not hidden behind its own hill.
	 */
	public Scene facingDownhill() {
		double best = Double.MAX_VALUE;
		int turns = 0;
		Scene s = this;
		Scene pick = this;
		for (int k = 0; k < 4; k++) {
			double near = 0;
			for (int x = 0; x < s.w; x++) {
				near += s.ground[(s.d - 1) * s.w + x];
			}
			for (int z = 0; z < s.d; z++) {
				near += s.ground[z * s.w + s.w - 1];
			}
			near /= s.w + s.d;
			if (near < best - 0.5) {
				best = near;
				pick = s;
				turns = k;
			}
			s = s.turned();
		}
		return pick;
	}

	/** The scene turned a quarter clockwise (seen from above): column (x, z) moves to (d - 1 - z, x). */
	private Scene turned() {
		Scene r = new Scene(this.x0, this.z0, this.d, this.w, this.sea);
		for (int z = 0; z < this.d; z++) {
			for (int x = 0; x < this.w; x++) {
				int from = z * this.w + x;
				int nx = this.d - 1 - z;
				int nz = x;
				int to = nz * r.w + nx;
				r.ground[to] = this.ground[from];
				r.water[to] = this.water[from];
				r.temperature[to] = this.temperature[from];
				r.blocks[to] = this.blocks[from];
				r.surface[to] = this.surface[from];
				r.level[to] = this.level[from];
			}
		}
		r.blockCount = this.blockCount;
		return r;
	}

	public boolean inside(int x, int z) {
		return x >= this.x0 && z >= this.z0 && x < this.x0 + this.w && z < this.z0 + this.d;
	}

	int index(int x, int z) {
		return (z - this.z0) * this.w + (x - this.x0);
	}

	public int groundAt(int x, int z) {
		int i = index(x, z);
		return this.level[i] != Integer.MIN_VALUE ? this.level[i] : this.ground[i];
	}

	void block(int x, int y, int z, int rgb) {
		if (!inside(x, z)) {
			return;
		}
		int i = index(x, z);
		if (this.blocks[i] == null) {
			this.blocks[i] = new LongArrayList(4);
		}
		this.blocks[i].add(((long) (y + 1024) << 24) | (rgb & 0xFFFFFF));
		this.blockCount++;
	}

	void pave(int x, int y, int z, int rgb) {
		if (!inside(x, z)) {
			return;
		}
		int i = index(x, z);
		this.surface[i] = rgb;
		this.level[i] = y + 1;
	}

	/** Adds every plot of a settlement, in box mode or (with a service) block mode. */
	public void addSettlement(Settlement s, TerrainProbe terrain, Catalogue catalogue, BlueprintService blocksFrom) {
		List<PlotView> plots = new ArrayList<>(s.plots());
		plots.sort(Comparator.comparingInt(PlotView::id));
		// ground layer first (terraces, roads, farms), then what stands on it
		for (int pass = 0; pass < 2; pass++) {
			for (PlotView p : plots) {
				boolean ground = groundLayer(p.spec());
				if (ground == (pass == 0)) {
					if (blocksFrom != null) {
						addBlueprint(p, blocksFrom, terrain);
					} else {
						addBox(p, catalogue);
					}
				}
			}
		}
	}

	private static boolean groundLayer(PlotSpec s) {
		var id = s.archetype();
		return Archetypes.isLinear(id) && !id.equals(Archetypes.WALL) && !id.equals(Archetypes.PALISADE) || id.equals(Archetypes.TERRACE)
			|| id.equals(Archetypes.PLAZA) || id.getPath().startsWith("farm/");
	}

	private void addBox(PlotView plot, Catalogue catalogue) {
		PlotSpec s = plot.spec();
		ArchetypeInfo info = catalogue.info(s.archetype());
		if (info == null) {
			return;
		}
		var id = s.archetype();
		CompoundTag p = s.params();
		if (Archetypes.isLinear(id) || id.equals(Archetypes.WALKWAY)) {
			int[] pts = p.getIntArray("points").orElse(null);
			if (pts == null || plot.state() == PlotState.PLANNED) {
				return;
			}
			Polyline.Dense dense = new Polyline(pts).dense();
			int rgb = Palette.road(p.getStringOr("kind", ""), id);
			int deck = p.getIntOr("deck_y", Integer.MIN_VALUE);
			int height = p.getIntOr("height", 0);
			boolean stairs = id.equals(Archetypes.STAIRS);
			Polyline.corridor(pts, p.getIntOr("width", 1), (x, z) -> {
				int k = dense.nearest(x, z);
				int y = dense.ys()[k];
				int c = stairs && (k & 1) == 1 ? Palette.shade(rgb, 0.82) : rgb;
				if (id.equals(Archetypes.WALKWAY)) {
					block(x, deck - 1, z, rgb);
				} else if (id.equals(Archetypes.BRIDGE) || id.equals(Archetypes.DOCK)) {
					int dy = id.equals(Archetypes.DOCK) ? y : (k == 0 || k == dense.length() - 1 ? y : deck);
					if (inside(x, z) && water[index(x, z)] != TerrainProbe.NO_WATER) {
						block(x, dy - 1, z, c);
					} else {
						pave(x, dy - 1, z, c);
					}
				} else if (height > 0) {
					int g = inside(x, z) ? groundAt(x, z) : y;
					for (int yy = g; yy < y + height; yy++) {
						block(x, yy, z, rgb);
					}
				} else {
					pave(x, y - 1, z, c);
				}
			});
			return;
		}
		int x0 = s.origin().getX();
		int z0 = s.origin().getZ();
		int x1 = x0 + s.sizeX() - 1;
		int z1 = z0 + s.sizeZ() - 1;
		int floor = s.floorY();
		int roof = Palette.roof(id, info);
		int wall = Palette.wall(info);
		if (id.equals(Archetypes.TERRACE)) {
			int lv = p.getIntOr("level", floor);
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (!inside(x, z)) {
						continue;
					}
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					int g = groundAt(x, z);
					for (int y = g; y < lv - 1; y++) {
						block(x, y, z, edge ? Palette.TERRACE_EDGE : Palette.TERRACE);
					}
					pave(x, lv - 1, z, Palette.TERRACE);
				}
			}
			return;
		}
		if (info.category() == ArchetypeCategory.FARM || id.equals(Archetypes.PLAZA) || id.equals(Archetypes.GARDEN) || id.equals(Archetypes.PARK)) {
			boolean planned = plot.state() == PlotState.PLANNED;
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (!inside(x, z)) {
						continue;
					}
					int c = roof;
					if (info.category() == ArchetypeCategory.FARM) {
						boolean rows = s.sizeX() >= s.sizeZ() ? (z - z0) % 3 == 1 : (x - x0) % 3 == 1;
						c = rows ? Palette.shade(roof, 0.78) : roof;
						if (x == x0 || x == x1 || z == z0 || z == z1) {
							c = 0x8A7A5A;
						}
					} else if (id.equals(Archetypes.PLAZA) && ((x + z) & 3) == 0) {
						c = Palette.shade(roof, 0.92);
					}
					if (planned) {
						c = Palette.lerp(c, 0x9AB070, 0.6);
					}
					int g = groundAt(x, z);
					pave(x, Math.max(g, floor) - 1, z, c);
					if (id.equals(Archetypes.ORCHARD) && !planned && (x - x0) % 3 == 1 && (z - z0) % 3 == 1) {
						block(x, floor, z, 0x3C6E2C);
						block(x, floor + 1, z, 0x3C6E2C);
					}
				}
			}
			return;
		}
		int h = plot.state() == PlotState.BUILT ? BoxBlueprint.heightFor(s, info) : plot.state() == PlotState.BUILDING ? Math.max(1, BoxBlueprint.heightFor(s, info) / 2) : 0;
		if (h == 0) {
			// staked out: a timber outline on the ground
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					if (inside(x, z) && (x == x0 || x == x1 || z == z0 || z == z1)) {
						block(x, Math.max(groundAt(x, z), floor), z, 0xC8A060);
					}
				}
			}
			return;
		}
		boolean stacked = p.getBooleanOr("stacked", false);
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (!inside(x, z)) {
					continue;
				}
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				int g = groundAt(x, z);
				if (edge && !stacked) {
					for (int y = Math.min(g, floor); y < floor; y++) {
						block(x, y, z, 0x7C786C);
					}
				}
				if (edge) {
					for (int y = floor; y < floor + h - 1; y++) {
						// a band of windows on each storey of the walls
						boolean window = (y - floor) % 4 == 2 && ((x + z) & 1) == 0 && plot.state() == PlotState.BUILT;
						block(x, y, z, window ? 0x40506A : wall);
					}
				}
				boolean ridge = s.facing().getAxis() == net.minecraft.core.Direction.Axis.Z
					? x == x0 + s.sizeX() / 2 : z == z0 + s.sizeZ() / 2;
				block(x, floor + h - 1, z, plot.state() == PlotState.BUILT ? (ridge && info.category() == ArchetypeCategory.HOUSING ? Palette.shade(roof, 0.8) : roof) : 0xC8A060);
				if (!edge && g > floor) {
					pave(x, floor - 1, z, 0x7C786C);
				}
			}
		}
	}

	private void addBlueprint(PlotView plot, BlueprintService service, TerrainProbe terrain) {
		if (plot.state() == PlotState.PLANNED) {
			return;
		}
		Blueprint bp;
		try {
			bp = service.blueprint(plot.spec(), terrain);
		} catch (RuntimeException e) {
			return;
		}
		int stages = plot.state() == PlotState.BUILT ? bp.stageCount() : Math.max(1, plot.stage());
		for (int stage = 0; stage < stages; stage++) {
			int n = bp.stepCount(stage);
			for (int i = 0; i < n; i++) {
				bp.step(stage, i, (x, y, z, state, tag) -> place(x, y, z, state));
			}
		}
	}

	private void place(int x, int y, int z, BlockState state) {
		if (!inside(x, z)) {
			return;
		}
		int i = index(x, z);
		int g = groundAt(x, z);
		if (state.isAir()) {
			if (y < g) {
				this.level[i] = y;
			}
			return;
		}
		int rgb = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
		if (y == g - 1) {
			pave(x, y, z, rgb);
		} else {
			block(x, y, z, rgb);
		}
	}
}
