package net.lootr.serveronly.fabric.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.lootr.serveronly.fabric.config.LootrConfig;
import net.lootr.serveronly.config.TeamResolver;
import net.lootr.serveronly.fabric.data.LootrLootState;
import net.lootr.serveronly.data.ReadOnlyLootView;
import net.lootr.serveronly.fabric.interaction.ChunkDiscovery;
import net.lootr.serveronly.fabric.interaction.ContainerInteractionHandler;
import net.lootr.serveronly.fabric.registry.ModAttachments;
import net.lootr.serveronly.fabric.registry.ModLootTags;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class AdminCommands {

    private static final int MAX_FORCE_RADIUS = 16;

    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        for (Spawn type : Spawn.values()) {
            root.then(Commands.literal(type.id)
                    .executes(ctx -> spawn(ctx.getSource(), type, null))
                    .then(Commands.argument("table", ResourceKeyArgument.key(Registries.LOOT_TABLE))
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                    tablesFor(ctx.getSource().getServer(), type).stream().map(ResourceKey::location),
                                    builder))
                            .executes(ctx -> spawn(ctx.getSource(), type, tableArgument(ctx)))));
        }

        root.then(Commands.literal("force_chunk")
                .executes(ctx -> forceChunk(ctx.getSource())));
        root.then(Commands.literal("force_radius")
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_FORCE_RADIUS))
                        .executes(ctx -> forceRadius(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))));
        root.then(Commands.literal("force_all")
                .executes(ctx -> forceAll(ctx.getSource())));

        var byName = Commands.argument("player", GameProfileArgument.gameProfile());
        withTargets(byName, AdminCommands::profileKey);
        root.then(Commands.literal("open_as").then(byName));

        var byUuid = Commands.argument("uuid", UuidArgument.uuid());
        withTargets(byUuid, ctx -> {
            UUID id = UuidArgument.getUuid(ctx, "uuid");
            return new Key(id, id.toString(), false);
        });
        root.then(Commands.literal("open_as_uuid").then(byUuid));
    }

    private enum Spawn {
        CHEST("chest", "chest", Blocks.CHEST, "chests/"),
        TRAPPED_CHEST("trapped_chest", "trapped chest", Blocks.TRAPPED_CHEST, "chests/"),
        BARREL("barrel", "barrel", Blocks.BARREL, "chests/"),
        SHULKER("shulker", "shulker box", Blocks.SHULKER_BOX, "chests/"),
        POT("pot", "decorated pot", Blocks.DECORATED_POT, "chests/", "pots/"),
        GRAVEL("gravel", "suspicious gravel", Blocks.SUSPICIOUS_GRAVEL, "archaeology/"),
        SAND("sand", "suspicious sand", Blocks.SUSPICIOUS_SAND, "archaeology/"),
        CART("cart", "chest minecart", null, "chests/");

        final String id;
        final String label;
        @Nullable
        final Block block;
        final List<String> prefixes;

        Spawn(String id, String label, @Nullable Block block, String... prefixes) {
            this.id = id;
            this.label = label;
            this.block = block;
            this.prefixes = List.of(prefixes);
        }
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<LootTable> tableArgument(CommandContext<CommandSourceStack> ctx) {
        ResourceKey<?> key = ctx.getArgument("table", ResourceKey.class);
        if (!key.isFor(Registries.LOOT_TABLE)) {
            throw new IllegalStateException("table '" + key + "' is not a ResourceKey<LootTable>");
        }
        return (ResourceKey<LootTable>) key;
    }

    private static List<ResourceKey<LootTable>> allTables(MinecraftServer server) {
        return server.reloadableRegistries().lookup().lookup(Registries.LOOT_TABLE)
                .map(o -> (HolderLookup<LootTable>) o).map(HolderLookup::listElementIds)
                .orElse(Stream.of()).toList();
    }

    private static List<ResourceKey<LootTable>> tablesFor(MinecraftServer server, Spawn type) {
        List<ResourceKey<LootTable>> out = new ArrayList<>();
        for (ResourceKey<LootTable> key : allTables(server)) {
            String path = key.location().getPath();
            for (String prefix : type.prefixes) {
                if (path.startsWith(prefix)) {
                    if (ModLootTags.isTableEnabled(key)) {
                        out.add(key);
                    }
                    break;
                }
            }
        }
        return out;
    }

    private static int spawn(CommandSourceStack source, Spawn type, @Nullable ResourceKey<LootTable> requested) {
        ServerLevel level = source.getLevel();
        MinecraftServer server = source.getServer();

        ResourceKey<LootTable> table;
        if (requested == null) {
            List<ResourceKey<LootTable>> candidates = tablesFor(server, type);
            if (candidates.isEmpty()) {
                source.sendFailure(Component.literal("No enabled loot table is available for a " + type.label
                        + " (the loot-table filters exclude every " + String.join(" / ", type.prefixes) + " table)."));
                return 0;
            }
            table = candidates.get(level.getRandom().nextInt(candidates.size()));
        } else {
            if (!allTables(server).contains(requested)) {
                source.sendFailure(Component.literal("Unknown loot table: " + requested.location() + "."));
                return 0;
            }
            if (!ModLootTags.isTableEnabled(requested)) {
                source.sendFailure(Component.literal("The loot-table filters exclude " + requested.location()
                        + ", so a container using it would not be per-player. Pick another table or change the filters."));
                return 0;
            }
            table = requested;
        }

        BlockPos pos = BlockPos.containing(source.getPosition());
        long seed = level.getRandom().nextLong();
        if (type.block == null) {
            MinecartChest cart = new MinecartChest(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            cart.setLootTable(table);
            cart.setLootTableSeed(seed);
            level.addFreshEntity(cart);
        } else if (!placeBlock(source, level, pos, type, table, seed)) {
            return 0;
        }

        boolean managed = LootrConfig.isDimensionEnabled(level.dimension());
        source.sendSuccess(() -> Component.literal("Created a loot " + type.label + " at " + pos.getX() + " "
                + pos.getY() + " " + pos.getZ() + " using " + table.location() + "."
                + (managed ? "" : " Warning: Lootr is disabled in this dimension (dimension_whitelist / "
                + "dimension_blacklist), so it will NOT be per-player; it behaves like a vanilla container.")), true);
        return 1;
    }

    private static boolean placeBlock(CommandSourceStack source, ServerLevel level, BlockPos pos, Spawn type,
                                      ResourceKey<LootTable> table, long seed) {
        if (!level.getBlockState(pos).canBeReplaced()) {
            source.sendFailure(Component.literal("There is already a block at " + pos.getX() + " " + pos.getY() + " "
                    + pos.getZ() + "; run this from an empty space."));
            return false;
        }
        BlockState state = type.block.defaultBlockState();
        Entity facing = source.getEntity();
        if (facing != null) {
            if (state.hasProperty(ChestBlock.FACING)) {
                state = state.setValue(ChestBlock.FACING, facing.getDirection().getOpposite());
            } else if (type == Spawn.BARREL && state.hasProperty(BarrelBlock.FACING)) {
                state = state.setValue(BarrelBlock.FACING, Direction.orderedByNearest(facing)[0].getOpposite());
            }
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BrushableBlockEntity brushable) {
            brushable.setLootTable(table, seed);
        } else if (be instanceof DecoratedPotBlockEntity pot) {
            pot.setLootTable(table, seed);
        } else if (be instanceof RandomizableContainerBlockEntity container) {
            container.setLootTable(table, seed);
        } else {
            level.removeBlock(pos, false);
            source.sendFailure(Component.literal("Placing a " + type.label + " did not create a block entity; nothing was created."));
            return false;
        }
        be.setChanged();
        return true;
    }

    private static int forceChunk(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ChunkPos center = new ChunkPos(BlockPos.containing(source.getPosition()));
        Set<Long> chunks = new HashSet<>();
        chunks.add(center.toLong());
        return forceScan(source, Map.of(level, chunks));
    }

    private static int forceRadius(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        ChunkPos center = new ChunkPos(BlockPos.containing(source.getPosition()));
        Set<Long> chunks = new HashSet<>();
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int z = center.z - radius; z <= center.z + radius; z++) {
                chunks.add(ChunkPos.asLong(x, z));
            }
        }
        return forceScan(source, Map.of(level, chunks));
    }

    private static int forceAll(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        int viewDistance = server.getPlayerList().getViewDistance();
        Map<ServerLevel, Set<Long>> targets = new LinkedHashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            Set<Long> chunks = new HashSet<>();
            for (ServerPlayer player : level.players()) {
                ChunkPos at = player.chunkPosition();
                for (int x = at.x - viewDistance; x <= at.x + viewDistance; x++) {
                    for (int z = at.z - viewDistance; z <= at.z + viewDistance; z++) {
                        chunks.add(ChunkPos.asLong(x, z));
                    }
                }
            }
            for (long forced : level.getForcedChunks()) {
                chunks.add(forced);
            }
            if (!chunks.isEmpty()) {
                targets.put(level, chunks);
            }
        }
        return forceScan(source, targets);
    }

    private static int forceScan(CommandSourceStack source, Map<ServerLevel, Set<Long>> targets) {
        boolean decay = ChunkDiscovery.decayEnabled();
        boolean refresh = ChunkDiscovery.refreshEnabled();
        if (!decay && !refresh) {
            source.sendFailure(Component.literal("Nothing to track: decay and refresh are both disabled "
                    + "(decay_value and refresh_value are 0)."));
            return 0;
        }
        int scanned = 0;
        int notLoaded = 0;
        int disabledDimensions = 0;
        ChunkDiscovery.Scan total = ChunkDiscovery.Scan.NONE;
        for (Map.Entry<ServerLevel, Set<Long>> entry : targets.entrySet()) {
            ServerLevel level = entry.getKey();
            if (!LootrConfig.isDimensionEnabled(level.dimension())) {
                disabledDimensions++;
                continue;
            }
            for (long packed : entry.getValue()) {
                ChunkPos pos = new ChunkPos(packed);
                ChunkAccess access = level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
                if (access instanceof LevelChunk chunk) {
                    scanned++;
                    total = total.plus(ChunkDiscovery.scan(level, chunk, decay, refresh));
                } else {
                    notLoaded++;
                }
            }
        }
        if (scanned == 0 && disabledDimensions > 0) {
            source.sendFailure(Component.literal("Lootr is disabled in this dimension "
                    + "(dimension_whitelist / dimension_blacklist); nothing was scanned."));
            return 0;
        }
        final int scannedFinal = scanned;
        final int notLoadedFinal = notLoaded;
        final ChunkDiscovery.Scan result = total;
        source.sendSuccess(() -> Component.literal("Scanned " + scannedFinal + " loaded chunk(s)"
                + (notLoadedFinal > 0 ? " (" + notLoadedFinal + " not loaded, skipped)" : "")
                + ": found " + result.looted() + " looted container(s); newly added "
                + result.decayAdded() + " to the decay tracker and " + result.refreshAdded()
                + " to the refresh tracker (ones already tracked are not counted)."), true);
        return result.decayAdded() + result.refreshAdded();
    }

    private record Key(UUID id, String label, boolean offlineWithTeamLoot) {}

    @FunctionalInterface
    private interface KeyResolver {
        Key resolve(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    private static void withTargets(ArgumentBuilder<CommandSourceStack, ?> parent, KeyResolver resolver) {
        parent.then(Commands.literal("block")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> {
                            Key key = resolver.resolve(ctx);
                            ServerLevel level = ctx.getSource().getLevel();
                            return openAs(ctx.getSource(), key,
                                    level.getBlockEntity(BlockPosArgument.getLoadedBlockPos(ctx, "pos")), "that block");
                        })));
        parent.then(Commands.literal("entity")
                .then(Commands.argument("target", EntityArgument.entity())
                        .executes(ctx -> {
                            Key key = resolver.resolve(ctx);
                            return openAs(ctx.getSource(), key, EntityArgument.getEntity(ctx, "target"), "that entity");
                        })));
    }

    private static Key profileKey(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        if (profiles.size() != 1) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.literal("Name exactly one player.")).create();
        }
        GameProfile profile = profiles.iterator().next();
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayer(profile.getId());
        UUID id = online != null ? TeamResolver.resolve(online) : profile.getId();
        return new Key(id, profile.getName(), online == null && LootrConfig.teamLoot());
    }

    @Nullable
    private static LootrLootState stateOf(@Nullable Object holder, ServerLevel level) {
        CompoundTag tag = null;
        if (holder instanceof BlockEntity be) {
            tag = be.getAttached(ModAttachments.LOOT_STATE);
        } else if (holder instanceof Entity entity) {
            tag = entity.getAttached(ModAttachments.LOOT_STATE);
        }
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        LootrLootState state = new LootrLootState(27);
        state.load(tag, level.registryAccess());
        return state;
    }

    private static int openAs(CommandSourceStack source, Key key, @Nullable Object holder, String what) {
        ServerPlayer admin = source.getPlayer();
        if (admin == null) {
            source.sendFailure(Component.literal("Only a player can open a loot view."));
            return 0;
        }
        boolean block = holder instanceof RandomizableContainerBlockEntity container
                && ContainerInteractionHandler.kindOf(container) != null;
        boolean cart = holder instanceof MinecartChest;
        if (!block && !cart) {
            source.sendFailure(Component.literal("There is no chest, trapped chest, barrel, shulker box or chest minecart there."));
            return 0;
        }
        LootrLootState state = stateOf(holder, source.getLevel());
        if (state == null || !state.hasGeneratedFor(key.id())) {
            source.sendFailure(Component.literal(key.label() + " has no loot entry in " + what
                    + " (never looted it, or it was cleared or refreshed). Nothing was rolled."
                    + (key.offlineWithTeamLoot()
                    ? " Team loot is on and that player is offline, so their team's entry can't be looked up; try again while they are online." : "")));
            return 0;
        }

        Predicate<Player> stillValid = holder instanceof BlockEntity be
                ? p -> Container.stillValidBlockEntity(be, p)
                : p -> !((Entity) holder).isRemoved() && p.distanceToSqr((Entity) holder) <= 64.0;
        ReadOnlyLootView view = new ReadOnlyLootView(state.getContentsOrEmpty(key.id()), state.getContainerSize(), stillValid);
        boolean shulker = holder instanceof ShulkerBoxBlockEntity;
        admin.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> shulker ? new ViewOnlyShulkerMenu(id, inventory, view) : new ViewOnlyChestMenu(id, inventory, view),
                Component.literal("Loot of " + key.label() + " (view only)")));
        return 1;
    }

    private static final class ViewOnlyChestMenu extends ChestMenu {
        ViewOnlyChestMenu(int id, Inventory inventory, Container view) {
            super(MenuType.GENERIC_9x3, id, inventory, view, 3);
        }

        @Override
        public void clicked(int slotId, int button, ClickType clickType, Player player) {
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }
    }

    private static final class ViewOnlyShulkerMenu extends ShulkerBoxMenu {
        ViewOnlyShulkerMenu(int id, Inventory inventory, Container view) {
            super(id, inventory, view);
        }

        @Override
        public void clicked(int slotId, int button, ClickType clickType, Player player) {
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }
    }

    private AdminCommands() {}
}
