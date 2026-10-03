package dev.voidmc.hearth.client.quest;

import dev.voidmc.hearth.quest.QuestRegistry;
import dev.voidmc.hearth.quest.net.QuestPayloads;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** A toast in the advancement style for a quest taken, ready to hand in, completed or failed. */
final class QuestToast implements Toast {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/advancement");
	private static final long DISPLAY_MS = 5000;
	private final QuestPayloads.Toast toast;
	private final ItemStack icon;
	private Visibility visibility = Visibility.SHOW;

	QuestToast(QuestPayloads.Toast toast) {
		this.toast = toast;
		this.icon = switch (toast.kind()) {
			case ACCEPTED -> new ItemStack(QuestRegistry.JOURNAL);
			case READY -> new ItemStack(Items.BELL);
			case COMPLETED -> new ItemStack(Items.EMERALD);
			case FAILED -> new ItemStack(Items.WITHER_ROSE);
		};
	}

	@Override
	public Visibility getWantedVisibility() {
		return this.visibility;
	}

	@Override
	public void update(ToastManager manager, long fullyVisibleForMs) {
		this.visibility = fullyVisibleForMs >= DISPLAY_MS * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
	}

	@Override
	public @Nullable SoundEvent getSoundEvent() {
		return this.toast.kind() == QuestPayloads.Toast.Kind.COMPLETED ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, Font font, long fullyVisibleForMs) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, this.width(), this.height());
		int color = switch (this.toast.kind()) {
			case ACCEPTED -> 0xFFFFFF55;
			case READY -> 0xFF8EE08E;
			case COMPLETED -> 0xFFFFAA00;
			case FAILED -> 0xFFFF6B6B;
		};
		String key = "hearth.quest.toast." + this.toast.kind().name().toLowerCase(java.util.Locale.ROOT);
		g.text(font, Component.translatable(key), 30, 7, color, false);
		String line = this.toast.title();
		g.text(font, font.plainSubstrByWidth(line, 125), 30, 18, 0xFFFFFFFF, false);
		g.fakeItem(this.icon, 8, 8);
	}
}
