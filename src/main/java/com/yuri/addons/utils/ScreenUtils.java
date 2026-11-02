package com.yuri.addons.utils;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

public final class ScreenUtils {
    private ScreenUtils() {
    }

    public static boolean isOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;

        try {
            Field screen = Minecraft.class.getField("screen");
            return screen.get(mc) != null;
        } catch (NoSuchFieldException e) {
            try {
                Object gui = Minecraft.class.getField("gui").get(mc);
                return gui != null && gui.getClass().getMethod("screen").invoke(gui) != null;
            } catch (ReflectiveOperationException ex) {
                return false;
            }
        } catch (IllegalAccessException e) {
            return false;
        }
    }
}
