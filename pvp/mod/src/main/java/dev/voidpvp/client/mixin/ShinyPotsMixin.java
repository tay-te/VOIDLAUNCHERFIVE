package dev.voidpvp.client.mixin;

import dev.voidpvp.client.visual.VisualConfig;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The {@code shiny_pots} mod.
 *
 * <p>{@code ItemRenderer.renderItem(ItemStack, BakedModel)} is the one funnel every item draw
 * reaches — hotbar, inventory, hand, dropped — and it ends with
 * {@code if (stack.hasEnchantmentGlint()) renderGlint(model)} (offsets 89-97). Three hooks on
 * that:</p>
 * <ul>
 *   <li>{@code all_potions}: the glint test answers yes for any potion, including water bottles
 *       and server pots whose effects live only in lore (vanilla answers yes only when the effect
 *       list resolves).</li>
 *   <li>{@code glint_passes}: after vanilla's glint pass, the same private pass runs again, so a
 *       pass count of 2 is exactly twice vanilla's shimmer through vanilla's own shader-free
 *       blend.</li>
 *   <li>{@code tint_glint}: the glint colour constant ({@code -8372020}, the purple) becomes the
 *       potion's own liquid colour while a potion is being drawn.</li>
 * </ul>
 */
@Mixin(ItemRenderer.class)
public abstract class ShinyPotsMixin {

    @Shadow
    private void renderGlint(BakedModel model) {
        throw new AssertionError("shadowed");
    }

    @Unique
    private ItemStack void$potion;

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("HEAD"))
    private void void$remember(ItemStack stack, BakedModel model, CallbackInfo ci) {
        void$potion = VisualConfig.shinyPotsOn && stack != null
                && stack.getItem() instanceof PotionItem ? stack : null;
    }

    @Redirect(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasEnchantmentGlint()Z"))
    private boolean void$glint(ItemStack stack) {
        boolean vanilla = stack.hasEnchantmentGlint();
        return vanilla || (void$potion != null && VisualConfig.shinyAllPotions);
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/BakedModel;)V",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/client/render/item/ItemRenderer;renderGlint(Lnet/minecraft/client/render/model/BakedModel;)V"))
    private void void$extraPasses(ItemStack stack, BakedModel model, CallbackInfo ci) {
        if (void$potion == null) {
            return;
        }
        for (int i = 1; i < VisualConfig.shinyPasses; i++) {
            renderGlint(model);
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/BakedModel;)V",
            at = @At("RETURN"))
    private void void$forget(ItemStack stack, BakedModel model, CallbackInfo ci) {
        void$potion = null;
    }

    @ModifyConstant(method = "renderGlint", constant = @Constant(intValue = -8372020))
    private int void$tint(int vanilla) {
        ItemStack potion = void$potion;
        if (potion == null || !VisualConfig.shinyTint) {
            return vanilla;
        }
        int rgb = ((PotionItem) potion.getItem()).getDisplayColor(potion, 0);
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
}
