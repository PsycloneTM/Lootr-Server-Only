package net.lootr.serveronly.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LootListeners {
    private static final Logger LOG = LoggerFactory.getLogger("lootr_serveronly");
    private static final List<LootListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static volatile boolean servicesLoaded = false;

    public static void register(LootListener listener) {
        LISTENERS.add(listener);
        LISTENERS.sort(Comparator.comparingInt(LootListener::priority));
    }

    public static void unregister(LootListener listener) {
        LISTENERS.remove(listener);
    }

    public static void looted(ServerLevel level, Object holder, BlockPos pos, ServerPlayer looter,
                              @Nullable ResourceKey<LootTable> table) {
        loadServices();
        if (LISTENERS.isEmpty() || table == null) {
            return;
        }
        for (LootListener listener : LISTENERS) {
            try {
                listener.onLooted(level, holder, pos, looter, table);
            } catch (RuntimeException e) {
                logOnce(listener, e);
            }
        }
    }

    public static void decaying(ServerLevel level, Object holder, BlockPos pos, @Nullable ResourceKey<LootTable> table) {
        loadServices();
        if (LISTENERS.isEmpty() || table == null) {
            return;
        }
        for (LootListener listener : LISTENERS) {
            try {
                listener.onDecaying(level, holder, pos, table);
            } catch (RuntimeException e) {
                logOnce(listener, e);
            }
        }
    }

    public static void refreshed(ServerLevel level, Object holder, BlockPos pos, @Nullable ResourceKey<LootTable> table) {
        loadServices();
        if (LISTENERS.isEmpty() || table == null) {
            return;
        }
        for (LootListener listener : LISTENERS) {
            try {
                listener.onRefreshed(level, holder, pos, table);
            } catch (RuntimeException e) {
                logOnce(listener, e);
            }
        }
    }

    private static void logOnce(LootListener listener, RuntimeException e) {
        if (FAILED.add(listener.name())) {
            LOG.error("Loot listener '{}' threw an exception and was skipped for this event "
                    + "(further failures from it are not logged).", listener.name(), e);
        }
    }

    private static void loadServices() {
        if (servicesLoaded) {
            return;
        }
        synchronized (LootListeners.class) {
            if (servicesLoaded) {
                return;
            }
            servicesLoaded = true;
            try {
                for (LootListener found : ServiceLoader.load(LootListener.class, LootListener.class.getClassLoader())) {
                    register(found);
                }
            } catch (ServiceConfigurationError | RuntimeException e) {
                LOG.error("Could not load loot listeners from META-INF/services; continuing without them.", e);
            }
        }
    }

    private LootListeners() {}
}
