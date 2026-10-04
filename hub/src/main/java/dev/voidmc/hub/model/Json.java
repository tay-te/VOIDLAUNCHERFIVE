package dev.voidmc.hub.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The smallest JSON that does the job: a writer for the messages Java sends the page, and a parser
 * for the ones the page sends back through {@code window.__void_native}. Both directions are small
 * flat objects, so this avoids depending on whichever JSON library the game happens to ship.
 */
public final class Json {

	private Json() {
	}

	public static Writer object() {
		return new Writer();
	}

	/** Builds one JSON object, keys in insertion order. */
	public static final class Writer {
		private final StringBuilder sb = new StringBuilder("{");
		private boolean first = true;

		private Writer key(String key) {
			if (!this.first) {
				this.sb.append(',');
			}
			this.first = false;
			quote(this.sb, key);
			this.sb.append(':');
			return this;
		}

		public Writer put(String key, String value) {
			this.key(key);
			if (value == null) {
				this.sb.append("null");
			} else {
				quote(this.sb, value);
			}
			return this;
		}

		public Writer put(String key, long value) {
			this.key(key).sb.append(value);
			return this;
		}

		public Writer put(String key, double value) {
			this.key(key).sb.append(number(value));
			return this;
		}

		public Writer put(String key, boolean value) {
			this.key(key).sb.append(value);
			return this;
		}

		/** Inserts already-encoded JSON (an object or array). */
		public Writer raw(String key, String json) {
			this.key(key).sb.append(json == null ? "null" : json);
			return this;
		}

		public Writer putStrings(String key, List<String> values) {
			this.key(key).sb.append('[');
			for (int i = 0; i < values.size(); i++) {
				if (i > 0) {
					this.sb.append(',');
				}
				quote(this.sb, values.get(i));
			}
			this.sb.append(']');
			return this;
		}

		public Writer putInts(String key, int[] values) {
			this.key(key).sb.append('[');
			for (int i = 0; i < values.length; i++) {
				if (i > 0) {
					this.sb.append(',');
				}
				this.sb.append(values[i]);
			}
			this.sb.append(']');
			return this;
		}

		public String end() {
			return this.sb.append('}').toString();
		}
	}

	public static String array(List<String> encodedObjects) {
		return "[" + String.join(",", encodedObjects) + "]";
	}

	/** Shortest round-trippable form for layout numbers; integers print without a fraction. */
	public static String number(double v) {
		if (Double.isNaN(v) || Double.isInfinite(v)) {
			return "0";
		}
		if (v == Math.rint(v) && Math.abs(v) < 1e15) {
			return Long.toString((long) v);
		}
		String s = String.format(Locale.ROOT, "%.6f", v);
		int end = s.length();
		while (end > 0 && s.charAt(end - 1) == '0') {
			end--;
		}
		if (end > 0 && s.charAt(end - 1) == '.') {
			end--;
		}
		return s.substring(0, end);
	}

	public static String quote(String s) {
		StringBuilder sb = new StringBuilder(s.length() + 2);
		quote(sb, s);
		return sb.toString();
	}

	static void quote(StringBuilder sb, String s) {
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				// Line/paragraph separators are legal JSON but end a JS statement: the page
				// receives these strings inside evaluateScript.
				case ' ' -> sb.append("\\u2028");
				case ' ' -> sb.append("\\u2029");
				default -> {
					if (c < 0x20) {
						sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		sb.append('"');
	}

	// ---- parsing ---------------------------------------------------------------------------------

	/**
	 * Parses a JSON value into String / Double / Boolean / null / {@code Map<String, Object>} /
	 * {@code List<Object>}. Throws IllegalArgumentException on malformed input.
	 */
	public static Object parse(String text) {
		Parser p = new Parser(text);
		p.ws();
		Object v = p.value();
		p.ws();
		if (p.i != text.length()) {
			throw new IllegalArgumentException("trailing characters at " + p.i);
		}
		return v;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> parseObject(String text) {
		Object v = parse(text);
		if (!(v instanceof Map)) {
			throw new IllegalArgumentException("not a JSON object");
		}
		return (Map<String, Object>) v;
	}

	private static final class Parser {
		private final String s;
		private int i;

		Parser(String s) {
			this.s = s;
		}

		void ws() {
			while (this.i < this.s.length() && Character.isWhitespace(this.s.charAt(this.i))) {
				this.i++;
			}
		}

		char peek() {
			if (this.i >= this.s.length()) {
				throw new IllegalArgumentException("unexpected end");
			}
			return this.s.charAt(this.i);
		}

		void expect(char c) {
			if (this.peek() != c) {
				throw new IllegalArgumentException("expected '" + c + "' at " + this.i);
			}
			this.i++;
		}

		Object value() {
			char c = this.peek();
			return switch (c) {
				case '{' -> this.object();
				case '[' -> this.array();
				case '"' -> this.string();
				case 't' -> this.literal("true", Boolean.TRUE);
				case 'f' -> this.literal("false", Boolean.FALSE);
				case 'n' -> this.literal("null", null);
				default -> this.number();
			};
		}

		Object literal(String word, Object v) {
			if (!this.s.startsWith(word, this.i)) {
				throw new IllegalArgumentException("bad literal at " + this.i);
			}
			this.i += word.length();
			return v;
		}

		Map<String, Object> object() {
			this.expect('{');
			Map<String, Object> m = new LinkedHashMap<>();
			this.ws();
			if (this.peek() == '}') {
				this.i++;
				return m;
			}
			while (true) {
				this.ws();
				String k = this.string();
				this.ws();
				this.expect(':');
				this.ws();
				m.put(k, this.value());
				this.ws();
				if (this.peek() == ',') {
					this.i++;
					continue;
				}
				this.expect('}');
				return m;
			}
		}

		List<Object> array() {
			this.expect('[');
			List<Object> l = new ArrayList<>();
			this.ws();
			if (this.peek() == ']') {
				this.i++;
				return l;
			}
			while (true) {
				this.ws();
				l.add(this.value());
				this.ws();
				if (this.peek() == ',') {
					this.i++;
					continue;
				}
				this.expect(']');
				return l;
			}
		}

		String string() {
			this.expect('"');
			StringBuilder sb = new StringBuilder();
			while (true) {
				char c = this.peek();
				this.i++;
				if (c == '"') {
					return sb.toString();
				}
				if (c == '\\') {
					char e = this.peek();
					this.i++;
					switch (e) {
						case '"', '\\', '/' -> sb.append(e);
						case 'b' -> sb.append('\b');
						case 'f' -> sb.append('\f');
						case 'n' -> sb.append('\n');
						case 'r' -> sb.append('\r');
						case 't' -> sb.append('\t');
						case 'u' -> {
							if (this.i + 4 > this.s.length()) {
								throw new IllegalArgumentException("bad \\u escape");
							}
							sb.append((char) Integer.parseInt(this.s.substring(this.i, this.i + 4), 16));
							this.i += 4;
						}
						default -> throw new IllegalArgumentException("bad escape \\" + e);
					}
				} else {
					sb.append(c);
				}
			}
		}

		Double number() {
			int start = this.i;
			while (this.i < this.s.length() && "+-0123456789.eE".indexOf(this.s.charAt(this.i)) >= 0) {
				this.i++;
			}
			if (start == this.i) {
				throw new IllegalArgumentException("unexpected '" + this.s.charAt(start) + "' at " + start);
			}
			return Double.parseDouble(this.s.substring(start, this.i));
		}
	}
}
