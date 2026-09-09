package dev.voidpvp.client.mixin;

import dev.voidpvp.client.VoidClient;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps this mod's {@code GameOptions} overrides out of {@code options.txt}.
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
 *
 * <p><b>{@code fov} is the same field family and the same hazard, so it is the same two
 * injections and not a second mechanism.</b> The FOV changer holds {@code GameOptions.fov}, a
 * public float on this class that {@code save()} writes exactly as it writes {@code gamma}; the
 * failure it would produce is identical, one field over — {@code options.txt} would come to read
 * the mod's 90, {@code VoidClient.playerFov} would capture 90 as the value to restore, and the
 * player's own field of view would be gone. {@code schema/mods/fov.json}'s {@code $comment}
 * spells the whole thing out and points here.</p>
 *
 * <p>Not a {@code save()} suppression, for either field: the player's other options still have
 * to reach disk, and a mod that quietly stops the game saving its settings is a much larger
 * surprise than the one being fixed. Two fields, one swap each, restored on the way out —
 * including on an early return, because {@code @At("RETURN")} injects at every one.</p>
 */
@Mixin(GameOptions.class)
public abstract class GameOptionsMixin {

    private float void$gammaDuringSave;
    private boolean void$gammaSwapped;

    private float void$fovDuringSave;
    private boolean void$fovSwapped;

    @Inject(method = "save", at = @At("HEAD"))
    private void void$restoreGammaBeforeSave(CallbackInfo ci) {
        VoidClient client = VoidClient.get();
        if (client == null) {
            return;
        }
        GameOptions options = (GameOptions) (Object) this;
        Float player = client.playerGamma();
        if (player != null) {
            void$gammaDuringSave = options.gamma;
            void$gammaSwapped = true;
            options.gamma = player.floatValue();
        }
        // Independent of gamma: the two mods are switched on and off separately, so a run with
        // one of them on must still swap that one. Sharing a single flag between them was the
        // obvious shortcut and is wrong in the case that matters — FOV changer on, Fullbright
        // off, which is a whole configuration rather than an edge.
        Float playerFov = client.playerFov();
        if (playerFov != null) {
            void$fovDuringSave = options.fov;
            void$fovSwapped = true;
            options.fov = playerFov.floatValue();
        }
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void void$reapplyGammaAfterSave(CallbackInfo ci) {
        GameOptions options = (GameOptions) (Object) this;
        if (void$gammaSwapped) {
            void$gammaSwapped = false;
            options.gamma = void$gammaDuringSave;
        }
        if (void$fovSwapped) {
            void$fovSwapped = false;
            options.fov = void$fovDuringSave;
        }
    }
}
