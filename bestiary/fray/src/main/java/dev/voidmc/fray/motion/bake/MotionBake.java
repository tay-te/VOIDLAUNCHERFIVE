package dev.voidmc.fray.motion.bake;

import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.ClipJson;
import dev.voidmc.fray.motion.Timeline;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;

/**
 * The bake (Gradle task {@code motionBake}): every {@code <src>/<namespace>/<path>.motion.json} becomes
 * <ul>
 * <li>{@code assets/<namespace>/fray/clip/<path>.json}, the client clip, in the canonical layout of
 * {@link ClipJson#writeClient};</li>
 * <li>{@code data/<namespace>/fray/timeline/<path>.json}, the server timeline, carrying the SHA-256 of the client
 * clip's bytes (the login handshake compares the clip set built from these).</li>
 * </ul>
 * and a report of phases and hit-volume reach. A clip that doesn't validate fails the build.
 */
public final class MotionBake {
	private MotionBake() {
	}

	public static void main(String[] args) throws IOException {
		Path src = Path.of(args[0]);
		Path out = Path.of(args[1]);
		Path report = Path.of(args[2]);
		List<String> lines = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		int n = 0;
		List<Path> files;
		try (Stream<Path> walk = Files.walk(src)) {
			files = walk.filter(p -> p.getFileName().toString().endsWith(".motion.json")).sorted().toList();
		}
		for (Path file : files) {
			Path rel = src.relativize(file);
			if (rel.getNameCount() < 2) {
				throw new IllegalArgumentException(file + ": motion sources live in <namespace>/<path>.motion.json");
			}
			String ns = rel.getName(0).toString();
			String path = rel.subpath(1, rel.getNameCount()).toString().replace('\\', '/');
			path = path.substring(0, path.length() - ".motion.json".length());
			Identifier id = Identifier.fromNamespaceAndPath(ns, path);
			Baked baked;
			try {
				baked = bake(id, Files.readString(file, StandardCharsets.UTF_8), warnings);
			} catch (RuntimeException e) {
				throw new IllegalArgumentException("motionBake: " + rel + ": " + e.getMessage(), e);
			}
			write(out.resolve("assets/" + ns + "/fray/clip/" + path + ".json"), baked.clientJson);
			write(out.resolve("data/" + ns + "/fray/timeline/" + path + ".json"), baked.timeline.write());
			lines.add(describe(baked.clip, baked.timeline));
			n++;
		}
		lines.add(0, "Fray motion bake: " + n + " clips from " + src);
		for (String w : warnings) {
			lines.add("WARNING " + w);
		}
		Files.createDirectories(report.getParent());
		Files.write(report, lines, StandardCharsets.UTF_8);
		for (String l : lines) {
			System.out.println(l);
		}
	}

	public record Baked(Clip clip, String clientJson, String clipHash, Timeline timeline) {
	}

	/** Parses a source, writes its client clip, hashes it and bakes the timeline from the client clip as parsed back. */
	public static Baked bake(Identifier id, String source, List<String> warnings) {
		Clip parsed = ClipJson.parse(id, source);
		String clientJson = ClipJson.writeClient(parsed);
		String hash = ClipJson.sha256(ClipJson.utf8(clientJson));
		// The timeline is baked from the source (it has the markers and locators), but the curves it samples must be the
		// client's: check they survive the round trip through the canonical file.
		Clip client = ClipJson.parse(id, clientJson);
		String again = ClipJson.writeClient(client);
		if (!again.equals(clientJson)) {
			throw new IllegalStateException(id + ": the client clip doesn't round-trip");
		}
		// Bake from exactly the curves the client will read (the canonical file rounds to four decimals), with the
		// source's server-only parts.
		Clip forBake = new Clip(parsed.id, parsed.rig, client.length, client.loop, client.blendIn, client.blendOut, client.mask, client.rotation,
			client.position, client.scale, client.fp, client.cues, parsed.markers, parsed.locators, parsed.hits, parsed.modelScale, parsed.damage, client.motion);
		Timeline timeline = TimelineBaker.bake(forBake, hash, warnings);
		return new Baked(parsed, clientJson, hash, timeline);
	}

	private static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
	}

	public static String describe(Clip clip, Timeline t) {
		StringBuilder s = new StringBuilder();
		s.append(String.format(Locale.ROOT, "%-34s %3d ticks %-5s hash %s", clip.id, t.ticks, clip.loop.id(), t.clipHash.substring(0, 12)));
		if (t.hasPhases()) {
			s.append(String.format(Locale.ROOT, " | windup 0-%d active %d-%d recovery %d-%d", t.activeStart - 1, t.activeStart, t.activeEnd - 1, t.activeEnd,
				t.ticks - 1));
		}
		if (t.tellFrom >= 0) {
			s.append(" | tell ").append(t.tellColour).append(" from ").append(t.tellFrom);
		}
		for (Map.Entry<String, Timeline.Capsule[]> e : t.volumes.entrySet()) {
			float reach = 0, lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
			for (Timeline.Capsule c : e.getValue()) {
				reach = Math.max(reach, c.reach());
				lo = Math.min(lo, Math.min(c.ay(), c.by()) - c.r());
				hi = Math.max(hi, Math.max(c.ay(), c.by()) + c.r());
			}
			s.append(String.format(Locale.ROOT, " | %s reach %.2f, height %.2f-%.2f", e.getKey(), reach, lo, hi));
			for (int i = 0; i < e.getValue().length; i++) {
				Timeline.Capsule c = e.getValue()[i];
				s.append(String.format(Locale.ROOT, "%n      tick %2d  a(%5.2f %5.2f %5.2f) b(%5.2f %5.2f %5.2f) r %.2f", t.activeStart + i, c.ax(), c.ay(), c.az(), c.bx(),
					c.by(), c.bz(), c.r()));
			}
		}
		return s.toString();
	}
}
