package net.lootr.serveronly.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;

public final class RegionQueue {
    private final Map<String, Map<Long, Long>> dueByRegion = new HashMap<>();
    private final Map<String, TreeMap<Long, Set<Long>>> regionsByDue = new HashMap<>();

    public Set<String> dimensions() {
        return new HashSet<>(dueByRegion.keySet());
    }

    public Map<Long, Long> entries(String dimension) {
        Map<Long, Long> map = dueByRegion.get(dimension);
        return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
    }

    public boolean contains(String dimension, long region) {
        Map<Long, Long> map = dueByRegion.get(dimension);
        return map != null && map.containsKey(region);
    }

    public Long get(String dimension, long region) {
        Map<Long, Long> map = dueByRegion.get(dimension);
        return map == null ? null : map.get(region);
    }

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
