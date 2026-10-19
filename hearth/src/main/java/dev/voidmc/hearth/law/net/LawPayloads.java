package dev.voidmc.hearth.law.net;

import dev.voidmc.hearth.Hearth;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * The law's packets. The server decides everything: who owns what, whether a lock is locked, what a pocket holds, what
 * a guard says. The client draws it and sends back intents; the lockpicking minigame is the one place the client plays
 * on its own (it has to feel immediate), and the server checks its result.
 */
public final class LawPayloads {
	private LawPayloads() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Hint.TYPE, Hint.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Status.TYPE, Status.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Stealth.TYPE, Stealth.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ContainerOwner.TYPE, ContainerOwner.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LockpickStart.TYPE, LockpickStart.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(LockpickEnd.TYPE, LockpickEnd.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Pocket.TYPE, Pocket.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Confront.TYPE, Confront.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ConfrontDetail.TYPE, ConfrontDetail.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LockpickEvent.TYPE, LockpickEvent.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PocketAction.TYPE, PocketAction.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ConfrontChoice.TYPE, ConfrontChoice.CODEC);
	}

	/**
	 * What the crosshair is on, when it is somebody's: "Owned by Edda Miller", locked or not and how well, whether the
	 * player holds its key. {@code owner} empty clears the hint.
	 *
	 * @param lockTier 0 for no lock, 1 (very easy) to 5 (very hard)
	 */
	public record Hint(Component owner, boolean animal, int lockTier, boolean locked, boolean hasKey, boolean yours) implements CustomPacketPayload {
		public static final Type<Hint> TYPE = new Type<>(Hearth.id("law/hint"));
		public static final Hint NONE = new Hint(Component.empty(), false, 0, false, false, false);
		public static final StreamCodec<RegistryFriendlyByteBuf, Hint> CODEC = StreamCodec.of((buf, p) -> {
			ComponentSerialization.STREAM_CODEC.encode(buf, p.owner);
			buf.writeBoolean(p.animal);
			buf.writeByte(p.lockTier);
			buf.writeBoolean(p.locked);
			buf.writeBoolean(p.hasKey);
			buf.writeBoolean(p.yours);
		}, buf -> new Hint(ComponentSerialization.STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readByte(), buf.readBoolean(), buf.readBoolean(),
			buf.readBoolean()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * The player's standing with the law where they are: wanted, how much, suspected, jailed or in the pillory.
	 *
	 * @param settlement   the town's name (empty: not in a town and not jailed)
	 * @param jailTicks    ticks of sentence left (0: free)
	 * @param pillory      serving it in the pillory rather than the cell
	 */
	public record Status(String settlement, long bounty, boolean outlaw, int suspicion, int jailTicks, boolean pillory, boolean wantedElsewhere)
		implements CustomPacketPayload {
		public static final Type<Status> TYPE = new Type<>(Hearth.id("law/status"));
		public static final Status CLEAR = new Status("", 0, false, 0, 0, false, false);
		public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeUtf(p.settlement);
			buf.writeVarLong(p.bounty);
			buf.writeBoolean(p.outlaw);
			buf.writeVarInt(p.suspicion);
			buf.writeVarInt(p.jailTicks);
			buf.writeBoolean(p.pillory);
			buf.writeBoolean(p.wantedElsewhere);
		}, buf -> new Status(buf.readUtf(), buf.readVarLong(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
			buf.readBoolean()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * How visible a sneaking player is to the folk around them, 0 (hidden) to 1 (in plain sight), and whether somebody
	 * just saw them do something they should not have.
	 *
	 * @param active  sneaking in a settlement (the eye shows)
	 * @param noticed a witness saw a crime this moment (the eye flashes red)
	 */
	public record Stealth(boolean active, float visibility, boolean noticed) implements CustomPacketPayload {
		public static final Type<Stealth> TYPE = new Type<>(Hearth.id("law/stealth"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Stealth> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeBoolean(p.active);
			buf.writeFloat(p.visibility);
			buf.writeBoolean(p.noticed);
		}, buf -> new Stealth(buf.readBoolean(), buf.readFloat(), buf.readBoolean()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The container the player just opened belongs to someone: the screen shows whose. */
	public record ContainerOwner(int containerId, Component owner) implements CustomPacketPayload {
		public static final Type<ContainerOwner> TYPE = new Type<>(Hearth.id("law/container_owner"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ContainerOwner> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.containerId);
			ComponentSerialization.STREAM_CODEC.encode(buf, p.owner);
		}, buf -> new ContainerOwner(buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * Opens the lockpicking screen. The lock's pins come from {@code seed} and {@code tier} through
	 * {@link dev.voidmc.hearth.law.lock.LockpickModel#spec}, eased by the player's {@code skill}.
	 *
	 * @param picks lockpicks the player carries
	 */
	public record LockpickStart(int session, int tier, long seed, int skill, int picks, Component what) implements CustomPacketPayload {
		public static final Type<LockpickStart> TYPE = new Type<>(Hearth.id("law/lockpick_start"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LockpickStart> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.session);
			buf.writeByte(p.tier);
			buf.writeLong(p.seed);
			buf.writeVarInt(p.skill);
			buf.writeVarInt(p.picks);
			ComponentSerialization.STREAM_CODEC.encode(buf, p.what);
		}, buf -> new LockpickStart(buf.readVarInt(), buf.readByte(), buf.readLong(), buf.readVarInt(), buf.readVarInt(),
			ComponentSerialization.STREAM_CODEC.decode(buf)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The server ended a lockpicking attempt (seen, out of picks, walked away); {@code picks} is what is left. */
	public record LockpickEnd(int session, int reason, int picks) implements CustomPacketPayload {
		public static final Type<LockpickEnd> TYPE = new Type<>(Hearth.id("law/lockpick_end"));
		public static final int OPENED = 0;
		public static final int SPOTTED = 1;
		public static final int NO_PICKS = 2;
		public static final int GONE = 3;
		/** Only the pick count changed (a pick broke and was taken). */
		public static final int PICKS = 4;
		public static final StreamCodec<RegistryFriendlyByteBuf, LockpickEnd> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.session);
			buf.writeByte(p.reason);
			buf.writeVarInt(p.picks);
		}, buf -> new LockpickEnd(buf.readVarInt(), buf.readByte(), buf.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** What happened at the lock, client to server: a pick snapped, the lock opened, or the player gave up. */
	public record LockpickEvent(int session, int kind, int elapsedTicks) implements CustomPacketPayload {
		public static final Type<LockpickEvent> TYPE = new Type<>(Hearth.id("law/lockpick_event"));
		public static final int BROKE = 1;
		public static final int OPENED = 2;
		public static final int CANCEL = 3;
		public static final StreamCodec<RegistryFriendlyByteBuf, LockpickEvent> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.session);
			buf.writeByte(p.kind);
			buf.writeVarInt(p.elapsedTicks);
		}, buf -> new LockpickEvent(buf.readVarInt(), buf.readByte(), buf.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * The pickpocketing screen as it stands (open, update or end). Rates let the client animate between updates.
	 *
	 * @param items    what the pockets hold (empty stacks where something was taken); coin shows as coin
	 * @param state    {@link #OPEN}, {@link #CAUGHT}, {@link #DONE}
	 * @param entity   the victim's folk entity id, or -1
	 * @param look     the victim's look ({@link dev.voidmc.hearth.npc.look.FolkLook#key()}) for their portrait, or -1
	 */
	public record Pocket(int session, String victim, boolean asleep, List<ItemStack> items, float awareness, float awarenessRate, int lifting,
		float liftProgress, float liftRate, int state, int entity, long look) implements CustomPacketPayload {
		/** Without the victim's face (2026-10-19 additions). */
		public Pocket(int session, String victim, boolean asleep, List<ItemStack> items, float awareness, float awarenessRate, int lifting,
			float liftProgress, float liftRate, int state) {
			this(session, victim, asleep, items, awareness, awarenessRate, lifting, liftProgress, liftRate, state, -1, -1L);
		}

		public static final Type<Pocket> TYPE = new Type<>(Hearth.id("law/pocket"));
		public static final int OPEN = 0;
		public static final int CAUGHT = 1;
		public static final int DONE = 2;
		public static final StreamCodec<RegistryFriendlyByteBuf, Pocket> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.session);
			buf.writeUtf(p.victim);
			buf.writeBoolean(p.asleep);
			buf.writeVarInt(p.items.size());
			for (ItemStack s : p.items) {
				ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
			}
			buf.writeFloat(p.awareness);
			buf.writeFloat(p.awarenessRate);
			buf.writeVarInt(p.lifting + 1);
			buf.writeFloat(p.liftProgress);
			buf.writeFloat(p.liftRate);
			buf.writeByte(p.state);
			buf.writeVarInt(p.entity + 1);
			buf.writeLong(p.look);
		}, buf -> {
			int session = buf.readVarInt();
			String victim = buf.readUtf();
			boolean asleep = buf.readBoolean();
			int n = Math.min(buf.readVarInt(), 27);
			List<ItemStack> items = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
			}
			return new Pocket(session, victim, asleep, items, buf.readFloat(), buf.readFloat(), buf.readVarInt() - 1, buf.readFloat(), buf.readFloat(),
				buf.readByte(), buf.readVarInt() - 1, buf.readLong());
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The player's hand in the pockets: lift a slot, stop lifting, or slip away. */
	public record PocketAction(int session, int action, int slot) implements CustomPacketPayload {
		public static final Type<PocketAction> TYPE = new Type<>(Hearth.id("law/pocket_action"));
		public static final int LIFT = 1;
		public static final int STOP = 2;
		public static final int LEAVE = 3;
		public static final StreamCodec<RegistryFriendlyByteBuf, PocketAction> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.session);
			buf.writeByte(p.action);
			buf.writeVarInt(p.slot);
		}, buf -> new PocketAction(buf.readVarInt(), buf.readByte(), buf.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** One choice in a confrontation. {@code enabled} false shows it greyed out (can't afford it, tried already). */
	public record Choice(String id, Component label, boolean enabled) {
	}

	/**
	 * A guard (or a witness) confronts the player: what they say and the choices. {@code open} false closes the screen.
	 *
	 * @param entity the folk's entity id (the client draws their face)
	 */
	public record Confront(int entity, boolean open, String name, Component speech, long bounty, List<Choice> choices) implements CustomPacketPayload {
		public static final Type<Confront> TYPE = new Type<>(Hearth.id("law/confront"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Confront> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.entity);
			buf.writeBoolean(p.open);
			buf.writeUtf(p.name);
			ComponentSerialization.STREAM_CODEC.encode(buf, p.speech);
			buf.writeVarLong(p.bounty);
			buf.writeVarInt(p.choices.size());
			for (Choice c : p.choices) {
				buf.writeUtf(c.id);
				ComponentSerialization.STREAM_CODEC.encode(buf, c.label);
				buf.writeBoolean(c.enabled);
			}
		}, buf -> {
			int entity = buf.readVarInt();
			boolean open = buf.readBoolean();
			String name = buf.readUtf();
			Component speech = ComponentSerialization.STREAM_CODEC.decode(buf);
			long bounty = buf.readVarLong();
			int n = Math.min(buf.readVarInt(), 16);
			List<Choice> choices = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				choices.add(new Choice(buf.readUtf(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readBoolean()));
			}
			return new Confront(entity, open, name, speech, bounty, choices);
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The player's answer to a confrontation (or {@code ""} when they closed it: walking away). */
	public record ConfrontChoice(int entity, String choice) implements CustomPacketPayload {
		public static final Type<ConfrontChoice> TYPE = new Type<>(Hearth.id("law/confront_choice"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ConfrontChoice> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.entity);
			buf.writeUtf(p.choice, 64);
		}, buf -> new ConfrontChoice(buf.readVarInt(), buf.readUtf(64)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * What a choice in a confrontation does, for screens that show more than its label (docs/OVERLAY.md
	 * {@code confront.choices[]}).
	 *
	 * @param kind    {@code pay, cell, submit, pillory, persuade, bribe, threaten, run, fight, allow, refuse, leave}
	 * @param odds    a check's chance 0–100, or -1
	 * @param cost    bits it costs, or -1
	 * @param minutes time it costs (the cell, the pillory), or -1
	 */
	public record ChoiceInfo(String id, String kind, int odds, long cost, int minutes) {
		static final StreamCodec<RegistryFriendlyByteBuf, ChoiceInfo> CODEC = StreamCodec.of((buf, c) -> {
			buf.writeUtf(c.id, 64);
			buf.writeUtf(c.kind, 64);
			buf.writeVarInt(c.odds);
			buf.writeVarLong(c.cost);
			buf.writeVarInt(c.minutes);
		}, buf -> new ChoiceInfo(buf.readUtf(64), buf.readUtf(64), buf.readVarInt(), buf.readVarLong(), buf.readVarInt()));
	}

	/**
	 * The guard's (or witness's) card and each choice's facts, sent right after an open {@link Confront} for the same
	 * entity (added 2026-10-19 for the Hub's screen; Hearth's own screen reads none of it).
	 *
	 * @param kind      {@code arrest}, {@code search} or {@code witness}
	 * @param job       their trade as a translation key
	 * @param traits    their traits' serialized names
	 * @param opinion   what they think of the player, −100…100
	 * @param standing  the player's standing in the town
	 * @param title     the player's title there
	 * @param town      the town's name
	 * @param offence   the worst offence charged, as a translation key ({@code hearth.offence.theft}), or empty
	 * @param offences  how many offences the warrant holds
	 * @param witnesses who saw it (the witness's own name for a witness), or empty
	 */
	public record ConfrontDetail(int entity, String kind, String job, List<String> traits, int opinion, int standing, String title, String town,
		String offence, int offences, String witnesses, List<ChoiceInfo> choices) implements CustomPacketPayload {
		public static final Type<ConfrontDetail> TYPE = new Type<>(Hearth.id("law/confront_detail"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ConfrontDetail> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.entity);
			buf.writeUtf(p.kind, 32);
			buf.writeUtf(p.job, 256);
			int nt = Math.min(p.traits.size(), 16);
			buf.writeVarInt(nt);
			for (String t : p.traits.subList(0, nt)) {
				buf.writeUtf(t, 64);
			}
			buf.writeVarInt(p.opinion);
			buf.writeVarInt(p.standing);
			buf.writeUtf(p.title, 64);
			buf.writeUtf(p.town, 256);
			buf.writeUtf(p.offence, 256);
			buf.writeVarInt(p.offences);
			buf.writeUtf(p.witnesses, 256);
			int nc = Math.min(p.choices.size(), 16);
			buf.writeVarInt(nc);
			for (ChoiceInfo c : p.choices.subList(0, nc)) {
				ChoiceInfo.CODEC.encode(buf, c);
			}
		}, buf -> {
			int entity = buf.readVarInt();
			String kind = buf.readUtf(32);
			String job = buf.readUtf(256);
			int nt = Math.min(buf.readVarInt(), 16);
			List<String> traits = new ArrayList<>(nt);
			for (int i = 0; i < nt; i++) {
				traits.add(buf.readUtf(64));
			}
			int opinion = buf.readVarInt();
			int standing = buf.readVarInt();
			String title = buf.readUtf(64);
			String town = buf.readUtf(256);
			String offence = buf.readUtf(256);
			int offences = buf.readVarInt();
			String witnesses = buf.readUtf(256);
			int nc = Math.min(buf.readVarInt(), 16);
			List<ChoiceInfo> choices = new ArrayList<>(nc);
			for (int i = 0; i < nc; i++) {
				choices.add(ChoiceInfo.CODEC.decode(buf));
			}
			return new ConfrontDetail(entity, kind, job, traits, opinion, standing, title, town, offence, offences, witnesses, choices);
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		public @org.jspecify.annotations.Nullable ChoiceInfo info(String id) {
			for (ChoiceInfo c : this.choices) {
				if (c.id.equals(id)) {
					return c;
				}
			}
			return null;
		}
	}
}
