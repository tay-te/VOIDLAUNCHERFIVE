package dev.voidmc.lod.net;

import dev.voidmc.lod.source.TreeKinds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * VOID LOD's conversation with a server that has it: the server says what it can describe ({@link Hello}); the client
 * asks for tiles of the land past its view ({@link TileRequest}); the server answers each with what its generator says
 * there ({@link TileFacts}), as facts, not colours: a dedicated server has no textures, and the client colours them its
 * own way, so the far land on a server looks as it does in singleplayer. The seed never leaves the server.
 */
public final class LodNet {
	/** Bumped when the facts change shape: a client and server of different versions keep to what they each have. */
	public static final int VERSION = 3;
	/** The most a tile's facts may take on the wire: 66 x 66 columns of 40 bytes, deflated, with room to spare. */
	private static final int MAX_FACTS = 1 << 20;

	private LodNet() {
	}

	/**
	 * The server's offer, on joining (and on changing dimension): which version, the dimension it describes and its
	 * sea level, its strata (badlands bands, as block states written as commands write them), and the trees each of
	 * its biomes places: a client's copy of the biomes comes without their features. Expanse worlds only.
	 */
	public record Hello(int version, String dimension, int seaLevel, List<String> strata, Map<String, List<TreeKinds.Kind>> trees)
		implements CustomPacketPayload {
		public static final Type<Hello> TYPE = new Type<>(Identifier.fromNamespaceAndPath("void_lod", "hello"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = StreamCodec.of((buf, h) -> {
			buf.writeVarInt(h.version);
			buf.writeUtf(h.dimension);
			buf.writeVarInt(h.seaLevel);
			buf.writeVarInt(h.strata.size());
			for (String s : h.strata) {
				buf.writeUtf(s);
			}
			buf.writeVarInt(h.trees.size());
			for (Map.Entry<String, List<TreeKinds.Kind>> biome : h.trees.entrySet()) {
				buf.writeUtf(biome.getKey());
				buf.writeVarInt(biome.getValue().size());
				for (TreeKinds.Kind kind : biome.getValue()) {
					buf.writeUtf(BlockStateParser.serialize(kind.leaves()));
					buf.writeFloat((float) kind.count());
					buf.writeFloat((float) kind.height());
					buf.writeBoolean(kind.conifer());
				}
			}
		}, buf -> {
			int version = buf.readVarInt();
			String dimension = buf.readUtf();
			int seaLevel = buf.readVarInt();
			int n = buf.readVarInt();
			List<String> strata = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				strata.add(buf.readUtf());
			}
			Map<String, List<TreeKinds.Kind>> trees = new HashMap<>();
			if (version == VERSION) {
				int biomes = buf.readVarInt();
				for (int b = 0; b < biomes; b++) {
					String name = buf.readUtf();
					int kinds = buf.readVarInt();
					List<TreeKinds.Kind> list = new ArrayList<>(kinds);
					for (int k = 0; k < kinds; k++) {
						String leaves = buf.readUtf();
						float count = buf.readFloat();
						float height = buf.readFloat();
						boolean conifer = buf.readBoolean();
						BlockState state;
						try {
							state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, leaves, false).blockState();
						} catch (Exception e) {
							// leaves of a mod this client lacks
							state = Blocks.OAK_LEAVES.defaultBlockState();
						}
						list.add(new TreeKinds.Kind(state, count, height, conifer));
					}
					trees.put(name, list);
				}
			}
			return new Hello(version, dimension, seaLevel, strata, trees);
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A tile's grid of columns: {@code size} a side, {@code cell} blocks apart, sample (0, 0) (inside the border) at (x0, z0). */
	public record TileRequest(long key, int x0, int z0, int cell, int border, int size) implements CustomPacketPayload {
		public static final Type<TileRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("void_lod", "tile_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TileRequest> CODEC = StreamCodec.of((buf, r) -> {
			buf.writeLong(r.key);
			buf.writeInt(r.x0);
			buf.writeInt(r.z0);
			buf.writeVarInt(r.cell);
			buf.writeVarInt(r.border);
			buf.writeVarInt(r.size);
		}, buf -> new TileRequest(buf.readLong(), buf.readInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * The facts for a requested grid, deflated: a table of biome names, then per column its {@code ExpanseFacts}, its
	 * biome's index and its strata offset. Empty {@code data}: the server cannot describe it (another dimension).
	 */
	public record TileFacts(long key, int size, byte[] data) implements CustomPacketPayload {
		public static final Type<TileFacts> TYPE = new Type<>(Identifier.fromNamespaceAndPath("void_lod", "tile_facts"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TileFacts> CODEC = StreamCodec.of((buf, f) -> {
			buf.writeLong(f.key);
			buf.writeVarInt(f.size);
			buf.writeByteArray(f.data);
		}, buf -> new TileFacts(buf.readLong(), buf.readVarInt(), buf.readByteArray(MAX_FACTS)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The trees of chunk (cx, cz), asked of a server that places them (its world's own, as singleplayer has them). */
	public record TreeRequest(int cx, int cz) implements CustomPacketPayload {
		public static final Type<TreeRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("void_lod", "tree_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TreeRequest> CODEC = StreamCodec.of((buf, r) -> {
			buf.writeInt(r.cx);
			buf.writeInt(r.cz);
		}, buf -> new TreeRequest(buf.readInt(), buf.readInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * A chunk's trees, deflated: a palette of block states (written as commands write them), then per block its place in
	 * the chunk (x and z 4 bits each, y biased) and its palette index. Empty {@code data}: none placed.
	 */
	public record TreeChunk(int cx, int cz, byte[] data) implements CustomPacketPayload {
		public static final Type<TreeChunk> TYPE = new Type<>(Identifier.fromNamespaceAndPath("void_lod", "tree_chunk"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TreeChunk> CODEC = StreamCodec.of((buf, c) -> {
			buf.writeInt(c.cx);
			buf.writeInt(c.cz);
			buf.writeByteArray(c.data);
		}, buf -> new TreeChunk(buf.readInt(), buf.readInt(), buf.readByteArray(MAX_FACTS)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A chunk's trees for the wire ({@link TreeChunk#data}). */
	public static byte[] encode(int cx, int cz, dev.voidmc.lod.source.TreePlacer.Placed placed) {
		java.util.Map<net.minecraft.world.level.block.state.BlockState, Integer> index = new java.util.LinkedHashMap<>();
		java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
		java.io.DataOutputStream out = new java.io.DataOutputStream(raw);
		try {
			for (var state : placed.states()) {
				index.putIfAbsent(state, index.size());
			}
			out.writeShort(index.size());
			for (var state : index.keySet()) {
				out.writeUTF(net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(state));
			}
			out.writeInt(placed.positions().length);
			for (int i = 0; i < placed.positions().length; i++) {
				long p = placed.positions()[i];
				int x = net.minecraft.core.BlockPos.getX(p), y = net.minecraft.core.BlockPos.getY(p), z = net.minecraft.core.BlockPos.getZ(p);
				out.writeInt((x - cx * 16 & 15) | (z - cz * 16 & 15) << 4 | (y + 4096) << 8);
				out.writeShort(index.get(placed.states()[i]));
			}
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		java.util.zip.Deflater deflater = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_SPEED);
		try {
			deflater.setInput(raw.toByteArray());
			deflater.finish();
			java.io.ByteArrayOutputStream zipped = new java.io.ByteArrayOutputStream();
			byte[] chunk = new byte[8192];
			while (!deflater.finished()) {
				zipped.write(chunk, 0, deflater.deflate(chunk));
			}
			return zipped.toByteArray();
		} finally {
			deflater.end();
		}
	}

	/** A chunk's trees from the wire; blocks this game does not have are left out. */
	public static dev.voidmc.lod.source.TreePlacer.Placed decode(int cx, int cz, byte[] data) throws java.io.IOException {
		java.util.zip.Inflater inflater = new java.util.zip.Inflater();
		byte[] raw;
		try (var in = new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(data), inflater)) {
			raw = in.readAllBytes();
		} finally {
			inflater.end();
		}
		java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(raw));
		int names = in.readUnsignedShort();
		net.minecraft.world.level.block.state.BlockState[] palette = new net.minecraft.world.level.block.state.BlockState[names];
		for (int i = 0; i < names; i++) {
			String name = in.readUTF();
			try {
				palette[i] = net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
					net.minecraft.core.registries.BuiltInRegistries.BLOCK, name, false).blockState();
			} catch (Exception e) {
				palette[i] = null;
			}
		}
		int count = in.readInt();
		long[] positions = new long[count];
		net.minecraft.world.level.block.state.BlockState[] states = new net.minecraft.world.level.block.state.BlockState[count];
		int kept = 0;
		for (int i = 0; i < count; i++) {
			int p = in.readInt();
			int k = in.readUnsignedShort();
			if (k >= names || palette[k] == null) {
				continue;
			}
			positions[kept] = net.minecraft.core.BlockPos.asLong(cx * 16 + (p & 15), (p >> 8) - 4096, cz * 16 + (p >> 4 & 15));
			states[kept++] = palette[k];
		}
		return new dev.voidmc.lod.source.TreePlacer.Placed(java.util.Arrays.copyOf(positions, kept), java.util.Arrays.copyOf(states, kept));
	}

	/** Registers the payloads, both ways: from the common entrypoint, on client and server alike. */
	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Hello.TYPE, Hello.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TileRequest.TYPE, TileRequest.CODEC);
		PayloadTypeRegistry.clientboundPlay().registerLarge(TileFacts.TYPE, TileFacts.CODEC, MAX_FACTS + 64);
		PayloadTypeRegistry.serverboundPlay().register(TreeRequest.TYPE, TreeRequest.CODEC);
		PayloadTypeRegistry.clientboundPlay().registerLarge(TreeChunk.TYPE, TreeChunk.CODEC, MAX_FACTS + 64);
	}
}
