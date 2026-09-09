package dev.voidpvp.client.actuator;

/**
 * The Old input actuator (§6.7): the three answers this mod gives in place of the ones
 * {@code MinecraftClient} would have computed.
 *
 * <p>All three Mixins are the same shape as the Overlay mod's view-bobbing redirects — they
 * answer a <em>guard</em> differently rather than cancelling the method behind it — so with the
 * mod off every one of them returns the value vanilla read and the client runs byte-identical
 * vanilla. Every claim below was read out of Loom's named 1.8.9 jar
 * ({@code net.legacyfabric:yarn:1.8.9+build.604}) and a 1.7.10 client jar remapped with Legacy
 * Fabric yarn {@code 1.7.10+build.603}.</p>
 *
 * <h2>{@code use_while_digging}</h2>
 *
 * <p>1.8.9 {@code MinecraftClient.doUse()} opens, at offsets 0-10, with
 * {@code if (this.interactionManager.isBreakingBlock()) return;}. That is the only call to
 * {@code isBreakingBlock()} anywhere in the class, so the redirect needs no ordinal. 1.7.10's
 * {@code doUse} starts straight at {@code blockPlaceDelay = 4} and its
 * {@code ClientPlayerInteractionManager} has no {@code isBreakingBlock()} at all — only the
 * private field the 1.8 method was written to expose.</p>
 *
 * <h2>{@code dig_while_using}</h2>
 *
 * <p>1.8.9 {@code MinecraftClient.handleBlockBreaking(Z)V}, offsets 9-26:
 * {@code if (this.attackCooldown > 0 || this.player.isUsingItem()) return;}. 1.7.10's, at the
 * same offsets 9-16, is {@code if (this.attackCooldown > 0) return;} — the cooldown term alone.
 * </p>
 *
 * <p><b>{@code isUsingItem()} is called three times in 1.8.9's {@code MinecraftClient}</b> —
 * once here and twice in {@code tick} (offsets 1673 and 1835), both of which are byte-identical
 * to 1.7.10's and neither of which this mod may touch. That is why the injection is scoped to
 * {@code handleBlockBreaking} by method name rather than by an ordinal over the class.</p>
 *
 * <h2>{@code no_miss_delay} — and why it is not "suppress the cooldown"</h2>
 *
 * <p>1.8.9 {@code doAttack} writes {@code attackCooldown = 10} behind
 * {@code hasLimitedAttackSpeed()} in two places: offsets 33-46, on the null-hit-result path, and
 * offsets 162-175, the tail the {@code tableswitch} sends both its MISS arm <em>and</em> a
 * BLOCK hit that resolved to {@code Material.AIR} to (the {@code if_acmpeq 162} at offset 140).
 * 1.7.10 has both of those writes too: offsets 33-46 for the null path and 156-169 for the
 * air-block path. Its {@code lookupswitch} has keys 1 and 2 only — ENTITY and BLOCK — and sends
 * everything else to {@code default: 192}, which is the bare {@code return}.</p>
 *
 * <p>So <b>only the MISS case differs</b>, and in 1.8.9 the MISS case shares its instructions
 * with the air-block case. A redirect on that second {@code hasLimitedAttackSpeed()} therefore
 * cannot be scoped by injection point alone: it has to read {@code MinecraftClient.result} and
 * let the air-block case through. Suppressing the field write generally, or the whole
 * {@code hasLimitedAttackSpeed()} tail, would go <em>past</em> 1.7 rather than back to it.</p>
 */
public final class OldInput {

    private OldInput() {
    }

    /**
     * What {@code doUse}'s opening {@code interactionManager.isBreakingBlock()} should report.
     *
     * @param breakingBlock  what the interaction manager actually said
     * @param on             {@code old_input.on}
     * @param useWhileDigging {@code use_while_digging}
     * @return {@code false} — "not breaking" — only when the setting is asking for it; the guard
     *         is otherwise vanilla's own answer, so this can never make a right click be
     *         discarded that vanilla would have acted on
     */
    public static boolean isBreakingBlock(boolean breakingBlock, boolean on,
                                          boolean useWhileDigging) {
        return breakingBlock && !(on && useWhileDigging);
    }

    /**
     * What {@code handleBlockBreaking}'s {@code player.isUsingItem()} should report.
     *
     * <p>Scoped to that one call site; the two in {@code tick} keep vanilla's answer.</p>
     */
    public static boolean isUsingItemForBreaking(boolean usingItem, boolean on,
                                                 boolean digWhileUsing) {
        return usingItem && !(on && digWhileUsing);
    }

    /**
     * What the second {@code interactionManager.hasLimitedAttackSpeed()} in {@code doAttack} —
     * the one on the shared MISS / air-block tail — should report.
     *
     * <p>{@code miss} is the runtime discriminator the injection point cannot supply:
     * {@code MinecraftClient.result.type == BlockHitResult.Type.MISS}. On an air block it is
     * {@code BLOCK}, and 1.7.10 armed the same cooldown there, so the answer stays vanilla's.
     * </p>
     *
     * @param limited     what the interaction manager actually said — survival, essentially
     * @param on          {@code old_input.on}
     * @param noMissDelay {@code no_miss_delay}
     * @param miss        the hit result this click resolved onto was a MISS
     */
    public static boolean hasLimitedAttackSpeed(boolean limited, boolean on, boolean noMissDelay,
                                                boolean miss) {
        return limited && !(on && noMissDelay && miss);
    }
}
