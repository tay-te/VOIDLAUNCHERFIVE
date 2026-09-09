package dev.voidpvp.client.sensor;

/**
 * The three monotonic counters behind {@code bridge.json}'s {@code hits} object, and the rules
 * for moving them (§6.6).
 *
 * <p>Plain on purpose, like {@link KeyStateTracker} and {@link ServerWatcher}: the Mixin that
 * feeds this cannot be unit-tested, so everything that <em>decides</em> lives here and the Mixin
 * only reads facts off {@code MinecraftClient} and hands them over. The bug this class was
 * extracted for is exactly the kind a test catches and a comment does not — see
 * {@code SensorsTest.aSwingAtAirIsNotAHit}.</p>
 *
 * <p>Counters and not events, because {@code bridge.json} says so and gives the reason: an event
 * lost to a dropped tick leaves the combo wrong forever, while a counter that jumps by two is
 * still exactly right. No timeout lives here either — {@code combo.reset_ms} is a mod setting and
 * a mod setting in a sensor is how a sensor starts needing to know about mods.</p>
 */
public final class HitTally {

    /** What a swing resolved onto — 1.8.9's three {@code BlockHitResult.Type} arms. */
    public enum Swing {
        /** {@code MISS}: the crosshair was on nothing. A swing at air. */
        AIR,
        /** {@code BLOCK}: the crosshair was on a block. Mining, not fighting. */
        BLOCK,
        /** {@code ENTITY}: the crosshair was on an entity, so vanilla attacked it. */
        ENTITY
    }

    private int dealt;
    private int sprintDealt;
    private int taken;
    private int lastHurtTime;

    /**
     * One run of {@code MinecraftClient.doAttack} — one swing, whatever it hit.
     *
     * <p><b>What counts as landed.</b> Only {@link Swing#ENTITY}, and only when vanilla would
     * itself have delivered the attack. In 1.8.9 {@code doAttack}'s {@code ENTITY} arm is the one
     * that calls {@code ClientPlayerInteractionManager.attackEntity}, which sends
     * {@code PlayerInteractEntityC2SPacket(ATTACK)} and then — unless the player is spectating —
     * calls {@code PlayerEntity.attack(target)}. {@code PlayerEntity.attack} opens with
     * {@code if (!target.isAttackable()) return;}, so {@code isAttackable} is vanilla's own
     * definition of "this was an attack" and is reused here rather than invented. {@code isAlive}
     * is {@code !removed}: the crosshair raycast will still resolve onto an entity that has
     * already been removed client-side, and a swing through a corpse is not a hit.</p>
     *
     * <p><b>Not narrowed to {@code LivingEntity}.</b> A boat or a minecart is an attack that
     * landed, and {@code bridge.json} says {@code dealt} is "attacks the player has landed" — not
     * "attacks on things that fight back". Whether a boat should extend a combo is a policy
     * question, and the whole shape of the {@code hits} contract is that policy lives on the
     * client with {@code combo.reset_ms} and never in the sensor.</p>
     *
     * <p><b>Invulnerability is deliberately not modelled.</b> {@code Entity.invulnerable} is a
     * private field with no getter and is loaded from entity NBT on the server; the client is
     * never told it. Damage frames are the same story — the client's {@code LivingEntity.hurtTime}
     * is an echo of the server's hurt animation and arrives after the swing that would have to
     * consult it. So the client genuinely cannot know whether damage was applied, and a guess
     * would be wrong in exactly the case the counter is watched: a fast combo landing inside the
     * target's i-frames. {@code dealt} therefore means "the client delivered an attack onto a
     * live, attackable entity", which is the most an unprivileged client can honestly claim.</p>
     *
     * @param at                which of the three arms the swing took
     * @param targetAlive       the resolved entity's {@code isAlive()}; meaningless unless
     *                          {@code at} is {@link Swing#ENTITY}
     * @param targetAttackable  the resolved entity's {@code isAttackable()}; likewise
     * @param spectating        the player is in spectator mode, where
     *                          {@code attackEntity} sends the packet but skips
     *                          {@code PlayerEntity.attack} and the server ignores it
     * @param sprinting         {@code PlayerEntity.isSprinting()} as it was <em>before</em> the
     *                          attack was delivered — see {@link #sprintDealt()}. Meaningless
     *                          unless the swing lands, and ignored when it does not
     */
    public void swung(Swing at, boolean targetAlive, boolean targetAttackable,
            boolean spectating, boolean sprinting) {
        if (at == Swing.ENTITY && targetAlive && targetAttackable && !spectating) {
            dealt++;
            if (sprinting) {
                sprintDealt++;
            }
        }
    }

    /**
     * The per-tick reading of {@code ClientPlayerEntity.hurtTime}, which is how {@code taken}
     * moves.
     *
     * <p>{@code hurtTime} is set to a fixed value on damage and counts down, so it is a level and
     * not an edge: counting it directly would count one hit once per tick it stays up. The rising
     * edge is the hit.</p>
     */
    public void sawHurtTime(int hurtTime) {
        if (hurtTime > lastHurtTime) {
            taken++;
        }
        lastHurtTime = hurtTime;
    }

    /** Attacks landed since the session began. Monotonic; never reset on the wire. */
    public int dealt() {
        return dealt;
    }

    /**
     * Of those, the ones delivered while sprinting. Monotonic, and never larger than
     * {@link #dealt()}.
     *
     * <p><b>Why this counter is the sprint-reset measurement and not a proxy for it.</b> 1.8.9's
     * {@code PlayerEntity.attack} adds one to the knockback amount when the attacker is sprinting
     * (offsets 81-88) and then, in the branch that applies that knockback, calls
     * {@code setSprinting(false)} at offset 339. So a sprint-hit spends the sprint that made it
     * one: landing two in a row is not a matter of holding W, it requires the sprint to have been
     * restarted in between, which is the whole of what a W-tap is. The fraction of landed attacks
     * that were sprint-hits is therefore the success rate of the reset, measured on the outcome
     * rather than on the key.</p>
     *
     * <p><b>Which is why the caller must read {@code isSprinting()} before the attack, not
     * after.</b> The flag this counts is cleared by the very call that consumes it, so a reading
     * taken at the end of {@code doAttack} — after
     * {@code ClientPlayerInteractionManager.attackEntity} has run {@code PlayerEntity.attack} at
     * its own offsets 32-34 — is false for exactly the hits this is trying to count, and the
     * counter would sit at zero for a player doing it perfectly. {@code MinecraftClientMixin}
     * latches the flag at {@code doAttack} HEAD for that reason.</p>
     *
     * <p><b>Not a keypress tally.</b> Counting W releases would count the attempt; this counts
     * the result, and the two differ precisely where the mod is useful — a tap that came too late
     * releases the key and does not produce a sprint-hit.</p>
     */
    public int sprintDealt() {
        return sprintDealt;
    }

    /** Times the player has been hit since the session began. Monotonic. */
    public int taken() {
        return taken;
    }
}
