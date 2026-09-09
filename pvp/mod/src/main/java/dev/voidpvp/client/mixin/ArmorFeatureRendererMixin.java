package dev.voidpvp.client.mixin;

import dev.voidpvp.client.state.LiveState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Overlay mod's {@code hide_own_armor} (§6.7).
 *
 * <p>Armour is drawn as a <em>feature layer</em> on top of the entity model, and every armour
 * layer in the game — the biped one, the zombie villager's, the wither's — extends this class
 * and inherits this one {@code render}. So the abstract base is the funnel, exactly as
 * {@code EntityRenderDispatcher.renderHitbox} is the funnel for debug boxes, and one injection
 * covers every model rather than one per subclass.</p>
 *
 * <p><b>Only the client's own player.</b> This setting is the one thing in the Overlay mod that
 * changes nothing in a first-person fight — your armour is on screen in third person and in the
 * inventory preview and nowhere else — so it is a preference about your own model. Suppressing
 * every entity's armour would be a different and much larger mod: what an opponent is wearing
 * decides whether you can kill them.</p>
 */
@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void void$hideOwnArmor(LivingEntity entity, float limbAngle, float limbDistance,
                                   float tickDelta, float animationProgress, float headYaw,
                                   float headPitch, float scale, CallbackInfo ci) {
        LiveState state = LiveState.get();
        if (!state.overlayOn || !state.overlayHideOwnArmor) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && entity != null && entity == mc.player) {
            ci.cancel();
        }
    }
}
