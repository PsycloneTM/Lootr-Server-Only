package net.lootr.serveronly.interaction;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewerRegistryTest {
    private static final Predicate<UUID> EVERYONE_ONLINE = id -> true;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void nobodyViewingIsAnAnswerWithoutAnyPlayerLookup() {
        ViewerRegistry<String> r = new ViewerRegistry<>();
        assertEquals(0, r.count("chest"));
        assertTrue(r.viewersOf("chest").isEmpty());
        assertTrue(r.isEmpty());
    }

    @Test
    void addReportsBeforeAndAfterCounts() {
        ViewerRegistry<String> r = new ViewerRegistry<>();
        assertEquals(new ViewerRegistry.Change(0, 1), r.add("chest", alice, EVERYONE_ONLINE));
        assertEquals(new ViewerRegistry.Change(1, 2), r.add("chest", bob, EVERYONE_ONLINE));
        assertEquals(2, r.count("chest"));
    }

    @Test
    void addingTheSameViewerTwiceDoesNotDoubleCount() {
        ViewerRegistry<String> r = new ViewerRegistry<>();
        r.add("chest", alice, EVERYONE_ONLINE);
        assertEquals(new ViewerRegistry.Change(1, 1), r.add("chest", alice, EVERYONE_ONLINE));
    }

    @Test
    void removeReportsChangeAndDropsEmptyKeys() {
        ViewerRegistry<String> r = new ViewerRegistry<>();
        r.add("chest", alice, EVERYONE_ONLINE);
        r.add("chest", bob, EVERYONE_ONLINE);
        assertEquals(new ViewerRegistry.Change(2, 1), r.remove("chest", alice, EVERYONE_ONLINE));
        assertEquals(new ViewerRegistry.Change(1, 0), r.remove("chest", bob, EVERYONE_ONLINE));
        assertTrue(r.isEmpty());
    }

    @Test
    void removingFromAnUnknownKeyReturnsNull() {
        ViewerRegistry<String> r = new ViewerRegistry<>();
        assertNull(r.remove("chest", alice, EVERYONE_ONLINE));
    }

    @Test
    void offlineViewersAreDroppedOnAdd() {
        ViewerRegistry<String> r = new ViewerRegistry<>();
        r.add("chest", alice, EVERYONE_ONLINE);
        ViewerRegistry.Change change = r.add("chest", bob, id -> id.equals(bob));
        assertEquals(1, change.before());
        assertEquals(1, change.after());
        assertEquals(Set.of(bob), r.viewersOf("chest"));
    }

    @Test
    void offlineViewersAreDroppedOnRemoveAndEmptyKeyIsCleared() {
        ViewerRegistry<String> r = new ViewerRegistry<>();
        r.add("chest", alice, EVERYONE_ONLINE);
        r.add("chest", bob, EVERYONE_ONLINE);
        r.remove("chest", alice, id -> false);
        assertEquals(0, r.count("chest"));
        assertTrue(r.isEmpty());
    }

    @Test
    void forgetRemovesThePlayerEverywhereAndReportsOnlyChangedKeys() {
        ViewerRegistry<String> r = new ViewerRegistry<>();
        r.add("a", alice, EVERYONE_ONLINE);
        r.add("a", bob, EVERYONE_ONLINE);
        r.add("b", alice, EVERYONE_ONLINE);
        r.add("c", bob, EVERYONE_ONLINE);
        Map<String, ViewerRegistry.Change> changed = r.forget(alice);
        assertEquals(2, changed.size());
        assertEquals(new ViewerRegistry.Change(2, 1), changed.get("a"));
        assertEquals(new ViewerRegistry.Change(1, 0), changed.get("b"));
        assertEquals(1, r.count("a"));
        assertEquals(0, r.count("b"));
        assertEquals(1, r.count("c"));
    }

    @Test
    void viewersOfReturnsACopy() {
        ViewerRegistry<String> r = new ViewerRegistry<>();
        r.add("chest", alice, EVERYONE_ONLINE);
        Set<UUID> copy = r.viewersOf("chest");
        copy.clear();
        assertFalse(r.viewersOf("chest").isEmpty());
    }

    @Test
    void keysAreIndependent() {
        ViewerRegistry<UUID> r = new ViewerRegistry<>();
        UUID cartA = UUID.randomUUID();
        UUID cartB = UUID.randomUUID();
        r.add(cartA, alice, EVERYONE_ONLINE);
        assertEquals(1, r.count(cartA));
        assertEquals(0, r.count(cartB));
    }
}
