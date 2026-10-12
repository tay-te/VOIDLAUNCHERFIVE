package dev.voidmc.reel.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.voidmc.reel.Reel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/**
 * Where rendered frames go: each capture copies the main colour target into a mapped GPU buffer (the read comes
 * back a frame or two later, as vanilla's screenshots do), blends the samples of one frame together for motion
 * blur, and writes the finished frame as a PNG on a writer thread so the game never waits on the disk.
 */
final class FrameSink {
	private static final int USAGE_READBACK = GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST;

	private final int samples;
	private final int supersample;
	private final ExecutorService writers = Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "Reel frame writer");
		t.setDaemon(true);
		return t;
	});
	private final AtomicInteger readbacks = new AtomicInteger();
	private final AtomicInteger writing = new AtomicInteger();
	private final AtomicInteger written = new AtomicInteger();
	private final Map<Integer, Blend> blends = new HashMap<>();
	private volatile String failure;

	FrameSink(int samples, int supersample) {
		this.samples = samples;
		this.supersample = supersample;
	}

	/** Frames read back or being written but not on disk yet: the director waits while this is high. */
	int backlog() {
		return this.readbacks.get() + this.writing.get();
	}

	boolean idle() {
		return this.backlog() == 0;
	}

	int written() {
		return this.written.get();
	}

	String failure() {
		return this.failure;
	}

	/** Queues a read of the target as sample `sample` of the frame that will be written to `file`. */
	void capture(RenderTarget target, int index, Path file) {
		GpuTexture texture = target.getColorTexture();
		if (texture == null) {
			this.failure = "the main render target has no colour texture";
			return;
		}
		int width = target.width;
		int height = target.height;
		int pixel = texture.getFormat().blockSize();
		GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Reel frame", USAGE_READBACK, (long) width * height * pixel);
		this.readbacks.incrementAndGet();
		RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
			try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
				byte[] rgb = read(view.data(), width, height, pixel);
				int s = this.supersample;
				if (s > 1) {
					rgb = shrink(rgb, width, height, s);
				}
				this.deliver(index, file, width / s, height / s, rgb);
			} catch (RuntimeException e) {
				this.failure = "reading a frame back failed: " + e;
				Reel.LOG.error("[reel] frame read failed", e);
			} finally {
				buffer.close();
				this.readbacks.decrementAndGet();
			}
		}, 0);
	}

	/** RGB bytes, top row first (the GPU's rows run bottom up). */
	private static byte[] read(ByteBuffer data, int width, int height, int pixel) {
		byte[] rgb = new byte[width * height * 3];
		for (int y = 0; y < height; y++) {
			int src = (height - 1 - y) * width * pixel;
			int dst = y * width * 3;
			for (int x = 0; x < width; x++) {
				rgb[dst++] = data.get(src);
				rgb[dst++] = data.get(src + 1);
				rgb[dst++] = data.get(src + 2);
				src += pixel;
			}
		}
		return rgb;
	}

	/** Box-filters an image down by a whole factor: each output pixel the mean of an s x s block. */
	private static byte[] shrink(byte[] rgb, int width, int height, int s) {
		int w = width / s;
		int h = height / s;
		byte[] out = new byte[w * h * 3];
		int area = s * s;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int r = 0;
				int g = 0;
				int b = 0;
				for (int dy = 0; dy < s; dy++) {
					int row = ((y * s + dy) * width + x * s) * 3;
					for (int dx = 0; dx < s; dx++) {
						r += rgb[row] & 0xFF;
						g += rgb[row + 1] & 0xFF;
						b += rgb[row + 2] & 0xFF;
						row += 3;
					}
				}
				int o = (y * w + x) * 3;
				out[o] = (byte) ((r + area / 2) / area);
				out[o + 1] = (byte) ((g + area / 2) / area);
				out[o + 2] = (byte) ((b + area / 2) / area);
			}
		}
		return out;
	}

	private void deliver(int index, Path file, int width, int height, byte[] rgb) {
		if (this.samples <= 1) {
			this.write(file, width, height, rgb);
			return;
		}
		Blend blend = this.blends.computeIfAbsent(index, i -> new Blend(new int[rgb.length]));
		for (int i = 0; i < rgb.length; i++) {
			blend.sum[i] += rgb[i] & 0xFF;
		}
		if (++blend.count == this.samples) {
			this.blends.remove(index);
			byte[] out = new byte[rgb.length];
			int half = this.samples / 2;
			for (int i = 0; i < out.length; i++) {
				out[i] = (byte) ((blend.sum[i] + half) / this.samples);
			}
			this.write(file, width, height, out);
		}
	}

	private void write(Path file, int width, int height, byte[] rgb) {
		this.writing.incrementAndGet();
		this.writers.execute(() -> {
			try {
				BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
				byte[] bgr = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
				for (int i = 0; i < rgb.length; i += 3) {
					bgr[i] = rgb[i + 2];
					bgr[i + 1] = rgb[i + 1];
					bgr[i + 2] = rgb[i];
				}
				Files.createDirectories(file.getParent());
				ImageIO.write(image, "png", file.toFile());
				this.written.incrementAndGet();
			} catch (IOException | RuntimeException e) {
				this.failure = "writing " + file.getFileName() + " failed: " + e;
				Reel.LOG.error("[reel] frame write failed", e);
			} finally {
				this.writing.decrementAndGet();
			}
		});
	}

	void close() {
		this.writers.shutdown();
		try {
			this.writers.awaitTermination(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static final class Blend {
		final int[] sum;
		int count;

		Blend(int[] sum) {
			this.sum = sum;
		}
	}
}
