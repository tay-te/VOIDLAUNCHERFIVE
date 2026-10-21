package dev.voidmc.bestiary.studio;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/** The studio's pictures: animated GIFs, labelled strips and side-by-side composites (plain Java2D and ImageIO). */
final class Images {
	private Images() {
	}

	static BufferedImage read(Path p) throws IOException {
		return ImageIO.read(p.toFile());
	}

	static BufferedImage scale(BufferedImage in, int width) {
		if (in.getWidth() == width) {
			return toRgb(in);
		}
		int h = Math.round(in.getHeight() * width / (float) in.getWidth());
		BufferedImage out = new BufferedImage(width, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(in, 0, 0, width, h, null);
		g.dispose();
		return out;
	}

	static BufferedImage toRgb(BufferedImage in) {
		if (in.getType() == BufferedImage.TYPE_INT_RGB) {
			return in;
		}
		BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(in, 0, 0, null);
		g.dispose();
		return out;
	}

	/** A small dark label in the top-left corner. */
	static BufferedImage label(BufferedImage img, String text) {
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		int size = Math.max(12, img.getWidth() / 50);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
		int w = g.getFontMetrics().stringWidth(text);
		g.setColor(new Color(0, 0, 0, 170));
		g.fillRect(0, 0, w + size, size + size / 2);
		g.setColor(Color.WHITE);
		g.drawString(text, size / 2, size + size / 8);
		g.dispose();
		return img;
	}

	/** Tiles in rows of {@code cols}, each scaled to {@code width}. */
	static BufferedImage grid(List<BufferedImage> tiles, int cols, int width) {
		int rows = (tiles.size() + cols - 1) / cols;
		int th = 0;
		for (BufferedImage t : tiles) {
			th = Math.max(th, Math.round(t.getHeight() * width / (float) t.getWidth()));
		}
		BufferedImage out = new BufferedImage(cols * width, rows * th, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setColor(new Color(24, 24, 24));
		g.fillRect(0, 0, out.getWidth(), out.getHeight());
		for (int i = 0; i < tiles.size(); i++) {
			g.drawImage(scale(tiles.get(i), width), (i % cols) * width, (i / cols) * th, null);
		}
		g.dispose();
		return out;
	}

	static void writeJpg(BufferedImage img, Path p) throws IOException {
		Files.createDirectories(p.getParent());
		ImageIO.write(toRgb(img), "jpg", p.toFile());
	}

	static void writePng(BufferedImage img, Path p) throws IOException {
		Files.createDirectories(p.getParent());
		ImageIO.write(img, "png", p.toFile());
	}

	/** An animated GIF, {@code delayMs} per frame (rounded to centiseconds), the last frame held {@code holdMs}, looping. */
	static void gif(List<BufferedImage> frames, Path out, int delayMs, int holdMs) throws IOException {
		Files.createDirectories(out.getParent());
		Files.deleteIfExists(out);
		ImageWriter w = ImageIO.getImageWritersByFormatName("gif").next();
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(out.toFile())) {
			w.setOutput(ios);
			w.prepareWriteSequence(null);
			ImageWriteParam param = w.getDefaultWriteParam();
			for (int i = 0; i < frames.size(); i++) {
				BufferedImage f = toRgb(frames.get(i));
				IIOMetadata meta = w.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(f), param);
				String fmt = meta.getNativeMetadataFormatName();
				IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(fmt);
				IIOMetadataNode gce = child(root, "GraphicControlExtension");
				gce.setAttribute("disposalMethod", "none");
				gce.setAttribute("userInputFlag", "FALSE");
				gce.setAttribute("transparentColorFlag", "FALSE");
				gce.setAttribute("delayTime", String.valueOf(Math.max(2, Math.round((i == frames.size() - 1 ? holdMs : delayMs) / 10f))));
				gce.setAttribute("transparentColorIndex", "0");
				if (i == 0) {
					IIOMetadataNode apps = child(root, "ApplicationExtensions");
					IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
					app.setAttribute("applicationID", "NETSCAPE");
					app.setAttribute("authenticationCode", "2.0");
					app.setUserObject(new byte[] {1, 0, 0});
					apps.appendChild(app);
				}
				meta.setFromTree(fmt, root);
				w.writeToSequence(new IIOImage(f, null, meta), param);
			}
			w.endWriteSequence();
		} finally {
			w.dispose();
		}
	}

	private static IIOMetadataNode child(IIOMetadataNode root, String name) {
		for (int i = 0; i < root.getLength(); i++) {
			if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
				return (IIOMetadataNode) root.item(i);
			}
		}
		IIOMetadataNode n = new IIOMetadataNode(name);
		root.appendChild(n);
		return n;
	}
}
