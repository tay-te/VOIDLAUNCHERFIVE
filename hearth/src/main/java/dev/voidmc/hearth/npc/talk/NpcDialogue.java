package dev.voidmc.hearth.npc.talk;

import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.person.NpcData;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The npc module's own conversation options: small talk (order 10–30) and goodbye (last). Their answers are lines of
 * speech composed from the dialogue templates.
 */
public final class NpcDialogue implements DialogueProvider {
	public static final String NEWS = "npc:news";
	public static final String RUMOURS = "npc:rumours";
	public static final String ABOUT = "npc:about";
	public static final String BYE = "npc:bye";

	@Override
	public void collect(ServerPlayer player, Settlement settlement, Resident resident, List<Option> out) {
		out.add(new Option(NEWS, Component.translatable("hearth.npc.talk.news"), 10));
		out.add(new Option(RUMOURS, Component.translatable("hearth.npc.talk.rumours"), 20));
		out.add(new Option(ABOUT, Component.translatable("hearth.npc.talk.about"), 30));
		out.add(new Option(BYE, Component.translatable("hearth.npc.talk.bye"), 10_000));
	}

	@Override
	public boolean select(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		// Answered by the conversation through reply(), which has the folk at hand.
		return true;
	}

	/** The folk's answer to one of these options, or null to end the conversation (goodbye). */
	@Nullable Component reply(ServerPlayer player, FolkBrain brain, String optionId) {
		ServerLevel level = player.level();
		Settlement s = brain.settlement;
		Resident r = brain.resident;
		return switch (optionId) {
			case NEWS -> Component.literal(String.valueOf(Greetings.news(level, s, r, player, brain.folk.blockPosition(), brain.random, true)));
			case RUMOURS -> Component.literal(Rumours.tell(level, s, r, player, brain.folk.blockPosition(), brain.random));
			case ABOUT -> Component.literal(about(level, player, brain));
			case BYE -> {
				DialogueTemplates.Context ctx = Greetings.context(level, s, r, player, brain.folk.blockPosition());
				String line = DialogueTemplates.pick("farewell", ctx, brain.random);
				if (line != null) {
					player.sendSystemMessage(Component.literal(r.givenName).withStyle(net.minecraft.ChatFormatting.GOLD)
						.append(Component.literal(": " + DialogueTemplates.fill(line, Greetings.values(s, r, player))).withStyle(net.minecraft.ChatFormatting.GRAY)));
				}
				yield null;
			}
			default -> Component.empty();
		};
	}

	private static String about(ServerLevel level, ServerPlayer player, FolkBrain brain) {
		Settlement s = brain.settlement;
		Resident r = brain.resident;
		DialogueTemplates.Context ctx = Greetings.context(level, s, r, player, brain.folk.blockPosition());
		Map<String, String> v = Greetings.values(s, r, player);
		StringBuilder sb = new StringBuilder();
		append(sb, DialogueTemplates.pick("about", ctx, brain.random), v);
		append(sb, DialogueTemplates.pick("about.job", ctx, brain.random), v);
		NpcData d = NpcData.of(r);
		d.spouse().map(s::resident).ifPresent(sp -> {
			v.put("spouse", sp.givenName);
			append(sb, DialogueTemplates.pick("about.spouse", ctx, brain.random), v);
		});
		List<String> kids = names(s, d.children());
		if (!kids.isEmpty()) {
			v.put("children", join(kids));
			append(sb, DialogueTemplates.pick("about.children", ctx, brain.random), v);
		} else {
			List<String> parents = names(s, d.parents());
			if (!parents.isEmpty()) {
				v.put("other", join(parents));
				append(sb, DialogueTemplates.pick("about.parents", ctx, brain.random), v);
			}
		}
		return sb.toString().trim();
	}

	private static void append(StringBuilder sb, @Nullable String line, Map<String, String> v) {
		if (line != null) {
			sb.append(DialogueTemplates.fill(line, v)).append(' ');
		}
	}

	private static List<String> names(Settlement s, List<UUID> ids) {
		List<String> out = new ArrayList<>();
		for (UUID id : ids) {
			Resident r = s.resident(id);
			if (r != null) {
				out.add(r.givenName);
			}
		}
		return out;
	}

	private static String join(List<String> names) {
		if (names.size() == 1) {
			return names.get(0);
		}
		return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
	}
}
