package dev.voidmc.atlas.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.voidmc.atlas.api.Waypoint;
import dev.voidmc.atlas.client.AtlasClient;
import dev.voidmc.atlas.client.AtlasSettings;
import dev.voidmc.atlas.client.Perf;
import dev.voidmc.atlas.client.data.MapSession;
import dev.voidmc.atlas.core.WaypointStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The waypoints in the world: a thin beam of light over each and a label with its name and distance, through walls.
 * Cheap by construction: the list of the dimension's waypoints is made again only when the waypoints change, labels
 * only when their whole-metre distance does, at most {@value #MAX_BEAMS} beams (the nearest), none behind the player,
 * and each fades out towards the beam distance in the settings (and right next to it, so it never blinds). A waypoint
 * beyond the render distance is drawn at its edge, in its direction, so the far ones still show the way. Hidden with
 * the HUD (F1).
 */
public final class WaypointBeams {
	public static final int MAX_BEAMS = 32;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final Vec3 ORIGIN = Vec3.ZERO;

	private final AtlasClient atlas;
	private List<Waypoint> shown = List.of();
	private long shownVersion = -1;
	private String shownDimension = "";
	private MapSession shownSession;
	private final Map<String, Label> labels = new HashMap<>();
	// per frame, reused
	private final double[] dist = new double[256];
	private final int[] order = new int[256];

	private record Label(int metres, Component text) {
	}

	public WaypointBeams(AtlasClient atlas) {
		this.atlas = atlas;
	}

	/** The world's submits are being collected (render thread). */
	public void collect(LevelRenderContext ctx) {
		long t0 = System.nanoTime();
		try {
			this.submit(ctx);
		} finally {
			Perf.beams(System.nanoTime() - t0);
		}
	}

	private void submit(LevelRenderContext ctx) {
		AtlasSettings settings = this.atlas.settings();
		Minecraft mc = Minecraft.getInstance();
		MapSession session = this.atlas.session();
		if (!settings.waypointBeams || session == null || mc.player == null || mc.gui.hud.isHidden()) {
			return;
		}
		String dim = mc.player.level().dimension().identifier().toString();
		WaypointStore store = session.waypoints;
		long version = store.version();
		if (version != this.shownVersion || !dim.equals(this.shownDimension) || session != this.shownSession) {
			List<Waypoint> list = new ArrayList<>();
			for (Waypoint w : store.in(dim)) {
				if (w.visible()) {
					list.add(w);
				}
			}
			this.shown = list.size() > this.dist.length ? list.subList(0, this.dist.length) : list;
			this.shownVersion = version;
			this.shownDimension = dim;
			this.shownSession = session;
			this.labels.keySet().retainAll(list.stream().map(Waypoint::id).toList());
		}
		if (this.shown.isEmpty()) {
			return;
		}
		CameraRenderState camera = ctx.levelState().cameraRenderState;
		Vec3 cam = camera.pos;
		double yaw = Math.toRadians(camera.yRot);
		// the way the camera looks, on the ground
		double lookX = -Math.sin(yaw);
		double lookZ = Math.cos(yaw);
		double maxD = settings.beamDistance <= 0 ? Double.MAX_VALUE : settings.beamDistance;
		int n = 0;
		for (int i = 0; i < this.shown.size(); i++) {
			Waypoint w = this.shown.get(i);
			double dx = w.x() + 0.5 - cam.x;
			double dz = w.z() + 0.5 - cam.z;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > maxD) {
				continue;
			}
			// behind the player (with a margin for the field of view and for ones close by): not drawn
			if (d > 16 && (dx * lookX + dz * lookZ) / d < -0.35) {
				continue;
			}
			this.dist[i] = d;
			this.order[n++] = i;
		}
		if (n == 0) {
			return;
		}
		// the nearest MAX_BEAMS (insertion sort: n is small)
		for (int a = 1; a < n; a++) {
			int v = this.order[a];
			int b = a - 1;
			while (b >= 0 && this.dist[this.order[b]] > this.dist[v]) {
				this.order[b + 1] = this.order[b];
				b--;
			}
			this.order[b + 1] = v;
		}
		n = Math.min(n, MAX_BEAMS);
		double edge = Math.max(32, mc.options.getEffectiveRenderDistance() * 16 - 24);
		PoseStack pose = ctx.poseStack();
		SubmitNodeCollector out = ctx.submitNodeCollector();
		float time = (float) Math.floorMod(ctx.levelState().gameTime, 40L) + ctx.levelState().worldPartialTicks;
		for (int k = 0; k < n; k++) {
			Waypoint w = this.shown.get(this.order[k]);
			double d = this.dist[this.order[k]];
			double dx = w.x() + 0.5 - cam.x;
			double dz = w.z() + 0.5 - cam.z;
			double bx = dx;
			double bz = dz;
			double by = w.y() - cam.y;
			if (d > edge) {
				// beyond what the world draws: at its edge, in the waypoint's direction
				bx = dx / d * edge;
				bz = dz / d * edge;
				by = Math.min(by, 0) - 16;
			}
			float fade = (float) Math.min(1, Math.min((d - 2) / 6, (maxD - d) / (maxD * 0.2)));
			if (fade <= 0.02F) {
				continue;
			}
			int rgb = w.rgb();
			int alpha = Math.round(fade * 160);
			pose.pushPose();
			pose.translate(bx - 0.5, by, bz - 0.5);
			BeaconRenderer.submitBeaconBeam(pose, out, BeaconRenderer.BEAM_LOCATION, 1.0F, time, 0, 1024, alpha << 24 | rgb, 0.08F, 0.14F);
			pose.popPose();
			// the label: above the spot, the same size on screen at any distance
			Label label = this.label(w, d);
			double lx = bx;
			double lz = bz;
			double ly = (d > edge ? 0 : by) + 2.2;
			float scale = (float) Math.max(1, Math.min(d, edge) / 10);
			pose.pushPose();
			pose.translate(lx, ly, lz);
			pose.scale(scale, scale, scale);
			out.submitNameTag(pose, ORIGIN, 0, label.text, true, FULL_BRIGHT, camera);
			pose.popPose();
		}
	}

	private Label label(Waypoint w, double d) {
		int metres = (int) Math.round(d);
		Label l = this.labels.get(w.id());
		if (l == null || l.metres != metres) {
			String dist = metres >= 10_000 ? String.format(Locale.ROOT, "%.1f km", metres / 1000.0) : metres + " m";
			l = new Label(metres, Component.literal(w.name() + "  " + dist));
			this.labels.put(w.id(), l);
		}
		return l;
	}
}
