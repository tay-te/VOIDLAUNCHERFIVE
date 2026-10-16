package dev.voidpvp.client.cosmetic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What can be equipped when. The wire says who wears what, never who owns what, so ownership is
 * known only from a dev file; otherwise an attached launcher means "apply and let the backend
 * revert", and no launcher means free items only.
 */
class OwnershipTest {

    private static final class RecordingSink implements CosmeticState.Sink {
        final List<String> equips = new ArrayList<String>();

        @Override
        public void query(List<String> uuids) {
        }

        @Override
        public void emote(String id) {
        }

        @Override
        public void equip(String slot, String id, String tint) {
            equips.add(slot + "=" + id);
        }

        @Override
        public void emotes(List<String> ids) {
        }
    }

    @Test
    @DisplayName("without a launcher only free items can be worn")
    void offline() {
        CosmeticState s = new CosmeticState();
        assertEquals("origin-cape", s.equip("cape", "origin-cape"));
        assertEquals("origin-cape", s.equip("cape", "regent-cape"), "refused: still wearing the free cape");
        assertFalse(s.owns("blade-wings"));
        assertTrue(s.owns("void-badge-player"));
    }

    @Test
    @DisplayName("with a launcher a paid item applies at once and is forwarded; a granted one never does")
    void optimistic() {
        CosmeticState s = new CosmeticState();
        RecordingSink sink = new RecordingSink();
        s.setSink(sink);
        assertEquals("blade-wings", s.equip("back", "blade-wings"));
        assertEquals(Arrays.asList("back=blade-wings"), sink.equips);
        assertNull(s.equip("badge", "void-badge-founder"), "founder is grant-only");
        assertEquals("blade-wings", s.equip("back", "regent-cape"), "wrong slot: refused, still wearing the wings");
        assertTrue(s.ownedIds().contains("blade-wings"), "what we wear is reported as owned");
        assertFalse(s.ownedIds().contains("vesper-sheath"));
    }

    @Test
    @DisplayName("a launcher self set replaces the local one (how a refused equip is reverted)")
    void revert() {
        CosmeticState s = new CosmeticState();
        s.setSink(new RecordingSink());
        s.setSelfUuid("069a79f444e94726a5befca90e38aaf5");
        s.equip("cape", "regent-cape");
        s.applyLauncher(EquippedSet.empty(""), null, null);
        assertNull(s.self().get("cape"));
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", s.self().uuid, "keeps the session uuid");
    }

    @Test
    @DisplayName("dev ownership owns everything")
    void dev() {
        CosmeticState s = new CosmeticState();
        s.setOwnsEverything(true);
        assertEquals("void-badge-founder", s.equip("badge", "void-badge-founder"));
    }
}
