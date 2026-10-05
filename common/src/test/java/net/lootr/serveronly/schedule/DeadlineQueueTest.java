package net.lootr.serveronly.schedule;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadlineQueueTest {
    @Test
    void emptyQueueHasNoDeadline() {
        DeadlineQueue<String> q = new DeadlineQueue<>();
        assertTrue(q.isEmpty());
        assertEquals(LootSchedule.NO_DEADLINE, q.peekDue());
        assertTrue(q.pollDue(Long.MAX_VALUE - 1).isEmpty());
    }

    @Test
    void pollDueReturnsOnlyDueKeysEarliestFirstAndRemovesThem() {
        DeadlineQueue<String> q = new DeadlineQueue<>();
        q.put("c", 30);
        q.put("a", 10);
        q.put("b", 20);
        assertEquals(10L, q.peekDue());
        assertEquals(List.of("a", "b"), q.pollDue(20));
        assertEquals(1, q.size());
        assertEquals(30L, q.peekDue());
        assertFalse(q.contains("a"));
    }

    @Test
    void putReplacesTheDeadlineRatherThanDuplicating() {
        DeadlineQueue<String> q = new DeadlineQueue<>();
        q.put("a", 10);
        q.put("a", 50);
        assertEquals(1, q.size());
        assertEquals(50L, q.peekDue());
        assertTrue(q.pollDue(49).isEmpty());
        assertEquals(List.of("a"), q.pollDue(50));
    }

    @Test
    void putWithTheSameDeadlineIsANoOp() {
        DeadlineQueue<String> q = new DeadlineQueue<>();
        q.put("a", 10);
        q.put("a", 10);
        assertEquals(1, q.size());
        assertEquals(List.of("a"), q.pollDue(10));
    }

    @Test
    void removeDropsTheKeyAndItsDeadline() {
        DeadlineQueue<String> q = new DeadlineQueue<>();
        q.put("a", 10);
        q.put("b", 10);
        assertTrue(q.remove("a"));
        assertFalse(q.remove("a"));
        assertEquals(List.of("b"), q.pollDue(10));
        assertTrue(q.isEmpty());
        assertEquals(LootSchedule.NO_DEADLINE, q.peekDue());
    }

    @Test
    void keysSharingADeadlineAreAllReturned() {
        DeadlineQueue<Integer> q = new DeadlineQueue<>();
        for (int i = 0; i < 5; i++) {
            q.put(i, 7);
        }
        assertEquals(5, q.pollDue(7).size());
        assertTrue(q.isEmpty());
    }

    @Test
    void clearEmptiesTheQueue() {
        DeadlineQueue<String> q = new DeadlineQueue<>();
        q.put("a", 1);
        q.put("b", 2);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(LootSchedule.NO_DEADLINE, q.peekDue());
    }

    @Test
    void matchesANaiveModelUnderRandomOperations() {
        Random rng = new Random(12345);
        DeadlineQueue<Integer> q = new DeadlineQueue<>();
        Map<Integer, Long> model = new HashMap<>();
        long now = 0;
        for (int step = 0; step < 20000; step++) {
            int op = rng.nextInt(10);
            int key = rng.nextInt(200);
            if (op < 5) {
                long due = now + rng.nextInt(500);
                q.put(key, due);
                model.put(key, due);
            } else if (op < 7) {
                assertEquals(model.remove(key) != null, q.remove(key));
            } else {
                now += rng.nextInt(40);
                final long cutoff = now;
                TreeSet<Integer> expected = new TreeSet<>();
                model.entrySet().removeIf(e -> {
                    if (e.getValue() <= cutoff) {
                        expected.add(e.getKey());
                        return true;
                    }
                    return false;
                });
                List<Integer> polled = q.pollDue(now);
                assertEquals(expected, new TreeSet<>(polled));
            }
            assertEquals(model.size(), q.size());
            long min = model.values().stream().mapToLong(Long::longValue).min().orElse(LootSchedule.NO_DEADLINE);
            assertEquals(min, q.peekDue());
        }
    }
}
