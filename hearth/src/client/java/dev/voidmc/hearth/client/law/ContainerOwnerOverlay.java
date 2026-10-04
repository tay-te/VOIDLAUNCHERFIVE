package dev.voidmc.hearth.client.law;

import dev.voidmc.hearth.client.mixin.law.ContainerScreenAccessor;
import dev.voidmc.hearth.law.net.LawPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** "Owned by Edda Miller" with the red hand, above the panel of a container that belongs to someone else. */
final class ContainerOwnerOverlay {
	private static int containerId = -1;
	private static @Nullable Component owner;

	private ContainerOwnerOverlay() {
	}

	static void accept(LawPayloads.ContainerOwner payload) {
		containerId = payload.containerId();
		owner = payload.owner();
	}

	static void reset() {
		containerId = -1;
		owner = null;
	}

	static void draw(Screen screen, GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (owner == null || !(screen instanceof AbstractContainerScreen<?> container) || container.getMenu().containerId != containerId) {
			return;
		}
		ContainerScreenAccessor acc = (ContainerScreenAccessor) container;
		var font = Minecraft.getInstance().font;
		Component line = Component.translatable("hearth.law.hud.owned", owner);
		int w = font.width(line) + 22;
		int x = acc.hearth$leftPos() + (acc.hearth$imageWidth() - w) / 2;
		int y = acc.hearth$topPos() - 18;
		g.fill(x - 3, y - 2, x + w + 3, y + 14, 0xC0180C08);
		g.fill(x - 3, y - 2, x + w + 3, y - 1, 0xFFB02A20);
		LawGui.sprite(g, x, y - 2, 0, 0, 16, 16);
		g.text(font, line, x + 19, y + 2, 0xFFF0D8D0, true);
	}
}
