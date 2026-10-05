package dev.voidmc.atlas.core.waypoints;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds block coordinates in chat text, so they can be turned into clickable waypoints. Three shapes are read, in
 * this order of trust, and a later shape never claims text an earlier one already took:
 * <ol>
 * <li>Xaero's shared waypoints, {@code xaero-waypoint:Home:H:100:64:-200:6:false:0:Internal-overworld-waypoints},
 * which also carry a name and a dimension.</li>
 * <li>Labelled coordinates, {@code X: 100 Y: 64 Z: -200}, {@code x=100 y=64 z=-200}, or just x and z. The labels
 * make these certain, so only the world range is checked.</li>
 * <li>Bare runs of numbers: {@code 100 64 -200}, {@code 100, 64, -200}, {@code 100 / 64 / -200} (the F3 screen's
 * {@code XYZ:} line), the same inside brackets, or two numbers as x and z. These are where false positives live, so
 * they follow the rules below.</li>
 * </ol>
 * Decimals are rounded down to the block they are in ({@code -200.3} is block {@code -201}).
 *
 * <p>Rules for bare numbers. A number is unusable (and spoils any run it is in) when it touches a letter, digit,
 * underscore, dot, {@code #}, {@code $}, {@code £} or {@code €} on either side, is followed by {@code %} or
 * {@code °}, has two or more dots ({@code 1.21.4}), has a leading zero ({@code 05}, {@code 007}), or is joined to
 * another number by a hyphen with no space ({@code 10-2}, {@code 2026-10-05}) or by a colon ({@code 12:30}). Numbers
 * are joined into a run when only spaces, or one comma, or one slash (with optional spaces) stand between them; a run
 * is used only when it holds two or three numbers, all joined the same way. So {@code tp 1 2 3 4} gives nothing
 * rather than a guess. On top of that:
 * <ul>
 * <li>x and z must be within {@value #XZ_LIMIT} and y within {@value #Y_LIMIT} either way of zero.</li>
 * <li>Without a keyword (below) three numbers must hold at least {@value #MIN_DIGITS} digits between them and two
 * numbers at least {@value #MIN_PAIR_DIGITS}, so {@code 1 2 3}, {@code 10 20} and {@code 12 34} are ignored but
 * {@code 10 64 5}, {@code 50 600} and {@code 100 -200} are read.</li>
 * <li>Two numbers are read as x and z only when joined by spaces or a comma (never a slash, which is how fractions
 * and ratios are written) and, without a keyword, only when both are whole numbers.</li>
 * <li>A comma run with no spaces whose later numbers all have exactly three digits is a thousands separator
 * ({@code 1,500} or {@code 12,345,678}), not coordinates.</li>
 * <li>Three numbers joined by slashes with no spaces, one end a year (1900 to 2199) and the other two no more than
 * 31, are a date ({@code 5/10/2026}).</li>
 * </ul>
 * A keyword just before the numbers ({@code coords}, {@code coordinates}, {@code xyz}, {@code pos}, {@code position},
 * {@code location}, {@code loc}, {@code tp}, {@code teleport}, {@code goto}, {@code waypoint}, optionally followed by
 * {@code :} or {@code =}) lifts the digit and whole-number rules.
 *
 * <p>The dimension hint is the nearest dimension word in the text ({@code nether}, {@code the_nether},
 * {@code overworld}, {@code the_end}, with or without {@code minecraft:}). Because "end" is an ordinary English word,
 * {@code end} and {@code the end} only count when they sit right next to the coordinates with nothing but a few
 * spaces or punctuation marks between ({@code End: 100 64 -200}). It is a hint, not a fact: the caller should fall
 * back to the player's own dimension when it is null.
 */
public final class CoordParser {
	public static final int XZ_LIMIT = 30_000_000;
	public static final int Y_LIMIT = 2048;
	/** Digits three bare numbers must hold between them, without a keyword. */
	public static final int MIN_DIGITS = 4;
	/** Digits two bare numbers must hold between them, without a keyword. */
	public static final int MIN_PAIR_DIGITS = 5;

	/** Which shape of text a match came from. */
	public enum Form {
		XAERO, LABELLED, BARE
	}

	/**
	 * One set of coordinates found in a text.
	 *
	 * @param start     the first character of the match in the text
	 * @param end       one past the last character, so {@code text.substring(start, end)} is the match
	 * @param y         null when the text only gave x and z (or Xaero's {@code ~})
	 * @param dimension a dimension id the text names, or null
	 * @param name      the waypoint's name, for Xaero's shares only; null otherwise
	 */
	public record Match(int start, int end, int x, Integer y, int z, String dimension, String name, Form form) {
		Match withDimension(String dimension) {
			return new Match(this.start, this.end, this.x, this.y, this.z, dimension, this.name, this.form);
		}
	}

	private static final String NUM = "(-?\\d+(?:\\.\\d+)?)(?![\\w°%]|\\.\\d)";
	private static final String LABEL_SEP = "\\s*[,;/|]?\\s*";
	private static final Pattern XAERO = Pattern.compile(
		"xaero-waypoint:([^:\\r\\n]{0,96}):([^:\\r\\n]{0,8}):(-?\\d{1,9}):(~|-?\\d{1,9}):(-?\\d{1,9})((?::[^\\s:]*)*)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern XAERO_DIM = Pattern.compile("internal[-_](.+?)[-_]waypoints", Pattern.CASE_INSENSITIVE);
	private static final Pattern LABELLED_XYZ = Pattern.compile(
		"(?<![\\w])x\\s*[:=]\\s*" + NUM + LABEL_SEP + "y\\s*[:=]\\s*" + NUM + LABEL_SEP + "z\\s*[:=]\\s*" + NUM,
		Pattern.CASE_INSENSITIVE);
	private static final Pattern LABELLED_XZ = Pattern.compile(
		"(?<![\\w])x\\s*[:=]\\s*" + NUM + LABEL_SEP + "z\\s*[:=]\\s*" + NUM, Pattern.CASE_INSENSITIVE);
	private static final Pattern DIGITS = Pattern.compile("\\d+(?:\\.\\d+)*");
	private static final Pattern KEYWORD = Pattern.compile(
		"(?i)(?<![\\w])(?:coords?|coordinates|xyz|pos|position|location|loc|tp|teleport|goto|waypoint)\\s*[:=]?\\s*$");
	private static final Pattern DIM_WORD = Pattern.compile(
		"(?i)(?<![\\w:])(minecraft:)?(the[ _]nether|nether|the[ _]end|end|overworld)(?![\\w])");

	private CoordParser() {
	}

	/** Every set of coordinates in the text, in the order they appear. Never null. */
	public static List<Match> find(String text) {
		List<Match> found = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return found;
		}
		boolean[] claimed = new boolean[text.length()];
		findXaero(text, claimed, found);
		findLabelled(text, LABELLED_XYZ, true, claimed, found);
		findLabelled(text, LABELLED_XZ, false, claimed, found);
		findBare(text, claimed, found);
		found.sort(Comparator.comparingInt(Match::start));
		return hintDimensions(text, found);
	}

	/** The first set of coordinates in the text, or null when there is none. */
	public static Match first(String text) {
		List<Match> all = find(text);
		return all.isEmpty() ? null : all.get(0);
	}

	// ---- Xaero ----

	private static void findXaero(String text, boolean[] claimed, List<Match> found) {
		Matcher m = XAERO.matcher(text);
		while (m.find()) {
			boolean noY = m.group(4).equals("~");
			Integer x = whole(m.group(3));
			Integer y = noY ? null : whole(m.group(4));
			Integer z = whole(m.group(5));
			if (x == null || z == null || !noY && y == null) {
				continue;
			}
			if (!xzOk(x) || !xzOk(z) || y != null && !yOk(y)) {
				continue;
			}
			String name = m.group(1).replace("§§", ":").strip();
			String dim = null;
			Matcher d = XAERO_DIM.matcher(m.group(6));
			if (d.find()) {
				dim = xaeroDimension(d.group(1));
			}
			found.add(new Match(m.start(), m.end(), x, y, z, dim, name.isEmpty() ? null : name, Form.XAERO));
			claim(claimed, m.start(), m.end());
		}
	}

	/** Reads the dimension part of Xaero's {@code Internal-<dim>-waypoints} container name. */
	static String xaeroDimension(String raw) {
		String s = raw.strip();
		if (s.toLowerCase(Locale.ROOT).startsWith("dim%")) {
			s = s.substring(4).replace('$', ':');
		}
		String id = DimensionIds.normalise(s);
		if (id == null) {
			id = DimensionIds.normalise(s.replace('-', '_'));
		}
		return id;
	}

	// ---- labelled ----

	private static void findLabelled(String text, Pattern pattern, boolean withY, boolean[] claimed, List<Match> found) {
		Matcher m = pattern.matcher(text);
		while (m.find()) {
			if (anyClaimed(claimed, m.start(), m.end())) {
				continue;
			}
			Integer x = floor(m.group(1));
			Integer y = withY ? floor(m.group(2)) : null;
			Integer z = floor(m.group(withY ? 3 : 2));
			if (x == null || z == null || withY && y == null) {
				continue;
			}
			if (!xzOk(x) || !xzOk(z) || withY && !yOk(y)) {
				continue;
			}
			found.add(new Match(m.start(), m.end(), x, y, z, null, null, Form.LABELLED));
			claim(claimed, m.start(), m.end());
		}
	}

	// ---- bare numbers ----

	private enum Sep {
		SPACE, COMMA, SLASH
	}

	private static final class Token {
		final int start;
		final int end;
		final String text;
		boolean bad;
		Sep sepBefore;
		boolean tightBefore;

		Token(int start, int end, String text) {
			this.start = start;
			this.end = end;
			this.text = text;
		}

		boolean negative() {
			return this.text.startsWith("-");
		}

		boolean decimal() {
			return this.text.indexOf('.') >= 0;
		}

		/** Digits before any decimal point, without the sign. */
		String integerPart() {
			String s = negative() ? this.text.substring(1) : this.text;
			int dot = s.indexOf('.');
			return dot < 0 ? s : s.substring(0, dot);
		}
	}

	private static void findBare(String text, boolean[] claimed, List<Match> found) {
		List<Token> tokens = tokens(text, claimed);
		int i = 0;
		while (i < tokens.size()) {
			int j = i + 1;
			while (j < tokens.size() && tokens.get(j).sepBefore != null) {
				j++;
			}
			Match match = run(text, tokens.subList(i, j));
			if (match != null) {
				found.add(match);
				claim(claimed, match.start(), match.end());
			}
			i = j;
		}
	}

	private static List<Token> tokens(String text, boolean[] claimed) {
		List<Token> tokens = new ArrayList<>();
		Matcher m = DIGITS.matcher(text);
		while (m.find()) {
			int s = m.start();
			int e = m.end();
			if (anyClaimed(claimed, s, e)) {
				continue;
			}
			boolean neg = s > 0 && text.charAt(s - 1) == '-';
			int ts = neg ? s - 1 : s;
			Token t = new Token(ts, e, text.substring(ts, e));
			char before = ts > 0 ? text.charAt(ts - 1) : ' ';
			char after = e < text.length() ? text.charAt(e) : ' ';
			char afterNext = e + 1 < text.length() ? text.charAt(e + 1) : ' ';
			String digits = m.group();
			if (digits.indexOf('.') != digits.lastIndexOf('.')) {
				t.bad = true;
			}
			String ip = t.integerPart();
			if (ip.length() > 1 && ip.charAt(0) == '0') {
				t.bad = true;
			}
			if (Character.isLetterOrDigit(before) || before == '_' || before == '.' || "#$£€".indexOf(before) >= 0) {
				t.bad = true;
			}
			if (Character.isLetter(after) || after == '_' || after == '%' || after == '°') {
				t.bad = true;
			}
			if ((after == ':' || after == '-' || after == '.') && Character.isDigit(afterNext)) {
				t.bad = true;
			}
			if (before == ':' && ts >= 2 && Character.isDigit(text.charAt(ts - 2))) {
				t.bad = true;
			}
			if (!tokens.isEmpty()) {
				Token prev = tokens.get(tokens.size() - 1);
				if (prev.end <= ts) {
					String gap = text.substring(prev.end, ts);
					t.sepBefore = separator(gap);
					t.tightBefore = gap.strip().equals(gap);
				}
			}
			tokens.add(t);
		}
		return tokens;
	}

	private static Sep separator(String gap) {
		if (gap.isEmpty()) {
			return null;
		}
		String core = gap.strip();
		if (core.isEmpty()) {
			return Sep.SPACE;
		}
		if (core.equals(",")) {
			return Sep.COMMA;
		}
		if (core.equals("/")) {
			return Sep.SLASH;
		}
		return null;
	}

	private static Match run(String text, List<Token> run) {
		if (run.size() < 2 || run.size() > 3) {
			return null;
		}
		for (Token t : run) {
			if (t.bad || t.integerPart().length() > 9) {
				return null;
			}
		}
		Sep sep = run.get(1).sepBefore;
		boolean tight = true;
		for (int i = 1; i < run.size(); i++) {
			if (run.get(i).sepBefore != sep) {
				return null;
			}
			tight &= run.get(i).tightBefore;
		}
		if (sep == Sep.COMMA && tight && looksLikeThousands(run)) {
			return null;
		}
		if (tight && sep == Sep.SLASH && run.size() == 3 && looksLikeDate(run)) {
			return null;
		}

		int start = run.get(0).start;
		int end = run.get(run.size() - 1).end;
		// brackets around the whole run belong to it
		int ls = start;
		while (ls > 0 && Character.isWhitespace(text.charAt(ls - 1))) {
			ls--;
		}
		int re = end;
		while (re < text.length() && Character.isWhitespace(text.charAt(re))) {
			re++;
		}
		if (ls > 0 && re < text.length()) {
			char open = text.charAt(ls - 1);
			char close = text.charAt(re);
			if (open == '[' && close == ']' || open == '(' && close == ')' || open == '{' && close == '}' || open == '<' && close == '>') {
				start = ls - 1;
				end = re + 1;
			}
		}
		boolean keyword = KEYWORD.matcher(text.substring(Math.max(0, start - 40), start)).find();

		int digits = 0;
		for (Token t : run) {
			digits += t.integerPart().length();
		}
		if (!keyword && digits < (run.size() == 2 ? MIN_PAIR_DIGITS : MIN_DIGITS)) {
			return null;
		}
		if (run.size() == 2) {
			if (sep == Sep.SLASH) {
				return null;
			}
			if (!keyword && (run.get(0).decimal() || run.get(1).decimal())) {
				return null;
			}
			Integer x = floor(run.get(0).text);
			Integer z = floor(run.get(1).text);
			if (x == null || z == null || !xzOk(x) || !xzOk(z)) {
				return null;
			}
			return new Match(start, end, x, null, z, null, null, Form.BARE);
		}
		Integer x = floor(run.get(0).text);
		Integer y = floor(run.get(1).text);
		Integer z = floor(run.get(2).text);
		if (x == null || y == null || z == null || !xzOk(x) || !yOk(y) || !xzOk(z)) {
			return null;
		}
		return new Match(start, end, x, y, z, null, null, Form.BARE);
	}

	private static boolean looksLikeThousands(List<Token> run) {
		Token first = run.get(0);
		if (first.negative() || first.decimal() || first.integerPart().length() > 3) {
			return false;
		}
		for (int i = 1; i < run.size(); i++) {
			Token t = run.get(i);
			if (t.negative() || t.decimal() || t.integerPart().length() != 3) {
				return false;
			}
		}
		return true;
	}

	private static boolean looksLikeDate(List<Token> run) {
		for (Token t : run) {
			if (t.negative() || t.decimal()) {
				return false;
			}
		}
		int a = Integer.parseInt(run.get(0).text);
		int b = Integer.parseInt(run.get(1).text);
		int c = Integer.parseInt(run.get(2).text);
		return year(a) && b <= 31 && c <= 31 || year(c) && a <= 31 && b <= 31;
	}

	private static boolean year(int v) {
		return v >= 1900 && v <= 2199;
	}

	// ---- dimension hints ----

	private record DimWord(int start, int end, String id, boolean nearOnly) {
	}

	private static List<Match> hintDimensions(String text, List<Match> found) {
		if (found.isEmpty()) {
			return found;
		}
		List<DimWord> words = new ArrayList<>();
		Matcher m = DIM_WORD.matcher(text);
		while (m.find()) {
			boolean inside = false;
			for (Match f : found) {
				if (m.start() < f.end() && m.end() > f.start()) {
					inside = true;
					break;
				}
			}
			if (inside) {
				continue;
			}
			String word = m.group(2).toLowerCase(Locale.ROOT);
			boolean prefixed = m.group(1) != null;
			boolean nearOnly = !prefixed && (word.equals("end") || word.equals("the end"));
			words.add(new DimWord(m.start(), m.end(), DimensionIds.normalise(word), nearOnly));
		}
		List<Match> out = new ArrayList<>(found.size());
		for (Match f : found) {
			if (f.dimension() != null || words.isEmpty()) {
				out.add(f);
				continue;
			}
			DimWord best = null;
			int bestGap = Integer.MAX_VALUE;
			for (DimWord w : words) {
				int gap = w.end() <= f.start() ? f.start() - w.end() : w.start() - f.end();
				if (w.nearOnly() && !adjacent(text, w, f)) {
					continue;
				}
				if (gap < bestGap) {
					bestGap = gap;
					best = w;
				}
			}
			out.add(best == null ? f : f.withDimension(best.id()));
		}
		return out;
	}

	private static boolean adjacent(String text, DimWord w, Match f) {
		String gap = w.end() <= f.start() ? text.substring(w.end(), f.start()) : text.substring(f.end(), w.start());
		if (gap.length() > 4) {
			return false;
		}
		for (int i = 0; i < gap.length(); i++) {
			if (Character.isLetterOrDigit(gap.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	// ---- helpers ----

	private static Integer whole(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** The block a number lies in (rounded down), or null if it is not a number or is far outside any world. */
	private static Integer floor(String s) {
		try {
			double v = Double.parseDouble(s);
			if (!Double.isFinite(v) || Math.abs(v) > 1e9) {
				return null;
			}
			return (int) Math.floor(v);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static boolean xzOk(int v) {
		return Math.abs((long) v) <= XZ_LIMIT;
	}

	private static boolean yOk(int v) {
		return v >= -Y_LIMIT && v <= Y_LIMIT;
	}

	private static void claim(boolean[] claimed, int start, int end) {
		for (int i = start; i < end; i++) {
			claimed[i] = true;
		}
	}

	private static boolean anyClaimed(boolean[] claimed, int start, int end) {
		for (int i = start; i < end; i++) {
			if (claimed[i]) {
				return true;
			}
		}
		return false;
	}
}
