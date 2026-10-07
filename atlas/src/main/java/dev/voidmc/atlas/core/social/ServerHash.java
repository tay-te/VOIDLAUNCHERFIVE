package dev.voidmc.atlas.core.social;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Which server a player is on, as a hash of its address, so friends can tell they are on the same one without the
 * address itself being sent around. The address is normalised first (case, spaces, a trailing dot, the default port),
 * so {@code Play.Example.com} and {@code play.example.com:25565} hash the same.
 */
public final class ServerHash {
	public static final int DEFAULT_PORT = 25565;

	private ServerHash() {
	}

	/** 32 hex digits (the first 128 bits of SHA-256) for a server address; null for a blank address. */
	public static String of(String address) {
		String norm = normalise(address);
		if (norm == null) {
			return null;
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(("atlas-server:" + norm).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 16);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** {@code host:port} in lower case, with the default port added when there is none; null for blank. */
	static String normalise(String address) {
		if (address == null) {
			return null;
		}
		String s = address.strip().toLowerCase(Locale.ROOT);
		if (s.isEmpty()) {
			return null;
		}
		String host;
		String port;
		if (s.startsWith("[")) {
			int close = s.indexOf(']');
			host = close > 0 ? s.substring(0, close + 1) : s;
			port = close > 0 && close + 1 < s.length() && s.charAt(close + 1) == ':' ? s.substring(close + 2) : "";
		} else {
			int colon = s.lastIndexOf(':');
			boolean onePort = colon > 0 && s.indexOf(':') == colon;
			host = onePort ? s.substring(0, colon) : s;
			port = onePort ? s.substring(colon + 1) : "";
		}
		while (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		if (port.isEmpty()) {
			port = Integer.toString(DEFAULT_PORT);
		}
		return host + ":" + port;
	}
}
