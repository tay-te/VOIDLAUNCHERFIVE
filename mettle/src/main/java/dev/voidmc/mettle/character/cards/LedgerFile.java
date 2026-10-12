package dev.voidmc.mettle.character.cards;

import dev.voidmc.mettle.Mettle;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Where the card ledger lives on disk ({@code <world>/data/mettle/cards.dat}, the same gzip NBT as before, so old worlds
 * load). Mettle reads and writes it itself instead of through vanilla's saved-data storage, which silently starts an
 * empty ledger when the file doesn't parse and writes it in place:
 * <ul>
 *   <li>a save goes to a temp file first, then replaces {@code cards.dat}; the previous file stays as {@code cards.dat_old};</li>
 *   <li>a {@code cards.dat} that can't be read is copied aside ({@code cards.dat.unreadable-<time>}) and the backup is
 *   loaded; when neither can be read the server refuses to start rather than start an empty ledger over the cards;</li>
 *   <li>it is saved whenever a player file is (before it: the ledger is never older than a player file), right after a
 *   trade, a Mythic and a migration, and with the world.</li>
 * </ul>
 */
public final class LedgerFile {
	private static @Nullable MinecraftServer owner;
	private static @Nullable CardLedger ledger;

	private LedgerFile() {
	}

	public static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.DATA).resolve(Mettle.ID).resolve("cards.dat");
	}

	/** The server's ledger, loaded on first use (at server start). */
	public static synchronized CardLedger get(MinecraftServer server) {
		if (owner != server || ledger == null) {
			ledger = load(file(server));
			owner = server;
		}
		return ledger;
	}

	/** Saves and forgets the ledger (server stopped). */
	public static synchronized void close(MinecraftServer server) {
		if (owner == server && ledger != null) {
			save(server);
		}
		owner = null;
		ledger = null;
	}

	/** Writes the ledger if anything changed since the last save. Server thread. Returns whether it is on disk now. */
	public static synchronized boolean save(MinecraftServer server) {
		if (owner != server || ledger == null || !ledger.isDirty()) {
			return true;
		}
		try {
			write(ledger, file(server));
			ledger.setDirty(false);
			return true;
		} catch (IOException | RuntimeException e) {
			Mettle.LOGGER.error("Mettle: the card ledger could not be saved to {} (it stays in memory and is tried again on the next save)", file(server), e);
			return false;
		}
	}

	/**
	 * Makes a change that touches the ledger and players' files durable now, in that order: the ledger, then each
	 * player's file (a trade's two sides, a Mythic's holder, a migrated player). Players not in the player list (a test's
	 * stand-in) have no file to save.
	 */
	public static void commit(MinecraftServer server, net.minecraft.server.level.ServerPlayer... players) {
		save(server);
		for (net.minecraft.server.level.ServerPlayer p : players) {
			if (p != null && server.getPlayerList().getPlayer(p.getUUID()) == p) {
				((dev.voidmc.mettle.mixin.core.PlayerListAccess) server.getPlayerList()).mettle$save(p);
			}
		}
	}

	// ---------------------------------------------------------------- reading

	/**
	 * Reads the ledger: {@code cards.dat}, else its backup {@code cards.dat_old}, else (neither exists) an empty ledger.
	 * A file that exists but can't be read is never replaced by an empty ledger: the backup is used, or start-up stops.
	 */
	public static CardLedger load(Path file) {
		Path backup = backup(file);
		boolean main = Files.exists(file);
		boolean old = Files.exists(backup);
		if (!main && !old) {
			return new CardLedger();
		}
		Exception mainError = null;
		if (main) {
			try {
				return read(file);
			} catch (Exception e) {
				mainError = e;
				Path aside = file.resolveSibling(file.getFileName() + ".unreadable-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
				try {
					Files.copy(file, aside);
				} catch (IOException ignored) {
					// The copy is only for whoever looks into it; loading goes on.
				}
				Mettle.LOGGER.error("Mettle: the card ledger {} can't be read ({}); a copy is kept as {}; trying the backup {}", file, e.toString(),
					aside.getFileName(), backup.getFileName());
			}
		} else {
			Mettle.LOGGER.warn("Mettle: {} is missing but its backup {} is there (a save was cut short); loading the backup", file, backup.getFileName());
		}
		if (old) {
			try {
				CardLedger l = read(backup);
				Mettle.LOGGER.warn("Mettle: loaded the card ledger from its backup {} ({}); changes after that save come back from players' own copies as they join",
					backup, l);
				l.setDirty();
				return l;
			} catch (Exception e) {
				Mettle.LOGGER.error("Mettle: the card ledger backup {} can't be read either ({})", backup, e.toString());
			}
		}
		throw new IllegalStateException("VOID Mettle: the card ledger " + file + " can't be read" + (old ? " and neither can its backup " + backup.getFileName() : "")
			+ " (" + (mainError == null ? "?" : mainError.toString()) + "). The server won't start, so no player's cards are wiped. Restore data/mettle"
			+ " from a world backup, or move cards.dat and cards.dat_old out of data/mettle to start an empty ledger: players' cards then come back"
			+ " from their own saved copies as they join (Mythic claims and released cards are forgotten).");
	}

	static CardLedger read(Path file) throws IOException {
		CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		Tag data = root.get("data");
		if (data == null) {
			throw new IOException("no data tag");
		}
		return CardLedger.CODEC.parse(NbtOps.INSTANCE, data).getOrThrow(IOException::new);
	}

	// ---------------------------------------------------------------- writing

	/** The ledger as it is saved: {@code {data: ..., DataVersion: n}}. */
	public static CompoundTag encode(CardLedger l) {
		CompoundTag root = new CompoundTag();
		root.put("data", CardLedger.CODEC.encodeStart(NbtOps.INSTANCE, l).getOrThrow(IllegalStateException::new));
		NbtUtils.addCurrentDataVersion(root);
		return root;
	}

	/** Encodes and writes atomically: a temp file in the same folder, then it replaces {@code file}, the old one kept as backup. */
	public static void write(CardLedger l, Path file) throws IOException {
		CompoundTag root = encode(l);
		Files.createDirectories(file.getParent());
		Path tmp = Files.createTempFile(file.getParent(), "cards-", ".tmp");
		try {
			try (OutputStream out = Files.newOutputStream(tmp);
				 DataOutputStream data = new DataOutputStream(new BufferedOutputStream(new FastGzip(out), 1 << 16))) {
				NbtIo.write(root, data);
			}
			if (!Util.safeReplaceOrMoveFile(file, tmp, backup(file), false)) {
				throw new IOException("could not move " + tmp.getFileName() + " into place");
			}
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	static Path backup(Path file) {
		return file.resolveSibling(file.getFileName() + "_old");
	}

	/** Gzip at the fastest level: the ledger is written often, a few percent bigger is fine. */
	private static final class FastGzip extends GZIPOutputStream {
		FastGzip(OutputStream out) throws IOException {
			super(out, 1 << 16);
			this.def.setLevel(Deflater.BEST_SPEED);
		}
	}
}
