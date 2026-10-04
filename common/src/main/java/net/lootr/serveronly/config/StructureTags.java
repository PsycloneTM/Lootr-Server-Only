package net.lootr.serveronly.config;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.List;

/**
 * Structure tags that widen which containers refresh or decay, the server-only
 * counterpart of upstream Lootr's {@code lootr:decay} / {@code lootr:refresh}
 * structure tags. Both ship EMPTY, so nothing changes until a datapack (or an
 * admin editing the jar's tag files) adds structures, e.g.
 * <pre>{"values": ["minecraft:desert_pyramid", "#minecraft:village"]}</pre>
 * in {@code data/lootr_serveronly/tags/worldgen/structure/decay.json}. A
 * container then refreshes/decays when it sits inside a tagged structure even
 * if its loot table is not listed in the config.
 * <p>
 * "Inside" is the structure's bounding box grown by 8 blocks, exactly as
 * upstream does it. Upstream also special-cases desert pyramid pits and has an
 * optional per-piece check; neither is ported, so a trap chest in a pyramid pit
 * is covered by a fixed box, and with {@code perform_piecewise_check} each structure piece is tested as well.
 */
public final class StructureTags {
    public static final TagKey<Structure> DECAY = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath("lootr_serveronly", "decay"));
    public static final TagKey<Structure> REFRESH = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath("lootr_serveronly", "refresh"));

    /** Desert pyramid pits sit outside the structure's bounding box; this box (relative to its centre) covers them, as upstream. */
    private static final BoundingBox DESERT_PYRAMID_PIT = new BoundingBox(-5, -30, -5, 5, 4, 4);

    /** True if {@code pos} lies inside a structure that is in {@code tag}. */
    public static boolean isIn(ServerLevel level, BlockPos pos, TagKey<Structure> tag) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        // The default, empty tag: skip the structure lookup entirely. This runs for every tracked
        // container every sweep, so an unconfigured server must pay nothing for it.
        if (registry.getTag(tag).map(set -> set.size() == 0).orElse(true)) {
            return false;
        }
        List<StructureStart> starts = level.structureManager().startsForStructure(new ChunkPos(pos),
                structure -> registry.getHolder(registry.getId(structure)).map(holder -> holder.is(tag)).orElse(false));
        for (StructureStart start : starts) {
            if (start.getBoundingBox().inflatedBy(8).isInside(pos)) {
                return true;
            }
            if (start.getStructure().type() == StructureType.DESERT_PYRAMID) {
                BlockPos c = start.getBoundingBox().getCenter();
                if (DESERT_PYRAMID_PIT.moved(c.getX(), c.getY(), c.getZ()).isInside(pos)) {
                    return true;
                }
            }
        }
        if (LootrSettings.performPiecewiseCheck()) { // perform_piecewise_check
            for (StructureStart start : starts) {
                for (StructurePiece piece : start.getPieces()) {
                    if (piece.getBoundingBox().inflatedBy(8).isInside(pos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private StructureTags() {}
}
