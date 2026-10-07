package dev.voidmc.atlas.core.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Reading the small JSON messages friends send: strict UTF-8, one object, typed fields; any doubt is an IOException. */
final class SocialJson {
	private SocialJson() {
	}

	/** The object in a payload of at most {@code max} bytes whose {@code t} is {@code type} and {@code v} at most {@code version}. */
	static JsonObject read(byte[] payload, int max, String type, int version) throws IOException {
		if (payload == null || payload.length == 0) {
			throw new IOException("empty");
		}
		if (payload.length > max) {
			throw new IOException("too large (" + payload.length + " bytes)");
		}
		String text;
		try {
			text = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(payload))
				.toString();
		} catch (CharacterCodingException e) {
			throw new IOException("not UTF-8");
		}
		JsonElement root;
		try {
			root = JsonParser.parseString(text);
		} catch (RuntimeException | StackOverflowError e) {
			throw new IOException("not JSON");
		}
		if (!root.isJsonObject()) {
			throw new IOException("not an object");
		}
		JsonObject o = root.getAsJsonObject();
		if (!type.equals(string(o, "t"))) {
			throw new IOException("not a " + type + " message");
		}
		Double v = number(o, "v");
		if (v == null || v != Math.floor(v) || v < 1 || v > version) {
			throw new IOException("unsupported version " + v);
		}
		return o;
	}

	static String string(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
	}

	/** A finite number, or null. */
	static Double number(JsonObject o, String key) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive()) {
			return null;
		}
		JsonPrimitive p = e.getAsJsonPrimitive();
		if (!p.isNumber()) {
			return null;
		}
		try {
			double d = p.getAsDouble();
			return Double.isFinite(d) ? d : null;
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	/** A whole number read exactly (epoch milliseconds keep every digit), or null. */
	static Long whole(JsonObject o, String key) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		String text = e.getAsString();
		if (!text.matches("-?\\d{1,18}")) {
			return null;
		}
		return Long.parseLong(text);
	}

	/** A non-blank string of at most {@code max} characters, or null. */
	static String id(JsonObject o, String key, int max) {
		String s = string(o, key);
		return s == null || s.isBlank() || s.length() > max ? null : s;
	}
}
