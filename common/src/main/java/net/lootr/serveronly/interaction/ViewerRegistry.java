package net.lootr.serveronly.interaction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class ViewerRegistry<K> {
    public record Change(int before, int after) {}

    private final Map<K, Set<UUID>> viewers = new HashMap<>();

    public Change add(K key, UUID id, Predicate<UUID> online) {
        Set<UUID> set = viewers.computeIfAbsent(key, k -> new HashSet<>());
        int before = set.size();
        set.removeIf(other -> !online.test(other));
        set.add(id);
        return new Change(before, set.size());
    }

    public Change remove(K key, UUID id, Predicate<UUID> online) {
        Set<UUID> set = viewers.get(key);
        if (set == null) {
            return null;
        }
        int before = set.size();
        set.remove(id);
        set.removeIf(other -> !online.test(other));
        if (set.isEmpty()) {
            viewers.remove(key);
        }
        return new Change(before, set.size());
    }

    public int count(K key) {
        Set<UUID> set = viewers.get(key);
        return set == null ? 0 : set.size();
    }

    public Set<UUID> viewersOf(K key) {
        Set<UUID> set = viewers.get(key);
        return set == null ? Set.of() : new HashSet<>(set);
    }

    public Map<K, Change> forget(UUID id) {
        Map<K, Change> changed = new HashMap<>();
        for (Map.Entry<K, Set<UUID>> e : new HashMap<>(viewers).entrySet()) {
            Set<UUID> set = e.getValue();
            if (!set.contains(id)) {
                continue;
            }
            int before = set.size();
            set.remove(id);
            if (set.isEmpty()) {
                viewers.remove(e.getKey());
            }
            changed.put(e.getKey(), new Change(before, set.size()));
        }
        return changed;
    }

    public boolean isEmpty() {
        return viewers.isEmpty();
    }
}
