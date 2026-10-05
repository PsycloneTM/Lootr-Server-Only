package net.lootr.serveronly.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongUnaryOperator;

public final class DueIndex {
    public static final long PARKED = net.lootr.serveronly.schedule.LootSchedule.PARKED;

    private final LongUnaryOperator chunkOf;
    private final Map<Long, Long> dueOf = new HashMap<>();
    private final TreeMap<Long, Set<Long>> byDue = new TreeMap<>();
    private final Map<Long, Set<Long>> byChunk = new HashMap<>();

    public DueIndex(LongUnaryOperator chunkOf) {
        this.chunkOf = chunkOf;
    }

    public int size() {
        return dueOf.size();
    }

    public boolean isEmpty() {
        return dueOf.isEmpty();
    }

    public boolean contains(long pos) {
        return dueOf.containsKey(pos);
    }

    public long minDue() {
        return byDue.isEmpty() ? Long.MAX_VALUE : byDue.firstKey();
    }

    public boolean add(long pos, long due) {
        if (dueOf.containsKey(pos)) {
            return false;
        }
        put(pos, due);
        return true;
    }

    public void put(long pos, long due) {
        Long old = dueOf.put(pos, due);
        if (old != null) {
            if (old == due) {
                return;
            }
            unlinkDue(pos, old);
        } else {
            byChunk.computeIfAbsent(chunkOf.applyAsLong(pos), k -> new HashSet<>()).add(pos);
        }
        byDue.computeIfAbsent(due, k -> new HashSet<>()).add(pos);
    }

    public boolean remove(long pos) {
        Long old = dueOf.remove(pos);
        if (old == null) {
            return false;
        }
        unlinkDue(pos, old);
        long chunk = chunkOf.applyAsLong(pos);
        Set<Long> inChunk = byChunk.get(chunk);
        if (inChunk != null) {
            inChunk.remove(pos);
            if (inChunk.isEmpty()) {
                byChunk.remove(chunk);
            }
        }
        return true;
    }

    public List<Long> takeDue(long now) {
        List<Long> out = new ArrayList<>();
        while (!byDue.isEmpty() && byDue.firstKey() <= now) {
            for (long pos : new ArrayList<>(byDue.firstEntry().getValue())) {
                out.add(pos);
                remove(pos);
            }
        }
        return out;
    }

    public int wakeChunk(long chunkKey, long now) {
        Set<Long> inChunk = byChunk.get(chunkKey);
        if (inChunk == null) {
            return 0;
        }
        int changed = 0;
        for (long pos : new ArrayList<>(inChunk)) {
            if (dueOf.get(pos) == PARKED) {
                put(pos, now);
                changed++;
            }
        }
        return changed;
    }

    public void wakeAll(long now) {
        for (long pos : new ArrayList<>(dueOf.keySet())) {
            if (dueOf.get(pos) > now) {
                put(pos, now);
            }
        }
    }

    public void shiftEarlier(long delta) {
        if (delta <= 0) {
            return;
        }
        for (Map.Entry<Long, Long> e : dueOf.entrySet()) {
            e.setValue(net.lootr.serveronly.schedule.LootSchedule.shiftEarlier(e.getValue(), delta));
        }
        byDue.clear();
        for (Map.Entry<Long, Long> e : dueOf.entrySet()) {
            byDue.computeIfAbsent(e.getValue(), k -> new HashSet<>()).add(e.getKey());
        }
    }

    public long[][] snapshot() {
        long[] positions = new long[dueOf.size()];
        long[] dues = new long[positions.length];
        int i = 0;
        for (Map.Entry<Long, Long> e : dueOf.entrySet()) {
            positions[i] = e.getKey();
            dues[i++] = e.getValue();
        }
        return new long[][] {positions, dues};
    }

    private void unlinkDue(long pos, long due) {
        Set<Long> at = byDue.get(due);
        if (at != null) {
            at.remove(pos);
            if (at.isEmpty()) {
                byDue.remove(due);
            }
        }
    }
}
