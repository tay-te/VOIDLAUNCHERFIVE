package dev.voidmc.atlas.core.social;

import dev.voidmc.atlas.api.AtlasSocialTransport;
import java.util.function.BiConsumer;

/**
 * The transport Atlas uses until a real one is installed: it sends nothing, receives nothing and is never
 * {@link #available()}, so friend features stay hidden.
 */
public final class NoopTransport implements AtlasSocialTransport {
	public static final NoopTransport INSTANCE = new NoopTransport();

	private NoopTransport() {
	}

	@Override
	public void publish(String topic, byte[] payload) {
	}

	@Override
	public AutoCloseable subscribe(String topic, BiConsumer<String, byte[]> handler) {
		return () -> {
		};
	}

	@Override
	public void presence(Presence state) {
	}

	@Override
	public boolean available() {
		return false;
	}
}
