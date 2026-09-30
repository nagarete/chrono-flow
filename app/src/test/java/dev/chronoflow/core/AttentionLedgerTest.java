package dev.chronoflow.core;

import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class AttentionLedgerTest {
    @Test public void checkMovesOnlyObservedVersionsToEarlier() {
        AttentionLedger ledger = new AttentionLedger();
        AttentionLedger.Record visible = ledger.post("visible", "a", 10, false);
        ledger.post("offscreen", "b", 20, false);
        ledger.check(Map.of("visible", visible.revision));
        assertFalse(ledger.get("visible").isNew());
        assertTrue(ledger.get("offscreen").isNew());
    }
    @Test public void arrivalDuringCheckStaysNew() {
        AttentionLedger ledger = new AttentionLedger();
        AttentionLedger.Record before = ledger.post("chat", "message-one", 10, false);
        ledger.post("chat", "message-two", 20, false);
        ledger.check(Map.of("chat", before.revision));
        assertTrue(ledger.get("chat").isNew());
        assertEquals(20, ledger.get("chat").receivedAt);
    }
    @Test public void identicalRepostDoesNotResurfaceOrReorder() {
        AttentionLedger ledger = new AttentionLedger();
        AttentionLedger.Record before = ledger.post("chat", "same", 10, false);
        ledger.check(Map.of("chat", before.revision));
        ledger.post("chat", "same", 50, false);
        assertFalse(ledger.get("chat").isNew());
        assertEquals(10, ledger.get("chat").receivedAt);
    }
    @Test public void ongoingProgressDoesNotResurface() {
        AttentionLedger ledger = new AttentionLedger();
        AttentionLedger.Record before = ledger.post("download", "10 percent", 10, true);
        ledger.check(Map.of("download", before.revision));
        ledger.post("download", "20 percent", 20, true);
        assertFalse(ledger.get("download").isNew());
    }
    @Test public void persistedAttentionSurvivesRestartAndNewRevisionIsUnique() {
        AttentionLedger first = new AttentionLedger();
        AttentionLedger.Record record = first.post("chat", "old", 10, false);
        first.check(Map.of("chat", record.revision));
        AttentionLedger restored = new AttentionLedger();
        restored.restore(first.snapshot());
        assertFalse(restored.post("chat", "old", 10, false).isNew());
        assertTrue(restored.post("chat", "new", 20, false).revision > record.revision);
    }
    @Test public void reconciliationDropsRemovedRecordsAndPayloadsAreNotNeeded() {
        AttentionLedger ledger = new AttentionLedger();
        ledger.post("a", "hash-a", 10, false); ledger.post("b", "hash-b", 20, false);
        ledger.retain(List.of("b"));
        assertNull(ledger.get("a")); assertEquals(1, ledger.snapshot().size());
        ledger.remove("b"); assertTrue(ledger.snapshot().isEmpty());
    }
    @Test public void chronologicalOrderIsStableForSimultaneousArrivals() {
        AttentionLedger ledger = new AttentionLedger();
        ledger.post("a", "a", 10, false); ledger.post("b", "b", 10, false);
        ledger.post("c", "c", 5, false);
        assertEquals(List.of("b", "a", "c"), ledger.snapshot().stream().map(r -> r.key).toList());
    }
    @Test public void snapshotsCannotMutateLedgerAndRemovedKeysStartFresh() {
        AttentionLedger ledger = new AttentionLedger();
        AttentionLedger.Record first = ledger.post("a", "a", 10, false);
        ledger.check(Map.of("a", first.revision));
        ledger.snapshot().get(0).seenRevision = -1;
        assertFalse(ledger.get("a").isNew());
        ledger.remove("a"); assertTrue(ledger.post("a", "a", 20, false).isNew());
    }
}
