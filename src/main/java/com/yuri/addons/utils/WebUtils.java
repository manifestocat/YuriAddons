package com.yuri.addons.utils;

import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class WebUtils {
    private WebUtils() {
    }

    public static List<BlockPos> getPlacePositions(Player target, WebPosition webPosition, boolean predict,
                                                   int predictionTicks, boolean checkEntities, double placeRange,
                                                   int fallbackDown) {
        List<BlockPos> positions = new ArrayList<>(4);
        Vec3 base = target.position();

        if (predict) {
            double ticks = predictionTicks;
            double dx = target.getX() - target.xo;
            double dy = target.getY() - target.yo;
            double dz = target.getZ() - target.zo;
            base = base.add(dx * ticks, dy * ticks, dz * ticks);
        }

        BlockPos feet = BlockPos.containing(base.add(0, 0.01, 0));
        BlockPos actualFeet = BlockPos.containing(target.getX(), target.getY() + 0.01, target.getZ());

        if (webPosition == WebPosition.Feet || webPosition == WebPosition.Both) {
            addPosition(positions, feet, checkEntities, placeRange);
            addPosition(positions, actualFeet, checkEntities, placeRange);

            for (int i = 1; i <= fallbackDown; i++) {
                addPosition(positions, feet.below(i), checkEntities, placeRange);
                addPosition(positions, actualFeet.below(i), checkEntities, placeRange);
            }
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

    public static List<BlockPos> getPlacePositions(Player target, WebPosition webPosition, boolean predict, int predictionTicks,
                                                   boolean airPlace, boolean checkEntities, double placeRange, boolean checkLineOfSight) {
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
            addLegacyPosition(positions, feet, airPlace, checkEntities, placeRange, checkLineOfSight);
            addLegacyPosition(positions, actualFeet, airPlace, checkEntities, placeRange, checkLineOfSight);
        }

        if (webPosition == WebPosition.Head || webPosition == WebPosition.Both) {
            BlockPos head = BlockPos.containing(base.add(0, target.getBbHeight() * 0.75, 0));
            addLegacyPosition(positions, head, airPlace, checkEntities, placeRange, checkLineOfSight);
            addLegacyPosition(positions, actualFeet.above(), airPlace, checkEntities, placeRange, checkLineOfSight);
        }

        return positions;
    }

    private static void addLegacyPosition(List<BlockPos> positions, BlockPos pos, boolean airPlace, boolean checkEntities,
                                          double placeRange, boolean checkLineOfSight) {
        if (pos == null || positions.contains(pos)) return;
        if (canPlace(pos, airPlace, checkEntities, placeRange, checkLineOfSight)) positions.add(pos);
    }

    public static boolean canPlace(BlockPos pos, boolean airPlace, boolean checkEntities, double placeRange, boolean checkLineOfSight) {
        if (!BlockUtils.canPlaceBlock(pos, checkEntities, Blocks.COBWEB)) return false;
        if (!airPlace && BlockUtils.getPlaceSide(pos) == null) return false;
        if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > placeRange * placeRange) return false;
        return !checkLineOfSight || canSee(pos);
    }

    public static boolean canSee(BlockPos pos) {
        BlockHitResult hit = mc.level.clip(new ClipContext(
            mc.player.getEyePosition(), Vec3.atCenterOf(pos),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player
        ));

        return hit.getType() == HitResult.Type.MISS;
    }

    public enum WebPosition {
        Feet,
        Head,
        Both
    }
}
