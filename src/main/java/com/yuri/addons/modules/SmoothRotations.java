package com.yuri.addons.modules;

import com.yuri.addons.YuriAddons;
import com.yuri.addons.utils.RotationState;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SmoothRotations extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Percentage of the remaining rotation to apply each tick.")
        .defaultValue(30.0)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Double> maxSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-speed")
        .description("Maximum degrees per tick on each axis. 0 means no limit.")
        .defaultValue(25.0)
        .min(0)
        .sliderRange(0, 180)
        .build()
    );

    private final Setting<Integer> holdTicks = sgGeneral.add(new IntSetting.Builder()
        .name("hold-ticks")
        .description("Ticks to keep sending the final rotation after it is reached.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> timeout = sgGeneral.add(new IntSetting.Builder()
        .name("timeout")
        .description("Ticks before a rotation snaps to its target.")
        .defaultValue(40)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Double> snapThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("snap-threshold")
        .description("Angle in degrees at which a rotation is considered complete.")
        .defaultValue(0.5)
        .min(0.01)
        .sliderRange(0.01, 5)
        .build()
    );

    private final Setting<Boolean> visible = sgGeneral.add(new BoolSetting.Builder()
        .name("visible")
        .description("Smooth the visible camera for rotations that are normally silent.")
        .defaultValue(false)
        .build()
    );

    private final List<SmoothRotation> queue = new ArrayList<>();
    private SmoothRotation active;
    private float currentYaw;
    private float currentPitch;
    private float preYaw;
    private float prePitch;
    private float holdYaw;
    private float holdPitch;
    private int holdTimer;
    private boolean holdClientSide;
    private boolean applied;
    private long tick;

    public SmoothRotations() {
        super(YuriAddons.CATEGORY, "smooth-rotations",
            "Smooths all rotations requested through Meteor's rotation system.",
            "smoothrotations", "smooth-rotations-plus");
    }

    @Override
    public void onActivate() {
        queue.clear();
        active = null;
        holdTimer = 0;
        applied = false;
        tick = 0;

        if (mc.player != null) {
            currentYaw = mc.player.getYRot();
            currentPitch = mc.player.getXRot();
        }

        RotationState.reset();
    }

    @Override
    public void onDeactivate() {
        queue.clear();
        active = null;
        holdTimer = 0;

        if (applied && mc.player != null) {
            mc.player.setYRot(preYaw);
            mc.player.setXRot(prePitch);
        }

        applied = false;
        Rotations.rotating = false;
        RotationState.reset();
    }

    public void addRotation(double yaw, double pitch, int priority, boolean clientSide, Runnable callback) {
        if (!isActive() || mc.player == null) return;

        if (active != null && active.callback == null && callback == null && active.priority == priority) {
            active.targetYaw = yaw;
            active.targetPitch = pitch;
            active.clientSide = clientSide;
            active.startTick = tick;
            active.reached = false;
            return;
        }

        SmoothRotation rotation = new SmoothRotation(yaw, pitch, priority, clientSide, callback, tick);

        if (active == null && queue.isEmpty()) {
            currentYaw = mc.player.getYRot();
            currentPitch = mc.player.getXRot();
        }

        if (active == null) {
            active = rotation;
            return;
        }

        if (rotation.priority > active.priority && active.callback == null) {
            queue.add(active);
            active = rotation;
        } else {
            queue.add(rotation);
        }

        queue.sort(Comparator.comparingInt((SmoothRotation r) -> r.priority).reversed());
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tick++;
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onSendPre(SendMovementPacketsEvent.Pre event) {
        if (!Utils.canUpdate() || mc.player == null) return;
        if (mc.getCameraEntity() != mc.player) return;

        applied = false;
        preYaw = mc.player.getYRot();
        prePitch = mc.player.getXRot();

        if (active == null) {
            if (!queue.isEmpty()) {
                active = queue.remove(0);
                active.startTick = tick;
            } else if (holdTimer > 0) {
                mc.player.setYRot(holdYaw);
                mc.player.setXRot(holdPitch);
                setServerRotation(holdYaw, holdPitch);
                applied = true;
                return;
            } else {
                Rotations.rotating = false;
                return;
            }
        }

        float yawDelta = Mth.wrapDegrees((float) (active.targetYaw - currentYaw));
        float pitchDelta = Mth.wrapDegrees((float) (active.targetPitch - currentPitch));
        boolean timedOut = tick - active.startTick > timeout.get();

        if (timedOut || Math.abs(yawDelta) <= snapThreshold.get() && Math.abs(pitchDelta) <= snapThreshold.get()) {
            currentYaw = (float) active.targetYaw;
            currentPitch = (float) active.targetPitch;
            active.reached = true;
        } else {
            float factor = (float) (speed.get() / 100.0);
            float limit = maxSpeed.get().floatValue();

            float yawStep = yawDelta * factor;
            float pitchStep = pitchDelta * factor;

            if (limit > 0) {
                yawStep = Mth.clamp(yawStep, -limit, limit);
                pitchStep = Mth.clamp(pitchStep, -limit, limit);
            }

            currentYaw += yawStep;
            currentPitch = Mth.clamp(currentPitch + pitchStep, -90.0F, 90.0F);
        }

        mc.player.setYRot(currentYaw);
        mc.player.setXRot(currentPitch);
        setServerRotation(currentYaw, currentPitch);
        applied = true;
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onSendPost(SendMovementPacketsEvent.Post event) {
        if (!applied) return;
        applied = false;

        if (active != null && active.reached) {
            SmoothRotation finished = active;
            active = queue.isEmpty() ? null : queue.remove(0);

            if (active != null) active.startTick = tick;
            if (finished.callback != null) finished.callback.run();

            if (active == null && queue.isEmpty()) {
                holdYaw = currentYaw;
                holdPitch = currentPitch;
                holdClientSide = finished.clientSide;
                holdTimer = holdTicks.get();
            }
        }

        boolean keepCamera = visible.get();
        if (active != null && active.clientSide) keepCamera = true;
        if (active == null && holdTimer > 0 && holdClientSide) keepCamera = true;

        if (!keepCamera && mc.player != null) {
            mc.player.setYRot(preYaw);
            mc.player.setXRot(prePitch);
        }

        if (active == null && holdTimer > 0) holdTimer--;
        if (active == null && holdTimer <= 0) Rotations.rotating = false;
    }

    private void setServerRotation(float yaw, float pitch) {
        Rotations.serverYaw = yaw;
        Rotations.serverPitch = pitch;
        Rotations.rotationTimer = 0;
        Rotations.rotating = true;
    }

    private static final class SmoothRotation {
        double targetYaw;
        double targetPitch;
        final int priority;
        boolean clientSide;
        final Runnable callback;
        long startTick;
        boolean reached;

        SmoothRotation(double targetYaw, double targetPitch, int priority, boolean clientSide, Runnable callback, long startTick) {
            this.targetYaw = targetYaw;
            this.targetPitch = targetPitch;
            this.priority = priority;
            this.clientSide = clientSide;
            this.callback = callback;
            this.startTick = startTick;
        }
    }
}
