package com.yuri.addons.utils;

import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class InteractionUtils {
    private InteractionUtils() {
    }

    public static boolean canPlaceAt(BlockPos pos, boolean checkEntities, Block block, double maxRange) {
        if (!BlockUtils.canPlaceBlock(pos, checkEntities, block)) return false;
        if (isPlayerInWeb()) return false;

        Direction side = BlockUtils.getPlaceSide(pos);
        if (side == null) return false;

        Vec3 hitPos = Vec3.atCenterOf(pos).add(
            side.getStepX() * 0.5,
            side.getStepY() * 0.5,
            side.getStepZ() * 0.5
        );

        double range = Math.min(maxRange, mc.player.blockInteractionRange());
        if (mc.player.getEyePosition().distanceToSqr(hitPos) > range * range) return false;

        BlockPos support = pos.relative(side);
        BlockHitResult hit = mc.level.clip(new ClipContext(
            mc.player.getEyePosition(), hitPos,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player
        ));

        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(support);
    }

    public static boolean canPickupWater(BlockPos pos, double maxRange) {
        if (isPlayerInWeb()) return false;

        double range = Math.min(maxRange, mc.player.blockInteractionRange());
        Vec3 center = Vec3.atCenterOf(pos);

        if (mc.player.getEyePosition().distanceToSqr(center) > range * range) return false;

        BlockHitResult hit = mc.level.clip(new ClipContext(
            mc.player.getEyePosition(), center,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, mc.player
        ));

        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    public static boolean isPlayerInWeb() {
        if (mc.player == null || mc.level == null) return false;

        BlockPos feet = mc.player.blockPosition();
        if (mc.level.getBlockState(feet).is(Blocks.COBWEB)) return true;

        BlockPos eye = BlockPos.containing(mc.player.getEyePosition());
        return mc.level.getBlockState(eye).is(Blocks.COBWEB);
    }
}
