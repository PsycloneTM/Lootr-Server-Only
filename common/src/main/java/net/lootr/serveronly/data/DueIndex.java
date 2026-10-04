package net.lootr.serveronly.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongUnaryOperator;

/**
 * The in-memory half of a tracker shard: tracked block positions, each with a "check me at this game time"
 * hint, ordered by that time. Deliberately free of any Minecraft types so it can be unit tested and
 * benchmarked on its own.
 * <p>
 * The hint is <b>not</b> the authority on when something decays or refreshes. The container's own
 * {@code firstGeneratedGameTime} still is, and the sweep re-derives the real deadline from it every time it
 * looks. The hint only says "there is no point looking before this". A hint that is too early costs one
 * wasted look; the sweep corrects it by rescheduling. Hints are never allowed to be too late: see
 * {@link #wakeAll} and {@link #wakeChunk}.
 * <p>
 * Costs: {@link #takeDue} is O(k log n) for k due entries (and O(1) when nothing is due), where the old
 * "copy the whole set and look at everything" sweep was O(n) every time.
 */
public final class DueIndex {

    private final LongUnaryOperator chunkOf;
    private final Map<Long, Long> dueOf = new HashMap<>();
    private final TreeMap<Long, Set<Long>> byDue = new TreeMap<>();
    private final Map<Long, Set<Long>> byChunk = new HashMap<>();

    /** @param chunkOf maps a packed position to a key identifying the chunk it is in */
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

    /** Earliest hint, or {@link Long#MAX_VALUE} if empty. */
    public long minDue() {
        return byDue.isEmpty() ? Long.MAX_VALUE : byDue.firstKey();
    }

    /** Adds a position if it is not tracked yet. An existing entry is left exactly as it is. */
    public boolean add(long pos, long due) {
        if (dueOf.containsKey(pos)) {
            return false;
        }
        put(pos, due);
        return true;
    }

    /** Adds or reschedules. */
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

    /**
     * Removes and returns every entry whose hint is at or before {@code now}. The caller decides, per
     * entry, whether to {@link #put} it back with a new hint or let it go.
     */
    public List<Long> takeDue(long now) {
        List<Long> out = new ArrayList<>();
        while (!byDue.isEmpty() && byDue.firstKey() <= now) {
            // copy: remove() edits the set we would be iterating
            for (long pos : new ArrayList<>(byDue.firstEntry().getValue())) {
                out.add(pos);
                remove(pos);
            }
        }
        return out;
    }

    /** Makes every entry in one chunk due at {@code now} (that chunk just loaded). Returns how many changed. */
    public int wakeChunk(long chunkKey, long now) {
        Set<Long> inChunk = byChunk.get(chunkKey);
        if (inChunk == null) {
            return 0;
        }
        int changed = 0;
        for (long pos : new ArrayList<>(inChunk)) {
            if (dueOf.get(pos) > now) {
                put(pos, now);
                changed++;
            }
        }
        return changed;
    }

    /** Makes every entry due at {@code now} (a setting that the hints were computed from changed). */
    public void wakeAll(long now) {
        for (long pos : new ArrayList<>(dueOf.keySet())) {
            if (dueOf.get(pos) > now) {
                put(pos, now);
            }
        }
    }

    /** Parallel arrays for saving: positions[i] has hint dues[i]. */
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
