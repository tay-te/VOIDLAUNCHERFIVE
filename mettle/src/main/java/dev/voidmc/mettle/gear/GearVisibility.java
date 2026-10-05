package dev.voidmc.mettle.gear;

import com.mojang.serialization.Codec;
import dev.voidmc.mettle.Mettle;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * "Show my gear on my character" (BURDEN.md §8, UI.md §10): a cosmetic flag synced to everyone, so a player who wants a
 * clean look is drawn without pack, belt and quiver by all clients. Set with {@code /mettle gear hide|show} until the
 * settings glyph in the K screen exists.
 */
public final class GearVisibility {
	public static final AttachmentType<Boolean> HIDDEN = AttachmentRegistry.create(Mettle.id("gear/hidden"), b -> b
		.persistent(Codec.BOOL)
		.copyOnDeath()
		.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private GearVisibility() {
	}

	public static boolean hidden(Player player) {
		return Boolean.TRUE.equals(player.getAttached(HIDDEN));
	}

	public static void set(ServerPlayer player, boolean hidden) {
		if (hidden) {
			player.setAttached(HIDDEN, true);
		} else {
			player.removeAttached(HIDDEN);
		}
	}

	static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("mettle")
			.then(Commands.literal("gear")
				.then(Commands.literal("hide").executes(c -> {
					set(c.getSource().getPlayerOrException(), true);
					c.getSource().sendSuccess(() -> Component.translatable("mettle.gear.hidden"), false);
					return 1;
				}))
				.then(Commands.literal("show").executes(c -> {
					set(c.getSource().getPlayerOrException(), false);
					c.getSource().sendSuccess(() -> Component.translatable("mettle.gear.shown"), false);
					return 1;
				})))));
	}
}
