package dev.voidmc.reel.tool;

import dev.voidmc.reel.math.Angles;
import dev.voidmc.reel.math.Vec3;
import dev.voidmc.reel.shot.CameraState;
import dev.voidmc.reel.shot.Shot;
import dev.voidmc.reel.shot.ShotParser;
import dev.voidmc.reel.shot.ShotReport;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;
import javax.imageio.ImageIO;

/**
 * Draws a shot's camera move without the game: the plan (seen from above, north up) with the view cone every half
 * second, the elevation (height against distance travelled), and speed and turn rate over time, with the report's
 * numbers and warnings. It is how a move is checked before a single frame is rendered.
 *
 * <p>Arguments: shots folder, shot name, output folder.
 */
public final class PathPreview {
	private static final Color PAPER = new Color(0xF4F1EA);
	private static final Color INK = new Color(0x2B2A27);
	private static final Color FAINT = new Color(0xC9C3B6);
	private static final Color PATH = new Color(0x2F6F8F);
	private static final Color CONE = new Color(0xD08A3C);
	private static final Color KEY = new Color(0xB5452F);
	private static final Color WARN = new Color(0xB5452F);

	private PathPreview() {
	}

	public static void main(String[] args) throws IOException {
		Path dir = Path.of(args[0]);
		String name = args[1];
		Path out = Path.of(args[2]);
		Shot shot = ShotParser.read(dir.resolve(name + ".json"));
		ShotReport report = ShotReport.of(shot);
		System.out.print(report.text());
		Files.createDirectories(out);
		Path file = out.resolve(name + "-path.png");
		ImageIO.write(draw(shot, report), "png", file.toFile());
		System.out.println("  plot: " + file);
	}

	public static BufferedImage draw(Shot shot, ShotReport report) {
		int w = 1600;
		int h = 1000;
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(PAPER);
		g.fillRect(0, 0, w, h);

		int samples = Math.max(2, (int) Math.ceil(shot.duration() * 30));
		List<CameraState> states = new ArrayList<>();
		for (int i = 0; i <= samples; i++) {
			states.add(shot.camera(shot.duration() * i / samples));
		}

		plan(g, shot, states, 20, 60, 940, 600);
		elevation(g, states, 980, 60, 600, 280);
		graph(g, "speed (blocks/s)", shot, states, 980, 380, 600, 140, true);
		graph(g, "turn (deg/s)", shot, states, 980, 540, 600, 120, false);

		g.setColor(INK);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
		g.drawString(shot.name(), 20, 36);
		g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
		int y = 690;
		for (String line : report.text().split("\n")) {
			g.setColor(line.trim().startsWith("!") ? WARN : INK);
			g.drawString(line, 20, y);
			y += 19;
		}
		g.dispose();
		return image;
	}

	private static void plan(Graphics2D g, Shot shot, List<CameraState> states, int x0, int y0, int w, int h) {
		frame(g, "plan (north up, x right)", x0, y0, w, h);
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (CameraState s : states) {
			minX = Math.min(minX, s.pos().x());
			maxX = Math.max(maxX, s.pos().x());
			minZ = Math.min(minZ, s.pos().z());
			maxZ = Math.max(maxZ, s.pos().z());
		}
		double span = Math.max(Math.max(maxX - minX, maxZ - minZ), 8) * 1.35;
		double cx = (minX + maxX) / 2;
		double cz = (minZ + maxZ) / 2;
		double scale = Math.min(w, h) / span;
		ToDoubleFunction<Vec3> px = p -> x0 + w / 2.0 + (p.x() - cx) * scale;
		ToDoubleFunction<Vec3> pz = p -> y0 + h / 2.0 + (p.z() - cz) * scale;

		double grid = niceStep(span / 8);
		g.setColor(FAINT);
		g.setStroke(new BasicStroke(1));
		for (double gx = Math.floor((cx - span) / grid) * grid; gx <= cx + span; gx += grid) {
			int sx = (int) (x0 + w / 2.0 + (gx - cx) * scale);
			if (sx > x0 && sx < x0 + w) {
				g.drawLine(sx, y0, sx, y0 + h);
			}
		}
		for (double gz = Math.floor((cz - span) / grid) * grid; gz <= cz + span; gz += grid) {
			int sz = (int) (y0 + h / 2.0 + (gz - cz) * scale);
			if (sz > y0 && sz < y0 + h) {
				g.drawLine(x0, sz, x0 + w, sz);
			}
		}
		g.setColor(INK);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g.drawString(String.format(Locale.ROOT, "grid %.0f blocks", grid), x0 + 8, y0 + h - 8);

		// view cones every half second: the horizontal field of view, a fixed length
		int every = Math.max(1, (states.size() - 1) / Math.max(1, (int) Math.round(shot.duration() * 2)));
		double coneLength = span * 0.08;
		for (int i = 0; i < states.size(); i += every) {
			CameraState s = states.get(i);
			double half = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(s.fov()) / 2) * shot.aspect()));
			Vec3 l = s.pos().add(Angles.direction(s.yaw() - half, 0).scale(coneLength));
			Vec3 r = s.pos().add(Angles.direction(s.yaw() + half, 0).scale(coneLength));
			Path2D cone = new Path2D.Double();
			cone.moveTo(px.applyAsDouble(s.pos()), pz.applyAsDouble(s.pos()));
			cone.lineTo(px.applyAsDouble(l), pz.applyAsDouble(l));
			cone.lineTo(px.applyAsDouble(r), pz.applyAsDouble(r));
			cone.closePath();
			g.setColor(new Color(CONE.getRed(), CONE.getGreen(), CONE.getBlue(), 60));
			g.fill(cone);
			g.setColor(CONE);
			g.draw(cone);
		}
		Path2D path = new Path2D.Double();
		for (int i = 0; i < states.size(); i++) {
			Vec3 p = states.get(i).pos();
			if (i == 0) {
				path.moveTo(px.applyAsDouble(p), pz.applyAsDouble(p));
			} else {
				path.lineTo(px.applyAsDouble(p), pz.applyAsDouble(p));
			}
		}
		g.setColor(PATH);
		g.setStroke(new BasicStroke(3));
		g.draw(path);
		// a tick each second along the path
		g.setColor(INK);
		for (int sec = 0; sec <= shot.duration(); sec++) {
			Vec3 p = shot.camera(sec).pos();
			int sx = (int) px.applyAsDouble(p);
			int sz = (int) pz.applyAsDouble(p);
			g.fillOval(sx - 4, sz - 4, 8, 8);
			g.drawString(sec + "s", sx + 6, sz - 6);
		}
		Vec3 start = states.getFirst().pos();
		g.setColor(KEY);
		g.fillOval((int) px.applyAsDouble(start) - 7, (int) pz.applyAsDouble(start) - 7, 14, 14);
	}

	private static void elevation(Graphics2D g, List<CameraState> states, int x0, int y0, int w, int h) {
		frame(g, "elevation (height against distance)", x0, y0, w, h);
		double total = 0;
		double[] along = new double[states.size()];
		double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (int i = 0; i < states.size(); i++) {
			if (i > 0) {
				Vec3 a = states.get(i - 1).pos();
				Vec3 b = states.get(i).pos();
				total += Math.hypot(b.x() - a.x(), b.z() - a.z());
			}
			along[i] = total;
			minY = Math.min(minY, states.get(i).pos().y());
			maxY = Math.max(maxY, states.get(i).pos().y());
		}
		double ySpan = Math.max(maxY - minY, 4);
		double pad = ySpan * 0.2;
		Path2D path = new Path2D.Double();
		for (int i = 0; i < states.size(); i++) {
			double sx = x0 + 10 + (total == 0 ? (double) i / (states.size() - 1) : along[i] / total) * (w - 20);
			double sy = y0 + h - 10 - (states.get(i).pos().y() - minY + pad) / (ySpan + 2 * pad) * (h - 20);
			if (i == 0) {
				path.moveTo(sx, sy);
			} else {
				path.lineTo(sx, sy);
			}
		}
		g.setColor(PATH);
		g.setStroke(new BasicStroke(3));
		g.draw(path);
		g.setColor(INK);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g.drawString(String.format(Locale.ROOT, "y %.0f to %.0f, %.0f blocks across", minY, maxY, total), x0 + 8, y0 + h - 8);
	}

	private static void graph(Graphics2D g, String title, Shot shot, List<CameraState> states, int x0, int y0, int w,
		int h, boolean speed) {
		frame(g, title, x0, y0, w, h);
		double dt = shot.duration() / (states.size() - 1);
		double[] v = new double[states.size()];
		double max = 1e-6;
		for (int i = 1; i < states.size(); i++) {
			CameraState a = states.get(i - 1);
			CameraState b = states.get(i);
			if (speed) {
				v[i] = b.pos().distance(a.pos()) / dt;
			} else {
				Vec3 da = Angles.direction(a.yaw(), a.pitch());
				Vec3 db = Angles.direction(b.yaw(), b.pitch());
				double dot = Math.clamp(da.x() * db.x() + da.y() * db.y() + da.z() * db.z(), -1, 1);
				v[i] = Math.toDegrees(Math.acos(dot)) / dt;
			}
			max = Math.max(max, v[i]);
		}
		v[0] = v.length > 1 ? v[1] : 0;
		Path2D path = new Path2D.Double();
		for (int i = 0; i < v.length; i++) {
			double sx = x0 + 10 + (double) i / (v.length - 1) * (w - 20);
			double sy = y0 + h - 10 - v[i] / (max * 1.15) * (h - 30);
			if (i == 0) {
				path.moveTo(sx, sy);
			} else {
				path.lineTo(sx, sy);
			}
		}
		g.setColor(PATH);
		g.setStroke(new BasicStroke(2));
		g.draw(path);
		g.setColor(INK);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g.drawString(String.format(Locale.ROOT, "peak %.1f", max), x0 + w - 90, y0 + 30);
	}

	private static void frame(Graphics2D g, String title, int x0, int y0, int w, int h) {
		g.setColor(Color.WHITE);
		g.fillRoundRect(x0, y0, w, h, 12, 12);
		g.setColor(FAINT);
		g.setStroke(new BasicStroke(1));
		g.drawRoundRect(x0, y0, w, h, 12, 12);
		g.setColor(INK);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		g.drawString(title, x0 + 8, y0 + 18);
	}

	private static double niceStep(double raw) {
		double p = Math.pow(10, Math.floor(Math.log10(raw)));
		for (double m : new double[] {1, 2, 5, 10}) {
			if (m * p >= raw) {
				return m * p;
			}
		}
		return 10 * p;
	}
}
