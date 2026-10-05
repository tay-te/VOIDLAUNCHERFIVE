package dev.voidmc.atlas.client.ui;

import dev.voidmc.atlas.api.MapLayer;
import dev.voidmc.atlas.api.Marker;
import dev.voidmc.atlas.api.MarkerQuery;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.AtlasSettings;
import dev.voidmc.atlas.client.data.DimensionData;
import dev.voidmc.atlas.client.data.LayerData;
import dev.voidmc.atlas.client.render.MapRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The interactive map: a view (centre, zoom) over one layer, drawn with its markers and the player; drag to pan,
 * scroll to zoom round the cursor (smoothly, settling on whole pixels per block), click a marker to select it. Used by
 * the full map screen, and by other mods (the VOID Hub) through the API.
 */
public final class MapPanel {
	public enum Kind {
		SURFACE("Surface"), UNDERGROUND("Underground"), NETHER("Nether");

		public final String label;

		Kind(String label) {
			this.label = label;
		}
	}

	private final AtlasClient atlas;
	private final MapRenderer renderer = new MapRenderer();
	public Kind kind = Kind.SURFACE;
	private double cx;
	private double cz;
	private boolean centred;
	/** Zoom, physical pixels per block, as drawn this frame and as wanted. */
	private double zoom;
	private int zoomIndex;
	private double anchorMapX = Double.NaN;
	private double anchorMapZ;
	private float anchorSx;
	private float anchorSy;
	private boolean dragging;
	private boolean dragged;
	private double dragX;
	private double dragY;
	public @Nullable String selected;
	public @Nullable String hovered;
	private List<MarkerPainter.Placed> placed = List.of();
	private List<Marker> markers = List.of();
	private MapRenderer.@Nullable Frame frame;
	private long lastNanos;
	/** A scripted camera for the performance test: when set, it moves the view every frame. */
	public @Nullable Script script;

	/** Moves the view each frame (the performance test's pan and zoom). */
	public interface Script {
		/** Returns false when finished. */
		boolean step(MapPanel panel, double seconds);
	}

	public MapPanel(AtlasClient atlas) {
		this.atlas = atlas;
		this.zoomIndex = atlas.settings().mapZoom;
		this.zoom = AtlasSettings.ZOOMS[this.zoomIndex];
	}

	// --- what is shown ---------------------------------------------------------------------------------------------

	/** The dimension a kind shows, given where the player is. */
	public String dimensionOf(Kind kind) {
		Minecraft mc = Minecraft.getInstance();
		String here = mc.level == null ? "minecraft:overworld" : mc.level.dimension().identifier().toString();
		boolean ceiling = mc.level != null && mc.level.dimensionType().hasCeiling();
		return switch (kind) {
			case NETHER -> "minecraft:the_nether";
			case SURFACE, UNDERGROUND -> ceiling ? "minecraft:overworld" : here;
		};
	}

	/** The band the underground view shows: chosen with the depth control, or (null) the player's. */
	private @Nullable Integer band;

	/** The layer a kind shows. */
	public @Nullable LayerData layer(Kind kind) {
		return this.layerAtBand(kind, this.band(kind));
	}

	/** The band the underground (or Nether) view shows. */
	public int band(Kind kind) {
		String dim = this.dimensionOf(kind);
		return this.band != null && kind != Kind.SURFACE ? this.band : this.atlas.sliceBand(dim);
	}

	/** Shows a band underground (null: follow the player). */
	public void setBand(@Nullable Integer band) {
		this.band = band;
	}

	public boolean followsPlayer() {
		return this.band == null;
	}

	/** The layer a kind shows at a band (the band is ignored on the surface). */
	public @Nullable LayerData layerAtBand(Kind kind, int band) {
		String dim = this.dimensionOf(kind);
		var session = this.atlas.session();
		if (session == null) {
			return null;
		}
		DimensionData data = session.dimension(dim, kind == Kind.NETHER);
		if (kind == Kind.SURFACE && !data.ceiling) {
			return data.layer(DimensionData.SURFACE);
		}
		return data.under(band);
	}

	/** The explored bands of the dimension a kind shows, lowest first. */
	public java.util.NavigableSet<Integer> bands(Kind kind) {
		var session = this.atlas.session();
		if (session == null) {
			return new java.util.TreeSet<>();
		}
		return session.dimension(this.dimensionOf(kind), kind == Kind.NETHER).bands();
	}

	/** Scale between the player's dimension and the shown one (8 between the Overworld and the Nether). */
	private double scaleTo(String dim) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return 1;
		}
		boolean hereNether = mc.level.dimension().identifier().toString().equals("minecraft:the_nether");
		boolean thereNether = dim.equals("minecraft:the_nether");
		if (hereNether == thereNether) {
			return 1;
		}
		return thereNether ? 1.0 / 8 : 8;
	}

	public void setKind(Kind kind) {
		if (kind == this.kind) {
			return;
		}
		double before = this.scaleTo(this.dimensionOf(this.kind));
		double after = this.scaleTo(this.dimensionOf(kind));
		this.cx = this.cx / before * after;
		this.cz = this.cz / before * after;
		this.kind = kind;
		this.selected = null;
		this.band = null;
	}

	public void centreOnPlayer() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p != null) {
			double s = this.scaleTo(this.dimensionOf(this.kind));
			this.cx = p.getX() * s;
			this.cz = p.getZ() * s;
			this.centred = true;
		}
	}

	public void centreOn(double x, double z) {
		this.cx = x;
		this.cz = z;
		this.centred = true;
	}

	public double centreX() {
		return this.cx;
	}

	public double centreZ() {
		return this.cz;
	}

	public double zoom() {
		return this.zoom;
	}

	public int zoomIndex() {
		return this.zoomIndex;
	}

	public void setZoom(double pixelsPerBlock) {
		this.zoom = pixelsPerBlock;
		int best = 0;
		for (int i = 0; i < AtlasSettings.ZOOMS.length; i++) {
			if (Math.abs(Math.log(AtlasSettings.ZOOMS[i] / pixelsPerBlock)) < Math.abs(Math.log(AtlasSettings.ZOOMS[best] / pixelsPerBlock))) {
				best = i;
			}
		}
		this.zoomIndex = best;
	}

	public void setZoomIndex(int index) {
		this.zoomIndex = Math.max(0, Math.min(AtlasSettings.ZOOMS.length - 1, index));
		this.zoom = AtlasSettings.ZOOMS[this.zoomIndex];
	}

	/** Steps the zoom (+1 in, -1 out), keeping the point at screen {@code (sx, sy)} still. */
	public void zoomBy(int steps, float sx, float sy) {
		int next = Math.max(0, Math.min(AtlasSettings.ZOOMS.length - 1, this.zoomIndex + steps));
		if (next == this.zoomIndex) {
			return;
		}
		if (this.frame != null) {
			this.anchorMapX = this.frame.mapX(sx, sy);
			this.anchorMapZ = this.frame.mapZ(sx, sy);
			this.anchorSx = sx;
			this.anchorSy = sy;
		}
		this.zoomIndex = next;
		this.atlas.settings().mapZoom = next;
	}

	public double targetZoom() {
		return AtlasSettings.ZOOMS[this.zoomIndex];
	}

	public MapRenderer.@Nullable Frame frame() {
		return this.frame;
	}

	public List<Marker> markers() {
		return this.markers;
	}

	public @Nullable Marker marker(String id) {
		for (Marker m : this.markers) {
			if (m.id().equals(id)) {
				return m;
			}
		}
		return null;
	}

	// --- drawing ---------------------------------------------------------------------------------------------------

	/** Draws the map into the rounded rectangle (design pixels). */
	public MapRenderer.Frame render(Ui ui, float x, float y, float w, float h, float radius, float mouseX, float mouseY, boolean interactive) {
		long now = System.nanoTime();
		double dt = this.lastNanos == 0 ? 0 : Math.min(0.1, (now - this.lastNanos) / 1e9);
		this.lastNanos = now;
		if (!this.centred) {
			this.centreOnPlayer();
		}
		if (this.script != null && !this.script.step(this, dt)) {
			this.script = null;
		}
		this.animateZoom(dt, ui, x, y, w, h);
		// the "Water & lava" chip: underground images are drawn again with or without them
		var session = this.atlas.session();
		boolean fluids = this.atlas.settings().layerVisible(MapLayer.FLUIDS);
		if (session != null && session.caveFluids() != fluids) {
			session.setCaveFluids(fluids);
			this.atlas.tiles().clear();
		}
		// the Terrain chip switches the land itself off, leaving the markers on the bare well
		LayerData layer = this.atlas.settings().layerVisible(MapLayer.TERRAIN) ? this.layer(this.kind) : null;
		ui.round(x, y, w, h, radius, Theme.MAP_WELL);
		MapRenderer.View view = new MapRenderer.View(this.cx, this.cz, this.zoom, 0);
		// day and night on the map (if wanted): the surface of a world with a sky, lit as the time of day
		net.minecraft.client.multiplayer.ClientLevel lightLevel = net.minecraft.client.Minecraft.getInstance().level;
		boolean lit = this.atlas.settings().mapLighting && this.kind == Kind.SURFACE && lightLevel != null && !lightLevel.dimensionType().hasCeiling();
		this.renderer.setTint(lit ? MapRenderer.daylight(lightLevel.getOverworldClockTime()) : 0xFFFFFF);
		MapRenderer.Frame f = this.renderer.draw(ui, this.atlas.tiles(), layer, x, y, w, h, radius, view, true);
		this.frame = f;
		// markers and the player, clipped to the map's box
		ui.flush();
		ui.g.enableScissor((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(x + w), (int) Math.ceil(y + h));
		String dim = this.dimensionOf(this.kind);
		double s = this.scaleTo(dim);
		LocalPlayer p = Minecraft.getInstance().player;
		double px = p == null ? this.cx : p.getX() * s;
		double pz = p == null ? this.cz : p.getZ() * s;
		this.markers = this.collect(f, dim, px, pz);
		this.hovered = null;
		if (interactive && f.inside(mouseX, mouseY)) {
			for (int i = this.placed.size() - 1; i >= 0; i--) {
				if (this.placed.get(i).hit(mouseX, mouseY)) {
					this.hovered = this.placed.get(i).marker().id();
					break;
				}
			}
		}
		this.placed = MarkerPainter.paint(ui, f, this.markers, this.selected, this.hovered, false, px, pz, this.zoom);
		if (p != null) {
			float sx = f.screenX(px, pz);
			float sy = f.screenY(px, pz);
			if (f.inside(sx, sy)) {
				MarkerPainter.self(ui, sx, sy, (float) Math.toRadians(p.getYRot() + 180), 44);
			}
		}
		ui.flush();
		ui.g.disableScissor();
		ui.border(x, y, w, h, radius, 0, Theme.HAIR_8);
		return f;
	}

	private void animateZoom(double dt, Ui ui, float x, float y, float w, float h) {
		double target = this.targetZoom();
		if (this.zoom != target) {
			double a = Math.log(this.zoom);
			double b = Math.log(target);
			double t = 1 - Math.exp(-dt * 14);
			double next = Math.exp(a + (b - a) * t);
			if (Math.abs(b - Math.log(next)) < 0.004 || dt == 0) {
				next = target;
			}
			this.zoom = next;
			if (!Double.isNaN(this.anchorMapX)) {
				// keep the point under the cursor still: solve for the centre that puts it there
				double k = this.zoom / ui.unit;
				this.cx = this.anchorMapX - (this.anchorSx - (x + w / 2)) / k;
				this.cz = this.anchorMapZ - (this.anchorSy - (y + h / 2)) / k;
			}
		}
		if (this.zoom == target) {
			this.anchorMapX = Double.NaN;
		}
	}

	private List<Marker> collect(MapRenderer.Frame f, String dim, double px, double pz) {
		double margin = 64 / f.k;
		double minX = f.mapX(f.x, f.y) - margin;
		double minZ = f.mapZ(f.x, f.y) - margin;
		double maxX = f.mapX(f.x + f.w, f.y + f.h) + margin;
		double maxZ = f.mapZ(f.x + f.w, f.y + f.h) + margin;
		MarkerQuery q = new MarkerQuery(dim, minX, minZ, maxX, maxZ, px, pz, MarkerQuery.Purpose.MAP);
		List<Marker> out = new ArrayList<>();
		AtlasSettings settings = this.atlas.settings();
		boolean under = this.kind == Kind.UNDERGROUND;
		int band = this.kind == Kind.SURFACE ? 0 : this.band(this.kind);
		for (Marker m : this.atlas.collect(q)) {
			if (!settings.layerVisible(m.layer()) || !m.dimension().equals(dim) || !q.contains(m.x(), m.z())) {
				continue;
			}
			if (m.style() == Marker.Style.LABEL && this.kind != Kind.SURFACE) {
				// a cave's name only on its own slice
				dev.voidmc.atlas.api.Place p = AtlasClient.get().placeOf(m);
				if (p == null || dev.voidmc.atlas.client.data.DimensionData.bandOf(p.y()) != band) {
					continue;
				}
			}
			if (shows(m, this.kind == Kind.NETHER ? null : under, this.zoom)) {
				out.add(m);
			}
		}
		return out;
	}

	/**
	 * Whether a marker shows at a zoom (physical pixels per block) on a view, by the design's thresholds: from 1:8 out
	 * only major tiles (and the larger names); from 1:4 the minor tiles and the sighted "?"s too; names of the land up
	 * to 2:1 (they fade out from 1:1). Underground places and the caves' names only on the underground view, the
	 * land's names and surface places only on the surface; {@code underground} null: a view with a roof (the Nether), where all show.
	 */
	public static boolean shows(Marker m, @Nullable Boolean underground, double zoom) {
		if (m.style() == Marker.Style.LABEL) {
			if (zoom >= 2) {
				return false;
			}
			// the land's names on the surface, the caves' names (cave biomes seen down there) underground
			dev.voidmc.atlas.api.Place p = AtlasClient.get().placeOf(m);
			boolean cave = p != null && p.underground();
			return underground == null ? cave : cave == underground;
		}
		if (m.style() == Marker.Style.STRUCTURE && zoom < 0.25
			&& (m.tier() == dev.voidmc.atlas.api.Tier.MINOR || m.glyph() == dev.voidmc.atlas.api.MarkerGlyph.UNKNOWN)) {
			return false;
		}
		if (underground == null) {
			return true;
		}
		dev.voidmc.atlas.api.Place p = AtlasClient.get().placeOf(m);
		if (p == null) {
			return true;
		}
		if (underground) {
			return p.underground() || p.kind().equals(dev.voidmc.atlas.api.Place.CAVE_MOUTH);
		}
		return !p.underground();
	}

	// --- input -----------------------------------------------------------------------------------------------------

	/** The marker under the cursor, if any. */
	public @Nullable Marker markerAt(float x, float y) {
		for (int i = this.placed.size() - 1; i >= 0; i--) {
			MarkerPainter.Placed p = this.placed.get(i);
			if (p.hit(x, y)) {
				return p.marker();
			}
		}
		return null;
	}

	public boolean inside(float x, float y) {
		return this.frame != null && this.frame.inside(x, y);
	}

	public void press(float x, float y) {
		this.dragging = true;
		this.dragged = false;
		this.dragX = x;
		this.dragY = y;
	}

	public void drag(float x, float y) {
		if (!this.dragging || this.frame == null) {
			return;
		}
		double dx = x - this.dragX;
		double dy = y - this.dragY;
		if (!this.dragged && Math.hypot(dx, dy) < 3) {
			return;
		}
		this.dragged = true;
		this.cx -= dx / this.frame.k;
		this.cz -= dy / this.frame.k;
		this.dragX = x;
		this.dragY = y;
		this.anchorMapX = Double.NaN;
	}

	/** Ends a press; returns true if it was a drag (not a click). */
	public boolean release() {
		boolean was = this.dragged;
		this.dragging = false;
		this.dragged = false;
		return was;
	}

	public boolean isDragging() {
		return this.dragging && this.dragged;
	}

	public void pan(double dxBlocks, double dzBlocks) {
		this.cx += dxBlocks;
		this.cz += dzBlocks;
	}
}
