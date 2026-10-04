package dev.voidmc.hearth.client.standing;

import dev.voidmc.hearth.api.StandingService.Title;
import dev.voidmc.hearth.standing.logic.Titles;
import dev.voidmc.hearth.standing.net.StandingPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A small standing line along the bottom of the conversation, market and bank screens: the town, the player's title and
 * standing there and — in conversation — what this resident thinks of them and how the last speech check went. Those
 * screens belong to the npc and economy modules, so the line is drawn after them through Fabric's screen events and the
 * screens are recognised by name (nothing breaks if they are renamed; the line just stops appearing).
 */
final class ScreenLine {
	private static final Set<String> TRADE = Set.of("MarketScreen", "BankScreen");
	private static final String TALK = "ConversationScreen";
	private static final String PACKAGE = "dev.voidmc.hearth.client.";

	private ScreenLine() {
	}

	static void install() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			String name = screen.getClass().getSimpleName();
			if (!screen.getClass().getName().startsWith(PACKAGE) || !(TRADE.contains(name) || TALK.equals(name))) {
				return;
			}
			boolean talk = TALK.equals(name);
			ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, a) -> draw(s, g, talk));
		});
	}

	private static void draw(Screen screen, GuiGraphicsExtractor g, boolean talk) {
		StandingPayloads.Here here = ClientStanding.here();
		StandingPayloads.Talk t = talk ? ClientStanding.talk() : null;
		Font font = Minecraft.getInstance().font;
		List<Component> lines = new ArrayList<>(3);
		List<Integer> colours = new ArrayList<>(3);
		if (here != null) {
			Title title = ClientStanding.title(here.title());
			lines.add(Component.translatable("hearth.standing.screen.town", here.town(), Component.translatable(title.translationKey()),
				ClientStanding.signed(here.standing())));
			colours.add(Titles.colour(title));
		}
		if (t != null && !t.resident().isEmpty()) {
			lines.add(Component.translatable("hearth.standing.screen.opinion", t.resident(), ClientStanding.opinionWord(t.opinion()),
				ClientStanding.signed(t.opinion())));
			colours.add(t.opinion() >= 10 ? 0xFF9EE09E : t.opinion() <= -10 ? 0xFFE09E7E : 0xFFE0D8C0);
			if (!t.result().getString().isEmpty()) {
				lines.add(t.result());
				colours.add(0xFFE8E0C8);
			}
		}
		if (lines.isEmpty()) {
			return;
		}
		int width = 0;
		for (Component c : lines) {
			width = Math.max(width, font.width(c));
		}
		width = Math.min(width, screen.width - 16);
		int h = lines.size() * (font.lineHeight + 2) + 4;
		int cx = screen.width / 2;
		int y0 = screen.height - h - 4;
		g.fill(cx - width / 2 - 6, y0 - 2, cx + width / 2 + 6, y0 + h, 0xB0100C08);
		int y = y0 + 2;
		for (int i = 0; i < lines.size(); i++) {
			Component c = lines.get(i);
			String clipped = font.plainSubstrByWidth(c.getString(), width);
			if (clipped.length() < c.getString().length()) {
				g.centeredText(font, clipped, cx, y, colours.get(i));
			} else {
				g.centeredText(font, c, cx, y, colours.get(i));
			}
			y += font.lineHeight + 2;
		}
	}
}
