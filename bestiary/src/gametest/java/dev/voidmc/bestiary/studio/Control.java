package dev.voidmc.bestiary.studio;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * The studio's two inputs: a control port (HTTP on localhost: POST a command line, get the result; tools/studio.sh
 * wraps it) and a file watcher over the motion sources and rig projects (an edit marks the studio for a reload).
 * Commands run on the game-test thread, one at a time, in arrival order.
 */
final class Control implements AutoCloseable {
	record Request(String line, CompletableFuture<String> reply) {
	}

	private final HttpServer http;
	private final LinkedBlockingQueue<Request> queue = new LinkedBlockingQueue<>();
	private final WatchService watch;
	private final AtomicLong lastChange = new AtomicLong();
	private final Thread watcher;

	Control(int port, Path... watched) throws IOException {
		this.http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
		this.http.createContext("/", ex -> {
			String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
			if (body.isEmpty() && ex.getRequestURI().getQuery() != null) {
				body = java.net.URLDecoder.decode(ex.getRequestURI().getQuery(), StandardCharsets.UTF_8);
			}
			CompletableFuture<String> f = new CompletableFuture<>();
			this.queue.add(new Request(body.isEmpty() ? "status" : body, f));
			String out;
			try {
				out = f.get(15, TimeUnit.MINUTES);
			} catch (Exception e) {
				out = "ERROR " + e;
			}
			byte[] bytes = (out + "\n").getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(200, bytes.length);
			try (OutputStream os = ex.getResponseBody()) {
				os.write(bytes);
			}
		});
		this.http.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {
			Thread t = new Thread(r, "fray-studio-http");
			t.setDaemon(true);
			return t;
		}));
		this.http.start();
		this.watch = FileSystems.getDefault().newWatchService();
		for (Path root : watched) {
			if (!Files.isDirectory(root)) {
				continue;
			}
			try (Stream<Path> dirs = Files.walk(root)) {
				for (Path d : dirs.filter(Files::isDirectory).toList()) {
					d.register(this.watch, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
				}
			}
		}
		this.watcher = new Thread(() -> {
			try {
				while (!Thread.currentThread().isInterrupted()) {
					WatchKey k = this.watch.take();
					if (!k.pollEvents().isEmpty()) {
						this.lastChange.set(System.currentTimeMillis());
					}
					k.reset();
				}
			} catch (InterruptedException | java.nio.file.ClosedWatchServiceException e) {
				// closing
			}
		}, "fray-studio-watch");
		this.watcher.setDaemon(true);
		this.watcher.start();
	}

	Request poll() {
		return this.queue.poll();
	}

	/** True once, 300 ms after the last change to a watched file (editors write in bursts). */
	boolean changed() {
		long t = this.lastChange.get();
		if (t != 0 && System.currentTimeMillis() - t > 300) {
			this.lastChange.compareAndSet(t, 0);
			return true;
		}
		return false;
	}

	@Override
	public void close() {
		this.http.stop(0);
		this.watcher.interrupt();
		try {
			this.watch.close();
		} catch (IOException e) {
			// closing
		}
		Request r;
		while ((r = this.queue.poll()) != null) {
			r.reply().complete("studio closed");
		}
	}
}
