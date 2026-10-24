package dev.voidmc.reel.tool;

import dev.voidmc.reel.shot.CameraState;
import dev.voidmc.reel.shot.Shot;
import dev.voidmc.reel.shot.ShotParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Writes a shot's camera for every frame as JSON, for Blender (tools/blender/reel_camera.py) or anything else that
 * has to line up with the film: overlays, labels, a map that turns into the land. The camera is the shot's own,
 * shake included and the anti-aliasing jitter left out, at each frame's centre instant, which is what the frame
 * shows. Minecraft's conventions are kept and stated in the file; the importer converts.
 *
 * <p>Arguments: shots folder, shot name, output folder. The studio also writes renders/&lt;shot&gt;/camera.json next to
 * every movie it films.
 */
public final class CameraExport {
	private CameraExport() {
	}

	public static void main(String[] args) throws IOException {
		Shot shot = ShotParser.read(Path.of(args[0]).resolve(args[1] + ".json"));
		Path out = Path.of(args[2]);
		Files.createDirectories(out);
		Path file = out.resolve(args[1] + "-camera.json");
		Files.writeString(file, json(shot, shot.width(), shot.height()));
		System.out.println("camera: " + file);
	}

	/** One line per frame: [t, x, y, z, yaw, pitch, roll, fov]. */
	public static String json(Shot shot, int width, int height) {
		StringBuilder b = new StringBuilder();
		b.append("{\n");
		b.append("  \"shot\": \"").append(shot.name().replace("\"", "'")).append("\",\n");
		b.append(String.format(Locale.ROOT, "  \"fps\": %d,%n  \"size\": [%d, %d],%n  \"frames\": %d,%n  \"duration\": %.6f,%n", shot.fps(), width, height,
			shot.frames(), shot.duration()));
		b.append("  \"space\": \"minecraft: x east, y up, z south, 1 block = 1 m; yaw 0 faces south (+z), 90 west; pitch positive looks down; "
			+ "roll degrees about the view axis; fov vertical degrees; the view is rotationYXZ(pi - yaw, -pitch, roll) with forward -z, up +y\",\n");
		b.append("  \"columns\": [\"t\", \"x\", \"y\", \"z\", \"yaw\", \"pitch\", \"roll\", \"fov\"],\n");
		b.append("  \"camera\": [\n");
		int n = shot.frames();
		for (int f = 0; f < n; f++) {
			double t = shot.timeOf(f);
			CameraState c = shot.camera(t);
			b.append(String.format(Locale.ROOT, "    [%.6f, %.5f, %.5f, %.5f, %.5f, %.5f, %.5f, %.5f]%s%n", t, c.pos().x(), c.pos().y(), c.pos().z(), c.yaw(),
				c.pitch(), c.roll(), c.fov(), f < n - 1 ? "," : ""));
		}
		b.append("  ]\n}\n");
		return b.toString();
	}
}
