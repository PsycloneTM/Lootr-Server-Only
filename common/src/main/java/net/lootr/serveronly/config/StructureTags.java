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

public final class StructureTags {
    public static final TagKey<Structure> DECAY = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath("lootr_serveronly", "decay"));
    public static final TagKey<Structure> REFRESH = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath("lootr_serveronly", "refresh"));

    private static final BoundingBox DESERT_PYRAMID_PIT = new BoundingBox(-5, -30, -5, 5, 4, 4);

    public static boolean isIn(ServerLevel level, BlockPos pos, TagKey<Structure> tag) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
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
        if (LootrSettings.performPiecewiseCheck()) {
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
