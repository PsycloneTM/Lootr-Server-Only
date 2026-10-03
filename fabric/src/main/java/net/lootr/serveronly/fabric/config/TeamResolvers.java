package net.lootr.serveronly.fabric.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pluggable team resolvers, the server-only counterpart of upstream's {@code ITeamResolver} machinery. Other
 * server mods supply a resolver so shared loot follows THEIR teams or parties. Resolvers come from three places,
 * all treated alike:
 * <ul>
 *     <li>the built-in scoreboard resolver, id {@link #VANILLA_DEFAULT}, priority -1000 (as upstream, so any
 *     add-on at the default priority 0 wins automatically);</li>
 *     <li>resolvers found with {@link ServiceLoader} (a {@code META-INF/services} file naming
 *     {@link ITeamResolver}), loaded once;</li>
 *     <li>resolvers added in code with {@link #registerResolver} (or the older {@link #register}).</li>
 * </ul>
 * Selection, as upstream: the resolver whose id is {@code pinned_team_resolver}; if that is blank or not found, the
 * highest priority (ties keep discovery order). The built-in is always present, so there is always an answer.
 * Only consulted when {@code team_loot} is on. Two resolvers with the same id: the higher-priority one wins and the
 * other is logged and ignored (upstream lets the later one silently replace the earlier).
 * <p>
 * A resolver that throws, or returns null, from {@code init} / {@code resolverId} / {@code resolveServerPlayer}
 * can never break loot: it is ignored (init, id) or the player falls back to their own UUID (resolve), and the
 * problem is logged once. Not verified by a build.
 */
public final class TeamResolvers {
    private static final Logger LOG = LoggerFactory.getLogger("lootr_serveronly");

    /** Id of the built-in scoreboard resolver; upstream's id for its own built-in, so a pinned value copies across. */
    public static final ResourceLocation VANILLA_DEFAULT = ResourceLocation.withDefaultNamespace("vanilla_default");
    /** This mod's earlier id for the built-in. Still accepted in {@code pinned_team_resolver} as an alias. */
    public static final ResourceLocation SCOREBOARD = ResourceLocation.fromNamespaceAndPath("lootr_serveronly", "scoreboard");
    /** What upstream's blank config value stands for. Nobody registers it; pinning it is not an error. */
    private static final ResourceLocation UPSTREAM_BLANK_DEFAULT = ResourceLocation.fromNamespaceAndPath("lootr", "default");
    private static final int BUILT_IN_PRIORITY = -1000;

    /** The simple form kept for existing callers; see {@link #register}. New code should use {@link ITeamResolver}. */
    public interface Resolver {
        ResourceLocation id();

        default int priority() {
            return 0;
        }

        /** The UUID this player's loot is keyed under; players that should share loot return the same UUID. */
        UUID resolve(Player player);
    }

    private record Entry(ITeamResolver resolver, ResourceLocation id, int priority) {}

    private record Snapshot(List<Entry> sorted, Map<ResourceLocation, ITeamResolver> byId) {}

    private static final ITeamResolver BUILT_IN = new ITeamResolver() {
        @Override
        public UUID resolveServerPlayer(Player player) {
            Team team = player.getTeam();
            return team == null ? player.getUUID()
                    : UUID.nameUUIDFromBytes(("lootr_serveronly:team:" + team.getName()).getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public ResourceLocation resolverId() {
            return VANILLA_DEFAULT;
        }

        @Override
        public int priority() {
            return BUILT_IN_PRIORITY;
        }
    };

    private static final List<ITeamResolver> REGISTERED = new CopyOnWriteArrayList<>();
    /** Whether each resolver's init succeeded. Only touched with the class lock held. */
    private static final Map<ITeamResolver, Boolean> INIT_RESULT = new IdentityHashMap<>();
    private static final Set<Object> WARNED = ConcurrentHashMap.newKeySet();
    private static List<ITeamResolver> discovered = null;
    private static volatile Snapshot snapshot = null;

    /** Registers a resolver in code, replacing any earlier code-registered one with the same id. Safe at any time. */
    public static void registerResolver(ITeamResolver resolver) {
        Objects.requireNonNull(resolver, "resolver");
        synchronized (TeamResolvers.class) {
            ResourceLocation id = idOf(resolver);
            if (id != null) {
                REGISTERED.removeIf(existing -> id.equals(idOf(existing)));
            }
            REGISTERED.add(resolver);
            snapshot = null;
        }
    }

    /** Registers the older simple form (id, priority, resolve) through an adapter. */
    public static void register(Resolver resolver) {
        Objects.requireNonNull(resolver, "resolver");
        registerResolver(new ITeamResolver() {
            @Override
            public UUID resolveServerPlayer(Player player) {
                return resolver.resolve(player);
            }

            @Override
            public ResourceLocation resolverId() {
                return resolver.id();
            }

            @Override
            public int priority() {
                return resolver.priority();
            }
        });
    }

    /** The resolver in use right now: the pinned one if it exists, otherwise the highest priority. */
    public static ITeamResolver active() {
        Snapshot snap = snapshot();
        String raw = LootrConfig.pinnedTeamResolver();
        if (raw != null && !raw.isBlank()) {
            ResourceLocation pinned = ResourceLocation.tryParse(raw.trim());
            ITeamResolver found = null;
            if (pinned != null) {
                found = snap.byId().get(pinned);
                if (found == null && pinned.equals(SCOREBOARD)) {
                    found = snap.byId().get(VANILLA_DEFAULT);
                }
            }
            if (found != null) {
                return found;
            }
            if (pinned == null || !pinned.equals(UPSTREAM_BLANK_DEFAULT)) {
                warnOnce("pin:" + raw, "pinned_team_resolver '{}' is not registered; using the highest-priority resolver instead.", raw);
            }
        }
        return snap.sorted().get(0).resolver();
    }

    /** The loot key for this player under the active resolver; never throws and never returns null. */
    public static UUID resolveFor(Player player) {
        ITeamResolver resolver = active();
        try {
            UUID id = resolver.resolveServerPlayer(player);
            if (id != null) {
                return id;
            }
            warnOnce("null:" + idOf(resolver), "Team resolver '{}' returned null; using each player's own UUID for it.", idOf(resolver));
        } catch (RuntimeException e) {
            warnOnce("threw:" + idOf(resolver), "Team resolver '" + idOf(resolver) + "' threw; using each player's own UUID for it.", e);
        }
        return player.getUUID();
    }

    private static Snapshot snapshot() {
        Snapshot s = snapshot;
        if (s == null) {
            synchronized (TeamResolvers.class) {
                s = snapshot;
                if (s == null) {
                    s = snapshot = buildSnapshot();
                }
            }
        }
        return s;
    }

    /** Called with the class lock held. */
    private static Snapshot buildSnapshot() {
        List<ITeamResolver> all = new ArrayList<>();
        all.add(BUILT_IN);
        all.addAll(discovered());
        all.addAll(REGISTERED);

        List<Entry> entries = new ArrayList<>();
        for (ITeamResolver resolver : all) {
            Entry entry = entryFor(resolver);
            if (entry != null) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.comparingInt(Entry::priority).reversed()); // stable: ties keep discovery order

        Map<ResourceLocation, ITeamResolver> byId = new HashMap<>();
        for (Entry entry : entries) {
            if (byId.putIfAbsent(entry.id(), entry.resolver()) != null) {
                LOG.warn("Team resolver id '{}' is used twice; ignoring the lower-priority one ({}).",
                        entry.id(), entry.resolver().getClass().getName());
            }
        }
        return new Snapshot(List.copyOf(entries), Map.copyOf(byId));
    }

    /** Null if this resolver cannot be used (its id or init threw, or the id is null). */
    private static Entry entryFor(ITeamResolver resolver) {
        ResourceLocation id = idOf(resolver);
        if (id == null) {
            return null;
        }
        if (resolver != BUILT_IN) {
            Boolean ok = INIT_RESULT.get(resolver);
            if (ok == null) {
                try {
                    resolver.init();
                    ok = Boolean.TRUE;
                } catch (RuntimeException e) {
                    LOG.error("Team resolver '{}' threw from init(); ignoring it", id, e);
                    ok = Boolean.FALSE;
                }
                INIT_RESULT.put(resolver, ok);
            }
            if (!ok) {
                return null;
            }
        }
        int priority = 0;
        try {
            priority = resolver.priority();
        } catch (RuntimeException e) {
            LOG.error("Team resolver '{}' threw from priority(); using 0", id, e);
        }
        return new Entry(resolver, id, priority);
    }

    /** The resolver's id, or null (logged once) if it threw or returned null. */
    private static ResourceLocation idOf(ITeamResolver resolver) {
        try {
            ResourceLocation id = resolver.resolverId();
            if (id == null) {
                warnOnce("noid:" + System.identityHashCode(resolver), "A team resolver ({}) returned a null resolverId(); ignoring it.",
                        resolver.getClass().getName());
            }
            return id;
        } catch (RuntimeException e) {
            warnOnce("idthrew:" + System.identityHashCode(resolver), "A team resolver (" + resolver.getClass().getName()
                    + ") threw from resolverId(); ignoring it.", e);
            return null;
        }
    }

    /** Resolvers named in {@code META-INF/services}, loaded once. Called with the class lock held. */
    private static List<ITeamResolver> discovered() {
        if (discovered == null) {
            List<ITeamResolver> found = new ArrayList<>();
            Iterator<ITeamResolver> iterator = ServiceLoader.load(ITeamResolver.class, ITeamResolver.class.getClassLoader()).iterator();
            // A provider that fails to load must not hide the ones after it. The cap guards against a
            // misbehaving loader that keeps throwing without advancing.
            for (int attempts = 0; attempts < 1000; attempts++) {
                try {
                    if (!iterator.hasNext()) {
                        break;
                    }
                    found.add(iterator.next());
                } catch (ServiceConfigurationError | RuntimeException e) {
                    LOG.error("A team resolver failed to load; skipping it", e);
                }
            }
            discovered = List.copyOf(found);
        }
        return discovered;
    }

    private static void warnOnce(Object key, String message, Object... args) {
        if (WARNED.add(key)) {
            LOG.error(message, args);
        }
    }

    private TeamResolvers() {}
}
