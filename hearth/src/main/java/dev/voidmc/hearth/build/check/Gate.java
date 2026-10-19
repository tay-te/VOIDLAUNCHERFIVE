package dev.voidmc.hearth.build.check;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.build.check.PlayChecks.Finding;
import dev.voidmc.hearth.build.check.PlayChecks.Rule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * The audit gate's switch for the play rules ({@link PlayChecks}): whether a broken rule fails the tests or is only
 * reported, and which archetypes are known to break which rules today.
 *
 * <p><b>The switch.</b> {@link #DEFAULT} is {@link Mode#REPORT} while the wave-7 fix lanes work: the rules run on every
 * blueprint the tests and previews check, nothing fails on them, and every (archetype, rule) that breaks is tallied
 * and written to {@code build/reports/gate/} at the end of the test JVM. At wave exit the coordinator sets it to
 * {@link Mode#HARD}: a rule broken by an archetype not listed for it in {@code hearth/gate/known/} is then a problem of
 * {@link BlueprintChecks#all}, so {@code BlueprintInvariantsTest} and every family test fail on it. {@code
 * -Dhearth.gate=report|hard} (Gradle: {@code -Phearth.gate=hard}) overrides the default for one run.
 *
 * <p><b>The known failures.</b> One file per category ({@code hearth/gate/known/house.txt}, {@code work.txt}, ...: the
 * first part of the archetype path, so each fix lane edits its own files), one line per archetype: its path, then the
 * rules it still breaks, then a comment. A lane that fixes an archetype deletes the rule (the report lists entries no
 * longer seen); the list only shrinks. The directory is {@code -Dhearth.gate.known} (the build sets it), else {@code
 * gate/known} under the working directory; with neither (in game) nothing is known. A decayed building's findings go
 * under {@link #DECAYED} ({@code age.txt}), not its archetype's.
 */
public final class Gate {
	private Gate() {
	}

	/** What a broken play rule does. */
	public enum Mode {
		/** Tallied and reported; never a problem. */
		REPORT,
		/** A problem (the tests fail) unless the archetype is listed as a known failure of that rule. */
		HARD
	}

	/** THE SWITCH. REPORT during wave 7; the coordinator sets HARD at wave exit (AUDIT.md §7, BUILDING_GUIDE.md §8). */
	public static final Mode DEFAULT = Mode.REPORT;

	/** The mode of this run: {@code -Dhearth.gate}, else {@link #DEFAULT}. */
	public static Mode mode() {
		String p = System.getProperty("hearth.gate", "");
		return switch (p.toLowerCase(Locale.ROOT)) {
			case "hard" -> Mode.HARD;
			case "report" -> Mode.REPORT;
			default -> DEFAULT;
		};
	}

	// ------------------------------------------------------------------------------------------- known failures

	private static volatile Map<String, Set<Rule>> known;

	/** The known failures: archetype path to the rules it breaks today. */
	public static Map<String, Set<Rule>> known() {
		Map<String, Set<Rule>> k = known;
		if (k == null) {
			synchronized (Gate.class) {
				if (known == null) {
					known = load(knownDir());
				}
				k = known;
			}
		}
		return k;
	}

	/** Where the known-failure lists are read from. */
	public static Path knownDir() {
		return Path.of(System.getProperty("hearth.gate.known", "gate/known"));
	}

	/** Reads every {@code *.txt} of a directory: {@code <archetype path> <rule> [<rule>...] [# comment]} per line. */
	public static Map<String, Set<Rule>> load(Path dir) {
		Map<String, Set<Rule>> out = new HashMap<>();
		if (!Files.isDirectory(dir)) {
			return out;
		}
		try (Stream<Path> files = Files.list(dir)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".txt")).sorted().toList()) {
				for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
					parse(line, out, f.getFileName().toString());
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("cannot read the gate's known failures in " + dir, e);
		}
		return out;
	}

	static void parse(String line, Map<String, Set<Rule>> out, String file) {
		int hash = line.indexOf('#');
		String body = (hash >= 0 ? line.substring(0, hash) : line).trim();
		if (body.isEmpty()) {
			return;
		}
		String[] parts = body.split("\\s+");
		Set<Rule> rules = out.computeIfAbsent(parts[0], p -> java.util.EnumSet.noneOf(Rule.class));
		for (int i = 1; i < parts.length; i++) {
			Rule r = Rule.byId(parts[i]);
			if (r == null) {
				throw new IllegalArgumentException(file + ": no gate rule '" + parts[i] + "' (rules: " + ruleIds() + ") in: " + line);
			}
			rules.add(r);
		}
	}

	private static String ruleIds() {
		List<String> ids = new ArrayList<>();
		for (Rule r : Rule.values()) {
			ids.add(r.id);
		}
		return String.join(", ", ids);
	}

	/** Whether an archetype is listed as breaking a rule today. */
	public static boolean knownFailure(String archetypePath, Rule rule) {
		Set<Rule> r = known().get(archetypePath);
		return r != null && r.contains(rule);
	}

	// ------------------------------------------------------------------------------------------- applying

	/** The pseudo-archetype decayed buildings are tallied and listed under ({@code gate/known/age.txt}). */
	public static final String DECAYED = "age/decay";

	/**
	 * The path a blueprint's findings are tallied and looked up under: its archetype's, or {@link #DECAYED} for a decayed
	 * one. A piece left hanging when the boards under it rot, or a door whose step fell in, is the decay pass's to answer
	 * (the age lane), not the archetype's; what the building gets wrong as built shows in its undecayed cases.
	 */
	public static String listedAs(PlotSpec spec) {
		return dev.voidmc.hearth.build.age.Decay.level(spec) > 0 ? DECAYED : spec.archetype().getPath();
	}

	/**
	 * Tallies a blueprint's play-rule findings and, in {@link Mode#HARD}, adds those not listed as known to {@code out}
	 * (each as one line beginning {@code "gate <rule>: "}).
	 */
	public static void apply(PlotSpec spec, List<Finding> findings, List<String> out) {
		String path = listedAs(spec);
		// the gate's own tests ("test/..." archetypes) stay out of the report
		boolean tally = !path.startsWith("test/");
		if (tally) {
			CHECKED.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
		}
		boolean hard = mode() == Mode.HARD;
		for (Finding f : findings) {
			if (tally) {
				Tally t = TALLY.computeIfAbsent(path + " " + f.rule().id, k -> new Tally());
				t.cases.incrementAndGet();
				if (t.example == null) {
					t.example = spec.style().getPath() + " seed " + spec.seed() + " " + spec.facing().getSerializedName() + ": " + f.text();
				}
			}
			if (hard && !knownFailure(path, f.rule())) {
				out.add(f.text());
			}
		}
	}

	// ------------------------------------------------------------------------------------------- the report

	private static final class Tally {
		final AtomicInteger cases = new AtomicInteger();
		volatile String example;
	}

	private static final Map<String, Tally> TALLY = new ConcurrentHashMap<>();
	private static final Map<String, AtomicInteger> CHECKED = new ConcurrentHashMap<>();

	static {
		if (System.getProperty("hearth.gate.out") != null) {
			Runtime.getRuntime().addShutdownHook(new Thread(Gate::writeReport, "hearth-gate-report"));
		}
	}

	/** The tally so far as report lines: {@code archetype rule cases known|NEW example}, worst first. */
	public static List<String> report() {
		List<String> lines = new ArrayList<>();
		Map<String, Tally> sorted = new TreeMap<>(TALLY);
		for (var e : sorted.entrySet()) {
			String[] k = e.getKey().split(" ");
			Rule r = Rule.byId(k[1]);
			lines.add(String.join("\t", k[0], k[1], Integer.toString(e.getValue().cases.get()), knownFailure(k[0], r) ? "known" : "NEW",
				e.getValue().example));
		}
		return lines;
	}

	/** Known entries for archetypes checked in this run that broke no case of the rule: fixed, so the line can go. */
	public static List<String> stale() {
		List<String> out = new ArrayList<>();
		for (var e : new TreeMap<>(known()).entrySet()) {
			if (!CHECKED.containsKey(e.getKey())) {
				continue;
			}
			for (Rule r : new TreeSet<>(e.getValue())) {
				if (!TALLY.containsKey(e.getKey() + " " + r.id)) {
					out.add(e.getKey() + "\t" + r.id + "\t" + CHECKED.get(e.getKey()).get() + " cases checked, none broke it");
				}
			}
		}
		return out;
	}

	private static void writeReport() {
		try {
			Path dir = Path.of(System.getProperty("hearth.gate.out"));
			Files.createDirectories(dir);
			List<String> lines = new ArrayList<>();
			lines.add("# hearth gate, mode " + mode().name().toLowerCase(Locale.ROOT) + ": " + CHECKED.values().stream().mapToInt(AtomicInteger::get).sum()
				+ " blueprints of " + CHECKED.size() + " archetypes checked");
			lines.add("# archetype\trule\tcases\tknown|NEW\tfirst case");
			List<String> rep = report();
			lines.addAll(rep);
			List<String> stale = stale();
			if (!stale.isEmpty()) {
				lines.add("# stale known entries (no case broke them in this JVM; check the other report files before deleting)");
				for (String s : stale) {
					lines.add("# " + s);
				}
			}
			Files.write(dir.resolve("gate-" + ProcessHandle.current().pid() + ".tsv"), lines, StandardCharsets.UTF_8);
			long fresh = rep.stream().filter(l -> l.split("\t")[3].equals("NEW")).count();
			System.out.println("[hearth gate] " + mode().name().toLowerCase(Locale.ROOT) + ": " + rep.size() + " archetype/rule failures, " + fresh
				+ " not in gate/known, " + stale.size() + " stale; " + dir.toAbsolutePath());
		} catch (IOException e) {
			System.out.println("[hearth gate] cannot write the report: " + e);
		}
	}

	/** For tests: forget the tally and reload the known lists. */
	static void reset() {
		TALLY.clear();
		CHECKED.clear();
		known = null;
	}

	/** An unmodifiable view of the categories with known failures (for the audit's gate table). */
	public static Set<String> knownArchetypes() {
		return Collections.unmodifiableSet(known().keySet());
	}
}
