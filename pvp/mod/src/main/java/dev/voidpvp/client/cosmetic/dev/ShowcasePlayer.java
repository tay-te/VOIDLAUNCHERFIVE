package dev.voidpvp.client.cosmetic.dev;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.OtherClientPlayerEntity;
import net.minecraft.world.World;

/**
 * A mannequin for the showcase: a remote-player entity driven by a script instead of a server.
 * Moves through vanilla's own interpolation ({@code updateTrackedPositionAndAngles} with one step)
 * so limb swing, body yaw and every cosmetic that reads motion see exactly what a real remote
 * player's movement looks like.
 */
public final class ShowcasePlayer extends OtherClientPlayerEntity {

    /** Where the script wants this player each tick. */
    public interface Script {
        /**
         * Fill {@code out}: x, y, z, yaw, pitch, headYawOffset, onGround (1/0), sneaking (1/0), and
         * optionally (left 0 otherwise) swing (1 starts an arm swing this tick), hurt (1 starts
         * the red hurt flash, as a server hit would), dead (1 holds health at zero: the death
         * fall, never removed).
         */
        void at(int tick, double[] out);
    }

    private final Script script;
    private final double[] pose = new double[11];
    private int hurtLeft;
    private int ticks;

    public ShowcasePlayer(World world, GameProfile profile, Script script) {
        super(world, profile);
        this.script = script;
        script.at(0, pose);
        refreshPositionAndAngles(pose[0], pose[1], pose[2], (float) pose[3], (float) pose[4]);
        bodyYaw = prevBodyYaw = headYaw = prevHeadYaw = (float) pose[3];
        noClip = true;
    }

    @Override
    public void tick() {
        ticks++;
        pose[8] = pose[9] = pose[10] = 0;
        script.at(ticks, pose);
        updateTrackedPositionAndAngles(pose[0], pose[1], pose[2], (float) pose[3], (float) pose[4], 1, false);
        if (pose[8] > 0.5) {
            swingHand();
        }
        if (pose[9] > 0.5) {
            hurtLeft = 10;
        }
        boolean dead = pose[10] > 0.5;
        if (dead) {
            setHealth(0);
        }
        super.tick();
        headYaw = (float) (pose[3] + pose[5]);
        onGround = pose[6] > 0.5;
        setSneaking(pose[7] > 0.5);
        // Otherwise a mannequin cannot be hurt or die: the client-side player ticks hunger and
        // health without a server to correct them, and a dying mannequin falls over mid-shot.
        // Only the script hurts it (a flash exactly like a server hit's) or kills it (held in
        // the fall, short of the tick that would remove it).
        if (dead) {
            setHealth(0);
            deathTime = Math.min(deathTime, 15);
        } else {
            setHealth(getMaxHealth());
            deathTime = 0;
        }
        maxHurtTime = 10;
        hurtTime = hurtLeft;
        if (hurtLeft > 0) {
            hurtLeft--;
        }
    }

    public int ticks() {
        return ticks;
    }
}
