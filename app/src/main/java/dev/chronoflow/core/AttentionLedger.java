package dev.chronoflow.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Android-free attention model. Keys and signatures must be hashes, never notification text. */
public final class AttentionLedger {
    public static final class Record {
        public final String key;
        public String signature;
        public long receivedAt;
        public long revision;
        public long seenRevision;

        public Record(String key, String signature, long receivedAt, long revision, long seenRevision) {
            this.key = key;
            this.signature = signature;
            this.receivedAt = receivedAt;
            this.revision = revision;
            this.seenRevision = seenRevision;
        }

        public boolean isNew() { return revision != seenRevision; }
        public Record copy() { return new Record(key, signature, receivedAt, revision, seenRevision); }
    }

    private final Map<String, Record> records = new HashMap<>();
    private long sequence;

    public void restore(Collection<Record> saved) {
        records.clear();
        sequence = 0;
        for (Record r : saved) {
            records.put(r.key, r.copy());
            sequence = Math.max(sequence, r.revision);
        }
    }

    public Record post(String key, String signature, long receivedAt, boolean ongoing) {
        Record r = records.get(key);
        if (r == null) {
            r = new Record(key, signature, receivedAt, ++sequence, 0);
            records.put(key, r);
        } else if (!r.signature.equals(signature)) {
            r.signature = signature;
            // Progress and media updates do not repeatedly demand attention.
            if (!ongoing) {
                r.receivedAt = receivedAt;
                r.revision = ++sequence;
            }
        }
        return r.copy();
    }

    /** Only exact versions actually observed may be marked seen; arrivals during a check stay NEW. */
    public void check(Map<String, Long> observed) {
        for (Map.Entry<String, Long> item : observed.entrySet()) {
            Record r = records.get(item.getKey());
            if (r != null && r.revision == item.getValue()) r.seenRevision = r.revision;
        }
    }

    public void remove(String key) { records.remove(key); }
    public void retain(Collection<String> activeKeys) { records.keySet().retainAll(activeKeys); }
    public Record get(String key) { Record r = records.get(key); return r == null ? null : r.copy(); }
    public ArrayList<Record> snapshot() {
        ArrayList<Record> out = new ArrayList<>();
        for (Record r : records.values()) out.add(r.copy());
        out.sort(Comparator.comparingLong((Record r) -> r.receivedAt).reversed()
                .thenComparing(Comparator.comparingLong((Record r) -> r.revision).reversed()));
        return out;
    }
}
