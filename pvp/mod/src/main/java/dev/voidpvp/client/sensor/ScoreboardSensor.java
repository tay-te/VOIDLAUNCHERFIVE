package dev.voidpvp.client.sensor;

import com.google.gson.JsonObject;
import dev.voidpvp.client.visual.VisualConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Reads the sidebar once a client tick and hands back a {@code scoreboard} payload when — and
 * only when — it changed, for the {@code scoreboard} HUD mod.
 *
 * <p>Which objective is the sidebar follows {@code InGameHud.render} (offsets 1102-1179): the
 * team-coloured sidebar slot ({@code 3 + colour index}) of the player's own team if it has one,
 * otherwise slot 1. Silent while the mod is off, and it forgets what it last sent, so switching
 * the mod on pushes the board at once.</p>
 */
public final class ScoreboardSensor {

    private String last;
    private volatile boolean forget;

    /** The page reloaded: the next poll sends the board again even if it has not changed. */
    public void forget() {
        forget = true;
    }

    /** The payload to emit this tick, or {@code null} when there is nothing new. Game thread. */
    public JsonObject poll(MinecraftClient mc) {
        if (!VisualConfig.scoreboardOn) {
            last = null;
            return null;
        }
        JsonObject payload = read(mc);
        String key = payload.toString();
        if (!forget && key.equals(last)) {
            return null;
        }
        forget = false;
        last = key;
        return payload;
    }

    private static JsonObject read(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            return ScoreboardLines.hidden();
        }
        Scoreboard board = mc.world.getScoreboard();
        if (board == null) {
            return ScoreboardLines.hidden();
        }
        ScoreboardObjective objective = null;
        Team own = board.getPlayerTeam(mc.player.getTranslationKey());
        if (own != null && own.getFormatting() != null) {
            int colour = own.getFormatting().getColorIndex();
            if (colour >= 0) {
                objective = board.getObjectiveForSlot(3 + colour);
            }
        }
        if (objective == null) {
            objective = board.getObjectiveForSlot(1);
        }
        if (objective == null) {
            return ScoreboardLines.hidden();
        }
        Collection<ScoreboardPlayerScore> scores = board.getAllPlayerScores(objective);
        List<ScoreboardPlayerScore> sorted = new ArrayList<ScoreboardPlayerScore>(scores);
        Collections.sort(sorted, ScoreboardPlayerScore.field_5683);
        List<ScoreboardLines.Entry> entries = new ArrayList<ScoreboardLines.Entry>(sorted.size());
        for (ScoreboardPlayerScore s : sorted) {
            String name = s.getPlayerName();
            String text = Team.decorateName(board.getPlayerTeam(name), name);
            entries.add(new ScoreboardLines.Entry(name, text, s.getScore()));
        }
        return ScoreboardLines.payload(objective.getDisplayName(), entries);
    }
}
