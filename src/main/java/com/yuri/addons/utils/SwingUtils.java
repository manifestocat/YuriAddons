package com.yuri.addons.utils;

import net.minecraft.world.InteractionHand;

import java.lang.reflect.Method;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class SwingUtils {
    private SwingUtils() {
    }

    public static void swing(InteractionHand hand) {
        if (mc.player == null) return;

        try {
            Method method = mc.player.getClass().getMethod("swing", InteractionHand.class);
            method.invoke(mc.player, hand);
            return;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Object stack = mc.player.getItemInHand(hand);
            Method animationMethod = stack.getClass().getMethod("getAttackAnimation");
            Object animation = animationMethod.invoke(stack);
            Class<?> animationClass = Class.forName("net.minecraft.world.item.component.SwingAnimation");
            Method method = mc.player.getClass().getMethod("swing", InteractionHand.class, animationClass, boolean.class);
            method.invoke(mc.player, hand, animation, false);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
