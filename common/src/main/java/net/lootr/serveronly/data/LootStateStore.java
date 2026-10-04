package net.lootr.serveronly.data;

import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

public final class LootStateStore {
    public interface Backend {
        boolean has(Object holder);

        @Nullable
        LootrLootState peek(Object holder, Level level);

        LootrLootState getOrCreate(Object holder, Level level);

        long firstGenerated(Object holder);

        void save(Object holder, LootrLootState state, Level level);
    }

    private static volatile Backend backend;
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    public static void install(Backend installed) {
        backend = installed;
    }

    private static Backend backend() {
        Backend current = backend;
        if (current == null) {
            if (WARNED.compareAndSet(false, true)) {
                net.lootr.serveronly.common.LootrServerOnlyConstants.LOGGER.error(
                        "LootStateStore used before a backend was installed; this is a wiring bug in the mod.");
            }
            throw new IllegalStateException("LootStateStore used before a backend was installed");
        }
        return current;
    }

    public static boolean has(Object holder) {
        return backend().has(holder);
    }

    @Nullable
    public static LootrLootState peek(Object holder, Level level) {
        return backend().peek(holder, level);
    }

    public static LootrLootState getOrCreate(Object holder, Level level) {
        return backend().getOrCreate(holder, level);
    }

    public static long firstGenerated(Object holder) {
        return backend().firstGenerated(holder);
    }

    public static void save(Object holder, LootrLootState state, Level level) {
        backend().save(holder, state, level);
    }

    private LootStateStore() {}
}
