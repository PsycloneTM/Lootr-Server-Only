package net.lootr.serveronly.schedule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class DeadlineQueue<K> {
    private final Map<K, Long> dueOf = new HashMap<>();
    private final TreeMap<Long, Set<K>> byDue = new TreeMap<>();

    public int size() {
        return dueOf.size();
    }

    public boolean isEmpty() {
        return dueOf.isEmpty();
    }

    public boolean contains(K key) {
        return dueOf.containsKey(key);
    }

    public long peekDue() {
        return byDue.isEmpty() ? LootSchedule.NO_DEADLINE : byDue.firstKey();
    }

    public void put(K key, long due) {
        Long old = dueOf.put(key, due);
        if (old != null) {
            if (old == due) {
                return;
            }
            detach(key, old);
        }
        byDue.computeIfAbsent(due, d -> new LinkedHashSet<>()).add(key);
    }

    public boolean remove(K key) {
        Long old = dueOf.remove(key);
        if (old == null) {
            return false;
        }
        detach(key, old);
        return true;
    }

    public void clear() {
        dueOf.clear();
        byDue.clear();
    }

    public List<K> pollDue(long now) {
        List<K> out = new ArrayList<>();
        while (!byDue.isEmpty() && byDue.firstKey() <= now) {
            Map.Entry<Long, Set<K>> first = byDue.pollFirstEntry();
            for (K key : first.getValue()) {
                dueOf.remove(key);
                out.add(key);
            }
        }
        return out;
    }

    private void detach(K key, long due) {
        Set<K> keys = byDue.get(due);
        if (keys == null) {
            return;
        }
        keys.remove(key);
        if (keys.isEmpty()) {
            byDue.remove(due);
        }
    }
}
