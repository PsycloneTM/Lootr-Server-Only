package net.lootr.serveronly.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;

/**
 * Per-dimension index of regions ordered by their earliest due time.
 *
 * <p>Each region maps to the minimum due time of the entries inside its shard. The index keeps that
 * value in two synchronised views: a lookup map (region to due time) and a sorted map (due time to the
 * regions due then). {@link #takeDue} therefore touches only the regions whose deadline has passed,
 * instead of scanning every region on each sweep.
 *
 * <p>This class has no Minecraft dependencies so it can be unit-tested directly. The persisted form in
 * {@link TrackerIndex} is unchanged: it is written from {@link #entries} and rebuilt with {@link #put}.
 */
public final class RegionQueue {
    private final Map<String, Map<Long, Long>> dueByRegion = new HashMap<>();
    private final Map<String, TreeMap<Long, Set<Long>>> regionsByDue = new HashMap<>();

    /** The dimensions that currently have at least one region. */
    public Set<String> dimensions() {
        return new HashSet<>(dueByRegion.keySet());
    }

    /** Region to due time for one dimension, in insertion-independent order. Empty if none. */
    public Map<Long, Long> entries(String dimension) {
        Map<Long, Long> map = dueByRegion.get(dimension);
        return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
    }

    public boolean contains(String dimension, long region) {
        Map<Long, Long> map = dueByRegion.get(dimension);
        return map != null && map.containsKey(region);
    }

    /** The stored due time for the region, or {@code null} if the region is not indexed. */
    public Long get(String dimension, long region) {
        Map<Long, Long> map = dueByRegion.get(dimension);
        return map == null ? null : map.get(region);
    }

    /**
     * Sets the region's due time, replacing any previous value. Returns the previous value, or
     * {@code null} if the region was not indexed before.
     */
    public Long put(String dimension, long region, long due) {
        Map<Long, Long> map = dueByRegion.computeIfAbsent(dimension, k -> new HashMap<>());
        TreeMap<Long, Set<Long>> ordered = regionsByDue.computeIfAbsent(dimension, k -> new TreeMap<>());
        Long old = map.put(region, due);
        if (old != null) {
            unlink(ordered, old, region);
        }
        ordered.computeIfAbsent(due, k -> new HashSet<>()).add(region);
        return old;
    }

    /** Removes the region. Returns {@code true} if it was indexed. */
    public boolean remove(String dimension, long region) {
        Map<Long, Long> map = dueByRegion.get(dimension);
        if (map == null) {
            return false;
        }
        Long old = map.remove(region);
        if (old == null) {
            return false;
        }
        unlink(regionsByDue.get(dimension), old, region);
        if (map.isEmpty()) {
            dueByRegion.remove(dimension);
            regionsByDue.remove(dimension);
        }
        return true;
    }

    /**
     * Removes and returns every region in the dimension whose due time is at or before {@code now}, in
     * due-time order. Callers re-insert a region with {@link #put} (or drop it with {@link #remove})
     * once they have processed it.
     */
    public List<Long> takeDue(String dimension, long now) {
        List<Long> out = new ArrayList<>();
        TreeMap<Long, Set<Long>> ordered = regionsByDue.get(dimension);
        if (ordered == null) {
            return out;
        }
        Map<Long, Long> map = dueByRegion.get(dimension);
        while (!ordered.isEmpty() && ordered.firstKey() <= now) {
            Map.Entry<Long, Set<Long>> first = ordered.pollFirstEntry();
            for (long region : first.getValue()) {
                map.remove(region);
                out.add(region);
            }
        }
        if (map.isEmpty()) {
            dueByRegion.remove(dimension);
            regionsByDue.remove(dimension);
        }
        return out;
    }

    /**
     * Pulls every region's due time {@code delta} ticks earlier, in memory only. Used when a duration
     * setting gets shorter. No shard is read or written here.
     */
    public void shiftEarlier(long delta) {
        if (delta <= 0) {
            return;
        }
        for (String dimension : new ArrayList<>(dueByRegion.keySet())) {
            Map<Long, Long> map = dueByRegion.get(dimension);
            TreeMap<Long, Set<Long>> ordered = regionsByDue.get(dimension);
            ordered.clear();
            for (Map.Entry<Long, Long> e : map.entrySet()) {
                e.setValue(net.lootr.serveronly.schedule.LootSchedule.shiftEarlier(e.getValue(), delta));
                ordered.computeIfAbsent(e.getValue(), k -> new HashSet<>()).add(e.getKey());
            }
        }
    }

    private static void unlink(TreeMap<Long, Set<Long>> ordered, long due, long region) {
        if (ordered == null) {
            return;
        }
        Set<Long> at = ordered.get(due);
        if (at != null) {
            at.remove(region);
            if (at.isEmpty()) {
                ordered.remove(due);
            }
        }
    }
}
