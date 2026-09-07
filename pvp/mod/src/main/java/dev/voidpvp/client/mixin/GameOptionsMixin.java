package dev.voidpvp.client.mixin;

import dev.voidpvp.client.VoidClient;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Fullbright's gamma override out of {@code options.txt}.
 *
 * <p>Fullbright works by writing {@code GameOptions.gamma}, which is the only lever 1.8.9 gives
 * — and {@code GameOptions.save()} writes that field to disk. So every time the game saved its
 * options with Fullbright on (which it does on almost any settings change, and on quit), the
 * mod's value was persisted as the player's own brightness.</p>
 *
 * <p><b>The damage is not that the file is untidy; it is that the setting stops being a
 * setting.</b> Once {@code gamma:10.0} is in {@code options.txt}, the next launch starts at full
 * brightness before the mod has done anything, {@code VoidClient} captures <em>that</em> as the
 * value to restore, and turning Fullbright off restores it to full brightness. The mod is then
 * permanently on, with its own switch reporting off, and the player's real brightness preference
 * is gone with no way back except editing the file by hand. Measured: after a single audit run,
 * {@code options.txt} read {@code gamma:10.0} and a Fullbright-off frame was pixel-identical to
 * Fullbright at gamma 15.</p>
 *
 * <p>So the field is put back to the player's own value for the duration of the write and
 * restored afterwards. A no-op whenever Fullbright is off, because there is nothing saved to put
 * back.</p>
 */
@Mixin(GameOptions.class)
public abstract class GameOptionsMixin {

    private float void$gammaDuringSave;
    private boolean void$gammaSwapped;

    @Inject(method = "save", at = @At("HEAD"))
    private void void$restoreGammaBeforeSave(CallbackInfo ci) {
        VoidClient client = VoidClient.get();
        if (client == null) {
            return;
        }
        Float player = client.playerGamma();
        if (player == null) {
            return;
        }
        GameOptions options = (GameOptions) (Object) this;
        void$gammaDuringSave = options.gamma;
        void$gammaSwapped = true;
        options.gamma = player.floatValue();
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void void$reapplyGammaAfterSave(CallbackInfo ci) {
        if (!void$gammaSwapped) {
            return;
        }
        void$gammaSwapped = false;
        ((GameOptions) (Object) this).gamma = void$gammaDuringSave;
    }
}
