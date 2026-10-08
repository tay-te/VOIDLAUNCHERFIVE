/**
 * <b>Not Mettle's code and never shipped in Mettle's jar.</b> A copy of the parts of VOID Hearth's api
 * ({@code hearth/src/main/java/dev/voidmc/hearth/api} on branch {@code hearth}) that Mettle's {@code compat.hearth}
 * package calls, so Mettle compiles without Hearth. At runtime the real classes come with Hearth, and Mettle only touches
 * them when Hearth is loaded.
 *
 * <p>Whole files are verbatim copies (PlayerSkills, PriceModifier, DialogueProvider, QuestEvents, QuestType, MarketEvents,
 * SettlementTier, JobType, AgeStage, Culture). HearthServices, EconomyService, HearthWorld, Settlement and Resident keep only the
 * members Mettle and its client tour use, with Hearth's exact signatures; bodies Mettle never runs are stubbed. When Hearth's api changes,
 * copy the file again ({@code git show hearth:hearth/src/main/java/dev/voidmc/hearth/api/<X>.java}).
 */
@NullMarked
package dev.voidmc.hearth.api;

import org.jspecify.annotations.NullMarked;
