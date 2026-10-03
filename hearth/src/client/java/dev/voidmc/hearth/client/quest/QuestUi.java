package dev.voidmc.hearth.client.quest;

import dev.voidmc.hearth.quest.model.Objective;
import dev.voidmc.hearth.quest.model.ObjectiveKind;
import dev.voidmc.hearth.quest.model.QuestInstance;
import dev.voidmc.hearth.quest.model.QuestStatus;
import dev.voidmc.hearth.quest.model.QuestType;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** Shared drawing and wording for the quest screens and HUD: ink colours, paper, wood, icons, objective lines. */
final class QuestUi {
	static final int INK = 0xFF3B2A1A;
	static final int INK_FADED = 0xFF7A6248;
	static final int INK_GREEN = 0xFF2F6B2F;
	static final int INK_RED = 0xFF8B2020;
	static final int INK_GOLD = 0xFF8A6418;
	static final int PAPER = 0xFFEFE3C6;
	static final int PAPER_EDGE = 0xFFC9B48A;
	static final int PAPER_SHADOW = 0x66000000;
	static final Identifier PLANKS = Identifier.withDefaultNamespace("textures/block/spruce_planks.png");
	static final Identifier FRAME = Identifier.withDefaultNamespace("textures/block/dark_oak_planks.png");

	private QuestUi() {
	}

	/** Tiles a block texture at twice its size (16-texel textures repeat). */
	static void wood(GuiGraphicsExtractor g, Identifier texture, int x, int y, int w, int h) {
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, w, h, w / 2, h / 2, 16, 16);
	}

	/** A sheet of paper with a soft shadow and a darker edge. */
	static void paper(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x + 2, y + 2, x + w + 2, y + h + 2, PAPER_SHADOW);
		g.fill(x, y, x + w, y + h, PAPER_EDGE);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PAPER);
		// A faint fold across the middle, as paper that was carried.
		g.fill(x + 1, y + h / 2, x + w - 1, y + h / 2 + 1, 0x14000000);
	}

	/** A round-headed pin: red, or brass for offers the player cannot take yet. */
	static void pin(GuiGraphicsExtractor g, int cx, int cy, boolean brass) {
		int head = brass ? 0xFFB08D3A : 0xFFB3262A;
		int shine = brass ? 0xFFE8CF7A : 0xFFE86A6A;
		g.fill(cx - 2, cy - 1, cx + 2, cy + 2, 0xFF2A1A10);
		g.fill(cx - 2, cy - 2, cx + 2, cy + 1, head);
		g.fill(cx - 1, cy - 3, cx + 1, cy + 2, head);
		g.fill(cx - 1, cy - 2, cx, cy - 1, shine);
	}

	/** Wrapped text; returns the y below it. */
	static int wrapped(GuiGraphicsExtractor g, Font font, String text, int x, int y, int width, int color, int maxLines) {
		if (text.isEmpty()) {
			return y;
		}
		List<FormattedCharSequence> lines = font.split(Component.literal(text), width);
		for (int i = 0; i < lines.size() && i < maxLines; i++) {
			g.text(font, lines.get(i), x, y, color, false);
			y += font.lineHeight;
		}
		return y;
	}

	static ItemStack icon(QuestType type) {
		return new ItemStack(switch (type) {
			case SUPPLY -> Items.OAK_LOG;
			case BOUNTY -> Items.IRON_SWORD;
			case COURIER -> Items.PAPER;
			case ESCORT -> Items.LEAD;
			case EXPLORATION -> Items.COMPASS;
			case SCOUTING -> Items.SPYGLASS;
			case GATHERING -> Items.WHEAT;
			case PERSONAL -> Items.POPPY;
		});
	}

	/** "Bring 24 × Spruce Log (12/24, 8 carried)". */
	static String objectiveLine(Objective o, @Nullable LocalPlayer player) {
		StringBuilder s = new StringBuilder(o.label);
		if (o.count > 1) {
			s.append(" (").append(o.progress).append('/').append(o.count);
			if (o.kind == ObjectiveKind.DELIVER && player != null && !o.done()) {
				int carried = carried(player, o.target);
				if (carried > 0) {
					s.append(", ").append(Component.translatable("hearth.quest.carried", carried).getString());
				}
			}
			s.append(')');
		}
		return s.toString();
	}

	/** How many of an item or tag the player carries (main inventory), for the "carried" hint. */
	static int carried(LocalPlayer player, String target) {
		int n = 0;
		boolean tag = target.startsWith("#");
		Identifier id = Identifier.tryParse(tag ? target.substring(1) : target);
		if (id == null) {
			return 0;
		}
		TagKey<net.minecraft.world.item.Item> key = tag ? TagKey.create(net.minecraft.core.registries.Registries.ITEM, id) : null;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.isEmpty()) {
				continue;
			}
			if (tag ? stack.is(key) : BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Where the tracked quest wants the player now: the open objective's place, or the giver's town to hand in. */
	static @Nullable BlockPos waypoint(QuestInstance q) {
		if (q.status == QuestStatus.READY) {
			return q.turnInPos;
		}
		Objective o = q.firstOpen();
		if (o == null) {
			return q.turnInPos;
		}
		return switch (o.kind) {
			case DELIVER -> q.turnInPos;
			case KILL, BIOME, SCOUT -> null;
			default -> o.pos;
		};
	}

	/** Days and hours left before a deadline, or empty. */
	static String timeLeft(QuestInstance q, long gameTime) {
		if (!q.hasDeadline()) {
			return "";
		}
		long left = Math.max(0, q.timing.deadlineTick() - gameTime);
		long days = left / 24000;
		long hours = (left % 24000) / 1000;
		return Component.translatable("hearth.quest.timeleft", days, hours).getString();
	}

	static long gameTime() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0 : mc.level.getGameTime();
	}
}
