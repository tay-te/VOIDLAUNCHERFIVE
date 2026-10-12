package dev.voidmc.reel.client;

import dev.voidmc.reel.Reel;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Turns a movie's frames into a video with ffmpeg, if there is one: -Dreel.ffmpeg=&lt;path&gt;, then
 * .tools/ffmpeg/bin under the repository, then the PATH. Without ffmpeg the frames stay as PNGs, which is a fine
 * master to cut from anyway. H.264 at a high quality (CRF 14) plays everywhere; -Dreel.codec=h265 makes a smaller
 * 10-bit HEVC file.
 */
public final class Encoder {
	private static final AtomicInteger RUNNING = new AtomicInteger();

	private Encoder() {
	}

	/** Encodes still going: the studio waits for them before the game closes. */
	public static int running() {
		return RUNNING.get();
	}

	static Path ffmpeg() {
		String set = System.getProperty("reel.ffmpeg");
		if (set != null && Files.isRegularFile(Path.of(set))) {
			return Path.of(set);
		}
		String exe = System.getProperty("os.name", "").startsWith("Windows") ? "ffmpeg.exe" : "ffmpeg";
		Path dir = Path.of("").toAbsolutePath();
		for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
			Path tools = dir.resolve(".tools").resolve("ffmpeg").resolve("bin").resolve(exe);
			if (Files.isRegularFile(tools)) {
				return tools;
			}
		}
		for (String entry : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
			Path candidate = Path.of(entry).resolve(exe);
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	static void encodeAsync(RenderJob job, Consumer<String> say) {
		Path ffmpeg = ffmpeg();
		if (ffmpeg == null) {
			say.accept("no ffmpeg found: frames are in " + job.out().resolve("frames") + " (set -Dreel.ffmpeg or put it in .tools/ffmpeg/bin)");
			return;
		}
		RUNNING.incrementAndGet();
		Thread thread = new Thread(() -> {
			Path video = job.out().resolve(job.shot().name() + ".mp4");
			List<String> command = new ArrayList<>(List.of(ffmpeg.toString(), "-y", "-loglevel", "error", "-framerate", Integer.toString(job.shot().fps()),
				"-i", job.out().resolve("frames").resolve("%06d.png").toString()));
			if ("h265".equals(System.getProperty("reel.codec"))) {
				command.addAll(List.of("-c:v", "libx265", "-preset", "slow", "-crf", "16", "-pix_fmt", "yuv420p10le", "-tag:v", "hvc1"));
			} else {
				command.addAll(List.of("-c:v", "libx264", "-preset", "slow", "-crf", "14", "-pix_fmt", "yuv420p"));
			}
			command.addAll(List.of("-movflags", "+faststart", video.toString()));
			try {
				Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(job.out().resolve("ffmpeg.log").toFile()).start();
				int code = process.waitFor();
				say.accept(code == 0 ? "encoded " + video : "ffmpeg failed (exit " + code + "): see " + job.out().resolve("ffmpeg.log"));
			} catch (IOException e) {
				say.accept("ffmpeg could not start: " + e.getMessage());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				RUNNING.decrementAndGet();
			}
		}, "Reel encoder");
		thread.setDaemon(false);
		thread.start();
		Reel.LOG.info("[reel] encoding {} with {}", job.shot().name(), ffmpeg);
	}
}
