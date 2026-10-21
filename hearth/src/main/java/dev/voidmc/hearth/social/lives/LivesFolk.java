package dev.voidmc.hearth.social.lives;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.CrowdService;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.QuestEvents;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.npc.crowd.Barks;
import dev.voidmc.hearth.npc.crowd.Role;
import dev.voidmc.hearth.npc.routine.ScheduleOverrides;
import dev.voidmc.hearth.sim.lives.Life;
import dev.voidmc.hearth.sim.lives.Lives;
import dev.voidmc.hearth.sim.lives.LivesBook;
import dev.voidmc.hearth.sim.lives.LivesView;
import dev.voidmc.hearth.sim.lives.TownLives;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.life.Matchmaker;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.life.Venues;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The embodied side of people with lives (docs/LIVING_WORLD_2.md §4.3): the behaviours (an apprentice at the master's
 * side 105, sweethearts walking out 30, a feud in passing 42, grief at the grave 36), the named characters' personal days
 * ({@link NotableSchedule}, band 35), talk where the player can hear ({@link LivesTalk}), the conversation options
 * ({@link LivesDialogue}) and the People list sent to the client. Settings in {@link Lives#config()}.
 * Lane B2 (§11.3) owns this package; the abstract model is {@code sim/lives}.
 */
public final class LivesFolk {
	private static boolean behavioursRegistered;
	private static long lastFolkDeath = Long.MIN_VALUE;
	static final AtomicInteger OVERHEARD = new AtomicInteger();
	private static volatile String lastOverheard = "";

	private LivesFolk() {
	}

	/** Called once from {@code SocialModule.init} (lane 0 put the call there; lanes never edit the module init). */
	public static void init() {
		Lives.setSameSex(() -> SocialModule.config().family().sameSex());
		Matchmaker.sweethearts(Life::sweetheart);
		ScheduleOverrides.register(NotableSchedule.INSTANCE);
		DialogueProvider.register(new LivesDialogue());
		PayloadTypeRegistry.clientboundPlay().register(LivesPayloads.People.TYPE, LivesPayloads.People.CODEC);
		PeopleDetail.register();
		registerBehaviours();
		ServerLifecycleEvents.SERVER_STARTING.register(s -> registerBehaviours());
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			LivesDialogue.clear();
			CourtingBehaviour.GIVEN.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(LivesTalk::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(Hearth.id("folk"))) {
				lastFolkDeath = entity.level().getGameTime();
			}
		});
		SettlementEvents.RESIDENT_REMOVED.register(LivesFolk::removed);
		SettlementEvents.LIFE_EVENT.register(LivesFolk::lifeEvent);
		QuestEvents.COMPLETED.register((player, settlement, type, template, tier) -> questDone(player, settlement, template.toString()));
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> LivesCommands.register(dispatcher));
	}

	private static synchronized void registerBehaviours() {
		NpcService npc = HearthServices.npc();
		if (behavioursRegistered || npc == null) {
			return;
		}
		npc.registerBehaviour(new ApprenticeBehaviour());
		npc.registerBehaviour(new FeudBehaviour());
		npc.registerBehaviour(new GriefBehaviour());
		npc.registerBehaviour(new CourtingBehaviour());
		behavioursRegistered = true;
	}

	/** How a resident went: a death (old age, killed, a thread's end) or a leaving (settlers, emigrants, a thread). */
	private static void removed(HearthWorld w, Settlement s, Resident r) {
		if (!Lives.config().lives().enabled()) {
			return;
		}
		boolean settlers = !r.extra.getCompoundOrEmpty("sim").getStringOr("left_for", "").isEmpty();
		var lives = Life.peek(r);
		boolean leftThread = lives != null && lives.getBooleanOr("left_thread", false);
		boolean diedThread = lives != null && lives.getBooleanOr("died_thread", false);
		boolean died = !settlers && !leftThread && (diedThread || r.age == AgeStage.ELDER || lastFolkDeath == w.level().getGameTime());
		Lives.gone(s, r, died, TownLife.today(w, s));
	}

	/** A master made in an embodied town: the trade gathers at the workshop to drink to them. */
	private static void lifeEvent(HearthWorld w, Settlement s, java.util.UUID resident, String kind, java.util.@Nullable UUID player) {
		if (!kind.equals("master") || !s.embodied()) {
			return;
		}
		Resident r = s.resident(resident);
		PlotView work = r == null || r.work == Resident.NO_PLOT ? null : s.plot(r.work);
		if (r == null) {
			return;
		}
		BlockPos at = work != null ? Venues.middle(work.spec()) : Venues.square(s);
		CrowdService crowds = HearthServices.crowds();
		if (crowds != null) {
			crowds.spectacle(s, at, "masterpiece", 40, 8);
		}
		NpcService.FolkHandle f = Hooks.folk(resident);
		if (f != null) {
			Barks.say(f, "lives.masterpiece", Role.of(r, false), null, null);
		}
	}

	/** A personal quest done: the thread that offered it can move on (its {@code quest:<template>} flag). */
	private static void questDone(ServerPlayer player, @Nullable Settlement s, String template) {
		if (s == null) {
			return;
		}
		for (TownLives.Notable n : LivesView.notables(s)) {
			if (n.flags.remove("offered:" + template)) {
				n.flags.add("quest:" + template);
				n.by = player.getUUID();
				n.byName = player.getName().getString();
				s.markDirty();
			}
		}
	}

	static void overheard(Settlement s, ServerPlayer p, String slug) {
		OVERHEARD.incrementAndGet();
		lastOverheard = s.name() + ": about " + p.getName().getString() + " (" + slug + ")";
	}

	/** Talk about the player overheard so far, and the last (tours). */
	public static String overheardLine() {
		return "overheard " + OVERHEARD.get() + (lastOverheard.isEmpty() ? "" : ", last " + lastOverheard);
	}

	// ---------------------------------------------------------------- the People list

	/** The named characters this player has met, in every town of the world they are in. */
	public static List<LivesPayloads.Row> people(ServerPlayer player) {
		List<LivesPayloads.Row> rows = new ArrayList<>();
		if (!(player.level() instanceof ServerLevel level)) {
			return rows;
		}
		HearthWorld w = HearthWorld.of(level);
		if (w == null) {
			return rows;
		}
		for (Settlement s : w.settlements()) {
			for (TownLives.Notable n : LivesView.notables(s)) {
				if (!n.met.contains(player.getUUID())) {
					continue;
				}
				Resident r = s.resident(n.resident);
				if (r == null) {
					continue;
				}
				LivesBook.Beat beat = LivesView.beat(n);
				String detail;
				try {
					detail = PeopleDetail.of(player, s, r, n).toString();
				} catch (RuntimeException e) {
					Hearth.LOG.warn("people detail for {} failed", r.fullName(), e);
					detail = "";
				}
				rows.add(new LivesPayloads.Row(r.fullName(), r.givenName, n.role, s.name(), n.thread, n.beat, beat != null && beat.end(),
					Hooks.opinion(r.id, player), player.getUUID().equals(n.by), detail));
				if (rows.size() >= 64) {
					return rows;
				}
			}
		}
		return rows;
	}

	static void sendPeople(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, LivesPayloads.People.TYPE)) {
			ServerPlayNetworking.send(player, new LivesPayloads.People(people(player)));
		}
	}

	/** Who the folk of note are and where their stories stand, as one line of talk. */
	static Component peopleLine(Settlement s, Resident speaker) {
		MutableComponent out = Component.translatable("hearth.lives.people.intro");
		int shown = 0;
		for (TownLives.Notable n : LivesView.notables(s)) {
			Resident r = s.resident(n.resident);
			if (r == null || r == speaker) {
				continue;
			}
			out.append(shown == 0 ? " " : "; ");
			out.append(Component.translatable("hearth.lives.people.entry", r.givenName, Component.translatable("hearth.lives.role." + n.role),
				Component.translatable("hearth.lives.story." + n.thread + "." + n.beat, r.givenName)));
			shown++;
		}
		if (shown == 0) {
			return Component.translatable("hearth.lives.people.none");
		}
		return out.append(".");
	}

	/** The server, for commands (null outside one). */
	static @Nullable MinecraftServer server(Settlement s) {
		HearthWorld w = TownLife.world(s);
		return w == null ? null : w.level().getServer();
	}
}
