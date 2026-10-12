package dev.voidmc.reel.tool;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Tiles frames into one image, four across, each labelled with its file name: a whole shot at a glance. Made after
 * every stills job; for a movie, `./gradlew contactSheet -Pshot=NAME [-Pevery=N]` picks every Nth frame (default:
 * about sixteen spread over the shot).
 *
 * <p>Arguments: renders folder, shot name, every (0 for automatic).
 */
public final class ContactSheet {
	private static final int COLUMNS = 4;
	private static final int TILE_WIDTH = 480;

	private ContactSheet() {
	}

	public static void main(String[] args) throws IOException {
		Path shot = Path.of(args[0]).resolve(args[1]);
		int every = args.length > 2 ? Integer.parseInt(args[2]) : 0;
		Path frames = shot.resolve("frames");
		if (!Files.isDirectory(frames)) {
			frames = shot.resolve("stills");
		}
		Path file = make(frames, args[1], shot.resolve("contact.png"), every);
		System.out.println("contact sheet: " + file);
	}

	public static Path make(Path folder, String title, Path out) throws IOException {
		return make(folder, title, out, 0);
	}

	public static Path make(Path folder, String title, Path out, int every) throws IOException {
		List<Path> all;
		try (Stream<Path> files = Files.list(folder)) {
			all = files.filter(p -> p.getFileName().toString().endsWith(".png")).sorted().toList();
		}
		if (all.isEmpty()) {
			throw new IOException("no frames in " + folder);
		}
		int step = every > 0 ? every : Math.max(1, (int) Math.ceil(all.size() / 16.0));
		List<Path> picked = new java.util.ArrayList<>();
		for (int i = 0; i < all.size(); i += step) {
			picked.add(all.get(i));
		}
		if (!picked.contains(all.getLast())) {
			picked.add(all.getLast());
		}
		BufferedImage first = ImageIO.read(picked.getFirst().toFile());
		int tileHeight = (int) Math.round((double) TILE_WIDTH * first.getHeight() / first.getWidth());
		int label = 22;
		int rows = (picked.size() + COLUMNS - 1) / COLUMNS;
		int gap = 8;
		int header = 34;
		BufferedImage sheet = new BufferedImage(COLUMNS * (TILE_WIDTH + gap) + gap, header + rows * (tileHeight + label + gap) + gap, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = sheet.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0x1E1E1C));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		g.setColor(new Color(0xEDE8DD));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
		g.drawString(title + "  (" + picked.size() + " of " + all.size() + " frames)", gap, 24);
		g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		for (int i = 0; i < picked.size(); i++) {
			BufferedImage frame = i == 0 ? first : ImageIO.read(picked.get(i).toFile());
			int x = gap + (i % COLUMNS) * (TILE_WIDTH + gap);
			int y = header + (i / COLUMNS) * (tileHeight + label + gap);
			g.drawImage(frame, x, y, TILE_WIDTH, tileHeight, null);
			g.setColor(new Color(0xBDB6A8));
			g.drawString(picked.get(i).getFileName().toString().replace(".png", ""), x + 2, y + tileHeight + 16);
		}
		g.dispose();
		Files.createDirectories(out.getParent());
		ImageIO.write(sheet, "png", out.toFile());
		return out;
	}
}
