package dev.voidmc.hearth.npc.talk;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Talking to folk, on the server. Right-clicking a folk opens the conversation: their name, trade and town, a greeting
 * composed for the moment, and the options every registered {@link DialogueProvider} offers, sorted by
 * {@link DialogueProvider.Option#order}. Picking one calls its provider; the npc module's own options (news, rumours,
 * about them, goodbye) answer with a line of speech.
 *
 * <p>Other modules add options by registering a provider ({@code DialogueProvider.register}); see docs/FOLK.md.
 */
public final class Conversations {
	/** Blocks a player may stand from the folk and keep talking (the client screen closes at the same distance). */
	public static final double RANGE = 10.0;

	private final Map<UUID, Session> sessions = new HashMap<>();

	private static final class Session {
		final Folk folk;
		final Map<String, DialogueProvider> owners = new HashMap<>();
		final java.util.Set<String> locked = new java.util.HashSet<>();
		Component speech = Component.empty();

		Session(Folk folk) {
			this.folk = folk;
		}
	}

	public void open(ServerPlayer player, Folk folk) {
		FolkBrain brain = folk.brain();
		if (brain == null) {
			return;
		}
		Settlement s = brain.settlement;
		Resident r = brain.resident;
		brain.engage(player);
		String text = Greetings.greeting(player.level(), s, r, player, folk.blockPosition(), brain.random);
		brain.data.recordMeeting(player.getUUID());
		s.markDirty();
		Session session = new Session(folk);
		this.sessions.put(player.getUUID(), session);
		show(player, session, Component.literal(text), true);
	}

	/** The player picked an option. */
	public void choose(ServerPlayer player, int entityId, String optionId) {
		Session session = this.sessions.get(player.getUUID());
		if (session == null || session.folk.getId() != entityId || !valid(player, session.folk)) {
			close(player, entityId);
			return;
		}
		FolkBrain brain = session.folk.brain();
		if (brain == null) {
			return;
		}
		brain.keepEngaged(player);
		DialogueProvider provider = session.owners.get(optionId);
		if (provider != null && session.locked.contains(optionId)) {
			// a locked option cannot be picked (a modified client, or the screen out of date)
			show(player, session, session.speech, false);
			return;
		}
		if (provider == null) {
			show(player, session, session.speech, false);
			return;
		}
		if (provider instanceof NpcDialogue npc) {
			Component reply = npc.reply(player, brain, optionId);
			if (reply == null) {
				// goodbye: the folk has had their last word, and the screen goes
				close(player, entityId);
				return;
			}
			brain.speak();
			show(player, session, reply, false);
			return;
		}
		Component reply = null;
		try {
			if (provider.select(player, brain.settlement, brain.resident, optionId)) {
				reply = provider.reply(player, brain.settlement, brain.resident, optionId);
			}
		} catch (RuntimeException e) {
			dev.voidmc.hearth.Hearth.LOG.warn("dialogue provider {} failed on {}", provider.getClass().getName(), optionId, e);
		}
		// Whatever it did (a trade screen, a quest accepted), the options may have changed; a reply is the folk's new line.
		if (this.sessions.get(player.getUUID()) == session) {
			if (reply != null) {
				brain.speak();
			}
			show(player, session, reply != null ? reply : session.speech, false);
		}
	}

	public void end(ServerPlayer player, int entityId) {
		Session session = this.sessions.remove(player.getUUID());
		if (session != null && session.folk.brain() != null && session.folk.brain().engagedWith(player)) {
			session.folk.brain().disengage();
		}
	}

	/** The server ends the conversation: the session goes and the player's screen is closed. */
	public void close(ServerPlayer player, int entityId) {
		end(player, entityId);
		if (ServerPlayNetworking.canSend(player, TalkPayloads.Close.TYPE)) {
			ServerPlayNetworking.send(player, new TalkPayloads.Close(entityId));
		}
	}

	/** The player left the server: their conversation ends. */
	public void forget(ServerPlayer player) {
		Session session = this.sessions.remove(player.getUUID());
		if (session != null && session.folk.brain() != null && session.folk.brain().engagedWith(player)) {
			session.folk.brain().disengage();
		}
	}

	private static boolean valid(ServerPlayer player, Folk folk) {
		return folk.isAlive() && !folk.isRemoved() && folk.level() == player.level() && folk.distanceToSqr(player) <= RANGE * RANGE;
	}

	private void show(ServerPlayer player, Session session, Component speech, boolean open) {
		FolkBrain brain = session.folk.brain();
		if (brain == null) {
			return;
		}
		session.speech = speech;
		session.owners.clear();
		session.locked.clear();
		List<Ranked> ranked = new ArrayList<>();
		for (DialogueProvider p : DialogueProvider.providers()) {
			List<DialogueProvider.Option> out = new ArrayList<>();
			try {
				p.collect(player, brain.settlement, brain.resident, out);
			} catch (RuntimeException e) {
				dev.voidmc.hearth.Hearth.LOG.warn("dialogue provider {} failed", p.getClass().getName(), e);
				continue;
			}
			for (DialogueProvider.Option o : out) {
				if (!session.owners.containsKey(o.id())) {
					session.owners.put(o.id(), p);
					if (o.locked()) {
						session.locked.add(o.id());
					}
					ranked.add(new Ranked(o, ranked.size()));
				}
			}
		}
		ranked.sort(Comparator.<Ranked>comparingInt(r -> r.option.order()).thenComparingInt(r -> r.index));
		List<TalkPayloads.Option> options = new ArrayList<>(ranked.size());
		for (Ranked r : ranked) {
			options.add(new TalkPayloads.Option(r.option.id(), r.option.label(), r.option.locked(), r.option.hint(), r.option.tooltip()));
		}
		Resident r = brain.resident;
		String title = r.age == AgeStage.CHILD ? "hearth.npc.child" : "hearth.npc.job." + r.job.getSerializedName();
		ServerPlayNetworking.send(player, new TalkPayloads.Show(session.folk.getId(), open, r.fullName(), title, brain.settlement.name(), speech, options));
	}

	private record Ranked(DialogueProvider.Option option, int index) {
	}

	/** Forgets conversations whose folk has gone (folded away, died) or whose player wandered off. */
	public void tick() {
		this.sessions.entrySet().removeIf(e -> {
			Folk f = e.getValue().folk;
			return f.isRemoved() || f.brain() == null;
		});
	}

	public @Nullable Entity talkingTo(ServerPlayer player) {
		Session s = this.sessions.get(player.getUUID());
		return s == null ? null : s.folk;
	}
}
