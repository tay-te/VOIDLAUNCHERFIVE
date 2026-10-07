package dev.voidmc.atlas.core.social;

import dev.voidmc.atlas.api.AtlasSocialTransport;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * An in-memory transport for tests: several players {@link Hub#join join} one {@link Hub}, and what one publishes
 * waits in the hub's queue until {@link Hub#deliver()} hands it to every other member subscribed to its topic. Like
 * a real backend it is a dumb pipe: it never filters by privacy or server, which is Atlas's job on each end.
 */
public final class LoopbackTransport implements AtlasSocialTransport, AutoCloseable {
	private final Hub hub;
	private final String player;
	private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
	private volatile boolean open = true;

	private LoopbackTransport(Hub hub, String player) {
		this.hub = hub;
		this.player = player;
	}

	/** The player this end belongs to. */
	@Override
	public String localPlayer() {
		return this.player;
	}

	@Override
	public java.util.Collection<String> party() {
		// everyone on the loopback hub is in one party, for tests
		java.util.List<String> all = new java.util.ArrayList<>(this.hub.members());
		all.remove(this.player);
		return all;
	}

	public String player() {
		return this.player;
	}

	@Override
	public void publish(String topic, byte[] payload) {
		if (this.open) {
			this.hub.enqueue(this.player, topic, payload.clone());
		}
	}

	@Override
	public AutoCloseable subscribe(String topic, BiConsumer<String, byte[]> handler) {
		Subscription s = new Subscription(Objects.requireNonNull(topic, "topic"), Objects.requireNonNull(handler, "handler"));
		if (this.open) {
			this.subscriptions.add(s);
		}
		return () -> this.subscriptions.remove(s);
	}

	@Override
	public void presence(Presence state) {
		this.hub.presence(this.player, state);
	}

	@Override
	public boolean available() {
		return this.open;
	}

	/** Leaves the hub: nothing more is sent or received. */
	@Override
	public void close() {
		this.open = false;
		this.subscriptions.clear();
		this.hub.leave(this.player, this);
	}

	private record Subscription(String topic, BiConsumer<String, byte[]> handler) {
	}

	private record Delivery(String from, String topic, byte[] payload) {
	}

	/** The shared middle every loopback player joins. Thread-safe. */
	public static final class Hub {
		private final Map<String, LoopbackTransport> members = new LinkedHashMap<>();
		private final Map<String, Presence> presence = new HashMap<>();
		private final Map<String, Long> bytes = new HashMap<>();
		private final ArrayDeque<Delivery> queue = new ArrayDeque<>();

		/** A new end for {@code player}; a player who joins again replaces their old end. */
		public synchronized LoopbackTransport join(String player) {
			LoopbackTransport end = new LoopbackTransport(this, Objects.requireNonNull(player, "player"));
			LoopbackTransport old = this.members.put(player, end);
			if (old != null) {
				old.open = false;
				old.subscriptions.clear();
			}
			return end;
		}

		/** Messages published and not yet delivered. */
		public synchronized int pending() {
			return this.queue.size();
		}

		/** Every payload byte a player has published so far. */
		public synchronized long bytesPublished(String player) {
			return this.bytes.getOrDefault(player, 0L);
		}

		/** What a player last said with {@link #presence(Presence)}, or null. */
		public synchronized Presence presenceOf(String player) {
			return this.presence.get(player);
		}

		public synchronized List<String> members() {
			return List.copyOf(this.members.keySet());
		}

		/**
		 * Delivers what is queued now (anything published by the handlers meanwhile waits for the next call), in
		 * publish order. Returns how many handler calls were made.
		 */
		public int deliver() {
			List<Delivery> batch;
			List<LoopbackTransport> ends;
			synchronized (this) {
				batch = new ArrayList<>(this.queue);
				this.queue.clear();
				ends = new ArrayList<>(this.members.values());
			}
			int calls = 0;
			for (Delivery d : batch) {
				for (LoopbackTransport end : ends) {
					if (!end.open || end.player.equals(d.from)) {
						continue;
					}
					for (Subscription s : end.subscriptions) {
						if (AtlasSocialTransport.matches(s.topic, d.topic)) {
							s.handler.accept(d.from, d.payload.clone());
							calls++;
						}
					}
				}
			}
			return calls;
		}

		/** Delivers until nothing is queued (at most {@code rounds} times, for handlers that keep answering). */
		public int deliverAll(int rounds) {
			int calls = 0;
			for (int i = 0; i < rounds && pending() > 0; i++) {
				calls += deliver();
			}
			return calls;
		}

		private synchronized void enqueue(String from, String topic, byte[] payload) {
			this.queue.add(new Delivery(from, topic, payload));
			this.bytes.merge(from, (long) payload.length, Long::sum);
		}

		private synchronized void presence(String player, Presence state) {
			this.presence.put(player, state);
		}

		private synchronized void leave(String player, LoopbackTransport end) {
			this.members.remove(player, end);
		}
	}
}
