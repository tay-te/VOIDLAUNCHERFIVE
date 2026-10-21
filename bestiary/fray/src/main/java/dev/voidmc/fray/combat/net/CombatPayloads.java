package dev.voidmc.fray.combat.net;

import dev.voidmc.fray.Fray;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Fray combat's payloads (CONTRACTS.md §15). One packet per action, meters and resources only on change and rate
 * limited, never per tick. Protocol: {@link dev.voidmc.fray.api.FrayApi#PROTOCOL}.
 */
public final class CombatPayloads {
	private CombatPayloads() {
	}

	private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> OPT_COMPONENT = ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs::optional);

	// ------------------------------------------------------------------ client → server

	/** Input types for {@link Input}. */
	public static final byte ATTACK_DOWN = 0, ATTACK_UP = 1, GUARD_DOWN = 2, GUARD_UP = 3, DODGE = 4, TECHNIQUE = 5, ART = 6, LOCK = 7;

	/**
	 * A combat input from the local player (already predicted on its side). {@code param}: held ticks on ATTACK_UP, the
	 * dodge direction (0 none = backstep, 1-8 compass relative to the look) on DODGE, the target on LOCK.
	 */
	public record Input(byte kind, int seq, long clientTick, float yaw, float pitch, int target, int param) implements CustomPacketPayload {
		public static final Type<Input> TYPE = new Type<>(Fray.id("combat/input"));
		public static final StreamCodec<FriendlyByteBuf, Input> STREAM_CODEC = StreamCodec.of((b, p) -> {
			b.writeByte(p.kind);
			b.writeVarInt(p.seq);
			b.writeVarLong(p.clientTick);
			b.writeFloat(p.yaw);
			b.writeFloat(p.pitch);
			b.writeVarInt(p.target + 1);
			b.writeVarInt(p.param);
		}, b -> new Input(b.readByte(), b.readVarInt(), b.readVarLong(), b.readFloat(), b.readFloat(), b.readVarInt() - 1, b.readVarInt()));

		@Override
		public Type<Input> type() {
			return TYPE;
		}
	}

	// ------------------------------------------------------------------ server → client

	/** Reply to an input: accepted, the action kind it started (an {@code ActionKind} ordinal, -1 none) and its start tick. */
	public record Ack(int seq, boolean accepted, byte kind, long startTick, byte reason) implements CustomPacketPayload {
		public static final Type<Ack> TYPE = new Type<>(Fray.id("combat/ack"));
		public static final StreamCodec<FriendlyByteBuf, Ack> STREAM_CODEC = StreamCodec.of((b, p) -> {
			b.writeVarInt(p.seq);
			b.writeBoolean(p.accepted);
			b.writeByte(p.kind);
			b.writeVarLong(p.startTick);
			b.writeByte(p.reason);
		}, b -> new Ack(b.readVarInt(), b.readBoolean(), b.readByte(), b.readVarLong(), b.readByte()));

		@Override
		public Type<Ack> type() {
			return TYPE;
		}
	}

	/** An entity started an action (trackers; the actor predicts its own). */
	public record ActionPlay(int entity, byte kind, byte variant, long startTick, float speed) implements CustomPacketPayload {
		public static final Type<ActionPlay> TYPE = new Type<>(Fray.id("combat/action"));
		public static final StreamCodec<FriendlyByteBuf, ActionPlay> STREAM_CODEC = StreamCodec.of((b, p) -> {
			b.writeVarInt(p.entity);
			b.writeByte(p.kind);
			b.writeByte(p.variant);
			b.writeVarLong(p.startTick);
			b.writeFloat(p.speed);
		}, b -> new ActionPlay(b.readVarInt(), b.readByte(), b.readByte(), b.readVarLong(), b.readFloat()));

		@Override
		public Type<ActionPlay> type() {
			return TYPE;
		}
	}

	/** Flags for {@link Self#flags}. */
	public static final int SELF_GUARDING = 1, SELF_IN_COMBAT = 2, SELF_BLOCK_UP = 4, SELF_CHARGING = 8, SELF_BOSS_FIGHT = 16;

	/** The player's own resources (target only, on change, at most 10 a second). */
	public record Self(float stamina, float max, int windedLeft, int regenDelayLeft, int flags, float poise, int art, int artCooldown, int artCooldownTotal,
		int lockTarget, int riposteLeft, float charge) implements CustomPacketPayload {
		public static final Type<Self> TYPE = new Type<>(Fray.id("resource/self"));
		public static final StreamCodec<FriendlyByteBuf, Self> STREAM_CODEC = StreamCodec.of((b, p) -> {
			b.writeFloat(p.stamina);
			b.writeFloat(p.max);
			b.writeVarInt(p.windedLeft);
			b.writeVarInt(p.regenDelayLeft);
			b.writeVarInt(p.flags);
			b.writeFloat(p.poise);
			b.writeVarInt(p.art + 1);
			b.writeVarInt(p.artCooldown);
			b.writeVarInt(p.artCooldownTotal);
			b.writeVarInt(p.lockTarget + 1);
			b.writeVarInt(p.riposteLeft);
			b.writeFloat(p.charge);
		}, b -> new Self(b.readFloat(), b.readFloat(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readFloat(), b.readVarInt() - 1, b.readVarInt(),
			b.readVarInt(), b.readVarInt() - 1, b.readVarInt(), b.readFloat()));

		@Override
		public Type<Self> type() {
			return TYPE;
		}
	}

	/** One active status on the player (its own status row). */
	public record StatusLine(byte status, int left, int total) {
	}

	/** The player's own Fray statuses with times (on change). */
	public record SelfStatuses(List<StatusLine> lines) implements CustomPacketPayload {
		public static final Type<SelfStatuses> TYPE = new Type<>(Fray.id("combat/self_statuses"));
		public static final StreamCodec<FriendlyByteBuf, SelfStatuses> STREAM_CODEC = StreamCodec.of((b, p) -> {
			b.writeVarInt(p.lines.size());
			for (StatusLine l : p.lines) {
				b.writeByte(l.status);
				b.writeVarInt(l.left);
				b.writeVarInt(l.total);
			}
		}, b -> {
			int n = b.readVarInt();
			List<StatusLine> l = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				l.add(new StatusLine(b.readByte(), b.readVarInt(), b.readVarInt()));
			}
			return new SelfStatuses(l);
		});

		@Override
		public Type<SelfStatuses> type() {
			return TYPE;
		}
	}

	/** An entity's build-up meters (bytes, StatusType.METERED order), active statuses and Stagger ticks left (trackers). */
	public record Meters(int entity, byte[] meters, int mask, int staggerLeft) implements CustomPacketPayload {
		public static final Type<Meters> TYPE = new Type<>(Fray.id("combat/meters"));
		public static final StreamCodec<FriendlyByteBuf, Meters> STREAM_CODEC = StreamCodec.of((b, p) -> {
			b.writeVarInt(p.entity);
			b.writeByteArray(p.meters);
			b.writeVarInt(p.mask);
			b.writeVarInt(p.staggerLeft);
		}, b -> new Meters(b.readVarInt(), b.readByteArray(32), b.readVarInt(), b.readVarInt()));

		@Override
		public Type<Meters> type() {
			return TYPE;
		}
	}

	/** A creature's rank, target-frame subtitle and affix icons (trackers, on change and on START_TRACKING). */
	public record Profile(int entity, byte rank, Optional<Component> subtitle, List<Identifier> affixes) implements CustomPacketPayload {
		public static final Type<Profile> TYPE = new Type<>(Fray.id("combat/profile"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Profile> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Profile::entity, ByteBufCodecs.BYTE, Profile::rank, OPT_COMPONENT, Profile::subtitle,
			Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(16)), Profile::affixes, Profile::new);

		@Override
		public Type<Profile> type() {
			return TYPE;
		}
	}

	/** Hit flags for the HUD in {@link Hit#flags}: the api's HitFlags plus a kill bit. */
	public static final int HIT_KILL = 1 << 30;

	/**
	 * A hit the server decided (attacker and target only): the guard outcome, the hit flags, the amount and its main kind
	 * (hit marker, parry ring, Slip, guard broken, damage numbers).
	 */
	public record Hit(int attacker, int target, byte outcome, int flags, float amount, byte kind, float x, float y, float z) implements CustomPacketPayload {
		public static final Type<Hit> TYPE = new Type<>(Fray.id("combat/hit"));
		public static final StreamCodec<FriendlyByteBuf, Hit> STREAM_CODEC = StreamCodec.of((b, p) -> {
			b.writeVarInt(p.attacker + 1);
			b.writeVarInt(p.target + 1);
			b.writeByte(p.outcome);
			b.writeVarInt(p.flags);
			b.writeFloat(p.amount);
			b.writeByte(p.kind);
			b.writeFloat(p.x);
			b.writeFloat(p.y);
			b.writeFloat(p.z);
		}, b -> new Hit(b.readVarInt() - 1, b.readVarInt() - 1, b.readByte(), b.readVarInt(), b.readFloat(), b.readByte(), b.readFloat(), b.readFloat(), b.readFloat()));

		@Override
		public Type<Hit> type() {
			return TYPE;
		}
	}

	/** A tell aimed at this player (H9). */
	public record Tell(int attacker, byte colour, int windup, Optional<Component> subtitle) implements CustomPacketPayload {
		public static final Type<Tell> TYPE = new Type<>(Fray.id("combat/tell"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Tell> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Tell::attacker, ByteBufCodecs.BYTE, Tell::colour, ByteBufCodecs.VAR_INT, Tell::windup, OPT_COMPONENT, Tell::subtitle, Tell::new);

		@Override
		public Type<Tell> type() {
			return TYPE;
		}
	}

	/** Fray's boss bar for one encounter (participants, on change, at most 4 a second). {@code boss} -1 removes it. */
	public record EncounterBar(int id, int boss, Component name, Optional<Component> subtitle, float health, int phase, List<Float> notches, float breakMeter,
		int brokenLeft, byte[] meters) implements CustomPacketPayload {
		public static final Type<EncounterBar> TYPE = new Type<>(Fray.id("encounter/bar"));
		public static final StreamCodec<RegistryFriendlyByteBuf, EncounterBar> STREAM_CODEC = StreamCodec.of((b, p) -> {
			b.writeVarInt(p.id);
			b.writeVarInt(p.boss + 1);
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(b, p.name);
			OPT_COMPONENT.encode(b, p.subtitle);
			b.writeFloat(p.health);
			b.writeVarInt(p.phase);
			b.writeVarInt(p.notches.size());
			for (float f : p.notches) {
				b.writeFloat(f);
			}
			b.writeFloat(p.breakMeter);
			b.writeVarInt(p.brokenLeft);
			b.writeByteArray(p.meters);
		}, b -> {
			int id = b.readVarInt();
			int boss = b.readVarInt() - 1;
			Component name = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(b);
			Optional<Component> sub = OPT_COMPONENT.decode(b);
			float health = b.readFloat();
			int phase = b.readVarInt();
			int n = b.readVarInt();
			List<Float> notches = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				notches.add(b.readFloat());
			}
			return new EncounterBar(id, boss, name, sub, health, phase, notches, b.readFloat(), b.readVarInt(), b.readByteArray(32));
		});

		@Override
		public Type<EncounterBar> type() {
			return TYPE;
		}
	}

	public static final byte BANNER_START = 0, BANNER_FELLED = 1, BANNER_PHASE = 2;

	/** An encounter banner (H7): the boss's name on start, "Felled" on victory. */
	public record Banner(byte kind, Component title, Component subtitle) implements CustomPacketPayload {
		public static final Type<Banner> TYPE = new Type<>(Fray.id("encounter/banner"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Banner> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BYTE, Banner::kind, ComponentSerialization.TRUSTED_STREAM_CODEC, Banner::title, ComponentSerialization.TRUSTED_STREAM_CODEC,
			Banner::subtitle, Banner::new);

		@Override
		public Type<Banner> type() {
			return TYPE;
		}
	}

	/** Configuration phase, both ways: the combat protocol, Fray's version and the hash of the combat rules and movesets. */
	public record Hello(int protocol, String version, String content) implements CustomPacketPayload {
		public static final Type<Hello> TYPE = new Type<>(Fray.id("combat/hello"));
		public static final StreamCodec<ByteBuf, Hello> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Hello::protocol, ByteBufCodecs.stringUtf8(32), Hello::version, ByteBufCodecs.stringUtf8(64), Hello::content, Hello::new);

		@Override
		public Type<Hello> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Input.TYPE, Input.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Ack.TYPE, Ack.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ActionPlay.TYPE, ActionPlay.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Self.TYPE, Self.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SelfStatuses.TYPE, SelfStatuses.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Meters.TYPE, Meters.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Profile.TYPE, Profile.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Hit.TYPE, Hit.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Tell.TYPE, Tell.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(EncounterBar.TYPE, EncounterBar.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Banner.TYPE, Banner.STREAM_CODEC);
		PayloadTypeRegistry.clientboundConfiguration().register(Hello.TYPE, Hello.STREAM_CODEC);
		PayloadTypeRegistry.serverboundConfiguration().register(Hello.TYPE, Hello.STREAM_CODEC);
	}
}
