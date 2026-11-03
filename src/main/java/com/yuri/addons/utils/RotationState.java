package com.yuri.addons.utils;

import meteordevelopment.meteorclient.utils.player.Rotations;

import java.lang.reflect.Field;
import java.util.List;

public final class RotationState {
    private RotationState() {
    }

    public static void reset() {
        try {
            Field rotationsField = Rotations.class.getDeclaredField("rotations");
            rotationsField.setAccessible(true);
            Object rotations = rotationsField.get(null);
            if (rotations instanceof List<?> list) list.clear();

            Field lastRotationField = Rotations.class.getDeclaredField("lastRotation");
            lastRotationField.setAccessible(true);
            lastRotationField.set(null, null);

            Field indexField = Rotations.class.getDeclaredField("i");
            indexField.setAccessible(true);
            indexField.setInt(null, 0);

            Field sentLastField = Rotations.class.getDeclaredField("sentLastRotation");
            sentLastField.setAccessible(true);
            sentLastField.setBoolean(null, false);

            Rotations.rotating = false;
            Rotations.rotationTimer = 0;
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
