package com.yuri.addons.utils;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class WebPlacement {
    private WebPlacement() {
    }

    public static boolean place(BlockPos pos, FindItemResult web, boolean rotate, int priority, boolean checkEntities,
                                double maxRange, BooleanSupplier canRun, Consumer<Boolean> onFinished) {
        if (!InteractionUtils.canPlaceAt(pos, checkEntities, Blocks.COBWEB, maxRange)) return false;

        Direction side = BlockUtils.getPlaceSide(pos);
        if (side == null) return false;

        Vec3 hitPos = Vec3.atCenterOf(pos).add(
            side.getStepX() * 0.5,
            side.getStepY() * 0.5,
            side.getStepZ() * 0.5
        );

        BlockPos neighbour = pos.relative(side);
        BlockHitResult hit = new BlockHitResult(hitPos, side.getOpposite(), neighbour, false);
        InteractionHand hand = web.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

        Runnable action = () -> {
            if (!canRun.getAsBoolean()) {
                onFinished.accept(false);
                return;
            }

            if (hand == InteractionHand.MAIN_HAND && !web.isMainHand()) {
                if (!InvUtils.swap(web.slot(), false)) {
                    onFinished.accept(false);
                    return;
                }
            }

            if (!mc.player.getItemInHand(hand).is(Items.COBWEB)) {
                onFinished.accept(false);
                return;
            }

            boolean wasSneaking = mc.player.isShiftKeyDown();
            mc.player.setShiftKeyDown(false);
            InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, hit);
            mc.player.setShiftKeyDown(wasSneaking);

            onFinished.accept(result.consumesAction());
        };

        if (rotate) {
            Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), priority, action);
        } else {
            action.run();
        }

        return true;
    }
}
