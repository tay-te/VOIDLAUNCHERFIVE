package dev.voidmc.mettle.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.mettle.Mettle;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * A per-world secret ({@code data/mettle/secret.dat}), made once from a secure random source and never sent to a client.
 * Every roll a player could otherwise work out in advance is salted with it: draft hands and the drawn-Mythic roll (a
 * player's seed is public, their UUID), scar seeds and fusion results. Without it, anyone could list on any server which
 * deal will carry their Mythic, or steer a fusion by the order they send the cards in.
 *
 * <p>{@link #hash} is one-way (SHA-256), so seeds a client does see (a card's scar seed) don't give the secret away.
 * A lost file only means a new secret: hands already dealt stay as they are.
 */
public final class MettleSecret extends SavedData {
	public static final Codec<MettleSecret> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.fieldOf("salt").forGetter(s -> s.salt)
	).apply(i, MettleSecret::new));

	public static final SavedDataType<MettleSecret> TYPE = new SavedDataType<>(Mettle.id("secret"), MettleSecret::fresh, CODEC, null);

	private static final SecureRandom RANDOM = new SecureRandom();

	private final long salt;

	private MettleSecret(long salt) {
		this.salt = salt;
	}

	private static MettleSecret fresh() {
		// Game tests may pin it (-Dmettle.salt=...) so their deals repeat; never set on a real server.
		Long pinned = Long.getLong("mettle.salt");
		MettleSecret s = new MettleSecret(pinned != null ? pinned : RANDOM.nextLong());
		s.setDirty();
		return s;
	}

	/** The world's salt (server only). */
	public static long salt(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE).salt;
	}

	/** A seed from the salt and the given parts, one-way: knowing the result says nothing of the salt. */
	public static long hash(long salt, String purpose, long... parts) {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			ByteBuffer b = ByteBuffer.allocate(8 * (parts.length + 1));
			b.putLong(salt);
			for (long p : parts) {
				b.putLong(p);
			}
			sha.update(b.array());
			sha.update(purpose.getBytes(StandardCharsets.UTF_8));
			return ByteBuffer.wrap(sha.digest()).getLong();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
