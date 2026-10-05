package dev.voidmc.mettle.client.gear;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.gear.BeltUse;
import dev.voidmc.mettle.gear.GearComponents;
import dev.voidmc.mettle.gear.GearSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Gear on the client: the belt keys (R uses the last belt pocket; "Belt pocket 1–4" are unbound until the player binds
 * them; the radial comes with the overlay), the gear tooltip, and the worn-gear render layer.
 */
public final class GearClient {
	/** Ticks a belt use may take to start before the client stops holding the use key for it. */
	private static final int START_WINDOW = 20;

	public static KeyMapping beltUseKey;
	public static final KeyMapping[] POCKET_KEYS = new KeyMapping[4];
	private static int lastPocket;
	private static boolean beltHold;
	private static boolean sawUse;
	private static int holdTicks;

	private GearClient() {
	}

	public static void init() {
		// Same category as the K key (the record compares by id; MettleClient registers it).
		KeyMapping.Category category = new KeyMapping.Category(Mettle.id("character"));
		beltUseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mettle.belt_use", InputConstants.KEY_R, category));
		for (int i = 0; i < POCKET_KEYS.length; i++) {
			POCKET_KEYS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mettle.belt_pocket_" + (i + 1), InputConstants.UNKNOWN.getValue(), category));
		}

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (beltUseKey.consumeClick()) {
				useBelt(mc, lastPocket);
			}
			for (int i = 0; i < POCKET_KEYS.length; i++) {
				while (POCKET_KEYS[i].consumeClick()) {
					lastPocket = i;
					useBelt(mc, i);
				}
			}
			if (beltHold) {
				if (mc.player == null) {
					beltHold = false;
				} else if (mc.player.isUsingItem()) {
					sawUse = true;
				} else if (sawUse || ++holdTicks > START_WINDOW) {
					beltHold = false;
				}
			}
		});

		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> tooltip(stack, lines));

		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				@SuppressWarnings("unchecked")
				RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) avatar;
				helper.register(new GearLayer(parent));
			}
		});
	}

	/** Asks the server to use a belt pocket (0-based). Also the client game test's way in. */
	public static void useBelt(Minecraft mc, int pocket) {
		if (mc.player == null || mc.gui.screen() != null || !ClientPlayNetworking.canSend(BeltUse.Request.TYPE)) {
			return;
		}
		ClientPlayNetworking.send(new BeltUse.Request(pocket));
		beltHold = true;
		sawUse = false;
		holdTicks = 0;
	}

	/** While a belt use runs, the use key counts as held, so eating and drinking finish (GearMinecraftMixin). */
	public static boolean holdsUse(KeyMapping key) {
		Minecraft mc = Minecraft.getInstance();
		return beltHold && mc.player != null && key == mc.options.keyUse && mc.player.isUsingItem()
			&& mc.player.getUseItem().getUseDuration(mc.player) < 1200;
	}

	/** Whether to leave this player's gear off the model (reserved for the "show my gear" option, UI.md §10). */
	static boolean hidden(Player player) {
		return false;
	}

	private static void tooltip(ItemStack stack, List<Component> lines) {
		GearSpec spec = stack.get(GearComponents.GEAR);
		if (spec == null) {
			return;
		}
		List<Component> add = new ArrayList<>();
		String kg = kg(spec.capacityKg());
		add.add(switch (spec.slot()) {
			case BACK -> Component.translatable("mettle.gear.tooltip.back", kg, spec.pocketCount());
			case BELT -> Component.translatable("mettle.gear.tooltip.belt", kg, spec.pocketCount());
			case QUIVER -> Component.translatable("mettle.gear.tooltip.quiver", spec.pocketCount());
		});
		ItemContainerContents pockets = stack.get(GearComponents.POCKETS);
		if (pockets != null) {
			List<ItemStack> inside = pockets.nonEmptyItemCopyStream().toList();
			for (int i = 0; i < Math.min(5, inside.size()); i++) {
				add.add(Component.translatable("mettle.gear.tooltip.item", inside.get(i).getHoverName(), inside.get(i).getCount())
					.withStyle(ChatFormatting.GRAY));
			}
			if (inside.size() > 5) {
				add.add(Component.translatable("mettle.gear.tooltip.more", inside.size() - 5).withStyle(ChatFormatting.GRAY));
			}
		}
		add.add(Component.translatable("mettle.gear.tooltip.wear").withStyle(ChatFormatting.DARK_GRAY));
		add.set(0, add.get(0).copy().withStyle(ChatFormatting.GRAY));
		lines.addAll(Math.min(1, lines.size()), add);
	}

	private static String kg(float kg) {
		return kg == Math.round(kg) ? Integer.toString(Math.round(kg)) : String.format(Locale.ROOT, "%.1f", kg);
	}
}
