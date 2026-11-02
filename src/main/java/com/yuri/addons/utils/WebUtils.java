package com.yuri.addons.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class WebUtils {
    private WebUtils() {
    }

    public static List<BlockPos> getPlacePositions(Player target, WebPosition webPosition, boolean predict,
                                                   int predictionTicks, boolean checkEntities, double placeRange) {
        List<BlockPos> positions = new ArrayList<>(4);
        Vec3 base = target.position();

        if (predict) {
            double ticks = predictionTicks;
            double dx = target.getX() - target.xo;
            double dy = target.getY() - target.yo;
            double dz = target.getZ() - target.zo;
            base = base.add(dx * ticks, dy * ticks, dz * ticks);
        }

        BlockPos feet = BlockPos.containing(base);
        BlockPos actualFeet = target.blockPosition();

        if (webPosition == WebPosition.Feet || webPosition == WebPosition.Both) {
            addPosition(positions, feet, checkEntities, placeRange);
            addPosition(positions, actualFeet, checkEntities, placeRange);
        }

        if (webPosition == WebPosition.Head || webPosition == WebPosition.Both) {
            BlockPos head = BlockPos.containing(base.add(0, target.getBbHeight() * 0.75, 0));
            addPosition(positions, head, checkEntities, placeRange);
            addPosition(positions, actualFeet.above(), checkEntities, placeRange);
        }

        return positions;
    }

    private static void addPosition(List<BlockPos> positions, BlockPos pos, boolean checkEntities, double placeRange) {
        if (pos == null || positions.contains(pos)) return;
        if (InteractionUtils.canPlaceAt(pos, checkEntities, Blocks.COBWEB, placeRange)) positions.add(pos);
    }

    public enum WebPosition {
        Feet,
        Head,
        Both
    }
}
