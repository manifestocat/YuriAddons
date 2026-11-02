package com.yuri.addons.modules;

import com.yuri.addons.YuriAddons;
import com.yuri.addons.utils.ScreenUtils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class AimAssistPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgAim = settings.createGroup("Aim");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> pauseInGui = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-in-gui")
        .description("Stop aiming while a screen is open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyOnAttack = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-attack")
        .description("Only aim while the attack key is held.")
        .defaultValue(true)
        .build()
    );

    private final Setting<TargetMode> targetMode = sgTargeting.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .description("How the target is selected.")
        .defaultValue(TargetMode.Crosshair)
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance to target a player.")
        .defaultValue(4.5)
        .min(1)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Double> fov = sgTargeting.add(new DoubleSetting.Builder()
        .name("fov")
        .description("Maximum angle between your crosshair and the target.")
        .defaultValue(45.0)
        .min(1)
        .sliderRange(1, 180)
        .build()
    );

    private final Setting<AimPoint> aimPoint = sgTargeting.add(new EnumSetting.Builder<AimPoint>()
        .name("aim-point")
        .description("Where on the target to aim.")
        .defaultValue(AimPoint.Body)
        .build()
    );

    private final Setting<Integer> stickiness = sgTargeting.add(new IntSetting.Builder()
        .name("stickiness")
        .description("Ticks to keep the current target after it leaves range or FOV. 0 disables stickiness.")
        .defaultValue(8)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Boolean> ignoreInvisible = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-invisible")
        .description("Do not target invisible players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Do not target friends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyTargetOnGround = sgTargeting.add(new BoolSetting.Builder()
        .name("only-target-on-ground")
        .description("Only target players who are standing on the ground.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> checkLineOfSight = sgTargeting.add(new BoolSetting.Builder()
        .name("check-line-of-sight")
        .description("Only target players you can see.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> speed = sgAim.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Percentage of the remaining angle to turn each tick.")
        .defaultValue(10.0)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Double> maxSpeed = sgAim.add(new DoubleSetting.Builder()
        .name("max-speed")
        .description("Maximum turn in degrees per tick. 0 means no limit.")
        .defaultValue(20.0)
        .min(0)
        .sliderRange(0, 360)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgAim.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Vertical aim speed as a percentage of horizontal speed.")
        .defaultValue(45.0)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Double> smoothing = sgAim.add(new DoubleSetting.Builder()
        .name("smoothing")
        .description("Extra acceleration smoothing. Higher values are smoother but react slower.")
        .defaultValue(70.0)
        .min(0)
        .sliderRange(0, 95)
        .build()
    );

    private final Setting<Integer> reactionDelayMin = sgAim.add(new IntSetting.Builder()
        .name("reaction-delay-min")
        .description("Minimum delay before aiming at a new target in milliseconds.")
        .defaultValue(40)
        .min(0)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Integer> reactionDelayMax = sgAim.add(new IntSetting.Builder()
        .name("reaction-delay-max")
        .description("Maximum delay before aiming at a new target in milliseconds.")
        .defaultValue(100)
        .min(0)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Double> randomness = sgAim.add(new DoubleSetting.Builder()
        .name("randomness")
        .description("Random angular offset in degrees added each tick.")
        .defaultValue(0.15)
        .min(0)
        .sliderRange(0, 5)
        .build()
    );

    private final Setting<Boolean> predictMovement = sgAim.add(new BoolSetting.Builder()
        .name("predict-movement")
        .description("Predict the target's movement.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> predictionTicks = sgAim.add(new DoubleSetting.Builder()
        .name("prediction-ticks")
        .description("How many ticks ahead to predict.")
        .defaultValue(2.0)
        .min(0)
        .sliderRange(0, 10)
        .visible(predictMovement::get)
        .build()
    );

    private final Setting<Boolean> predictPing = sgAim.add(new BoolSetting.Builder()
        .name("predict-ping")
        .description("Add ping-based prediction ticks.")
        .defaultValue(false)
        .visible(predictMovement::get)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Render a box around the current target.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the target box is rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Side color of the target box.")
        .defaultValue(new SettingColor(255, 120, 120, 35))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Line color of the target box.")
        .defaultValue(new SettingColor(255, 120, 120, 255))
        .visible(render::get)
        .build()
    );

    private Player target;
    private long tick;
    private long lastValidTick;
    private float yawVelocity;
    private float pitchVelocity;
    private long aimStart;

    public AimAssistPlus() {
        super(YuriAddons.CATEGORY, "aim-assist-plus",
            "Smoothly assists your aim towards nearby players.",
            "aimassistplus", "aim-plus", "aimassist");
    }

    @Override
    public void onActivate() {
        target = null;
        tick = 0;
        lastValidTick = 0;
        aimStart = 0;
        resetAim();
    }

    @Override
    public void onDeactivate() {
        target = null;
        resetAim();
    }

    @Override
    public String getInfoString() {
        return target != null ? target.getName().getString() : "No target";
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;

        tick++;

        if (pauseInGui.get() && ScreenUtils.isOpen()) {
            target = null;
            resetAim();
            return;
        }

        if (onlyOnAttack.get() && !mc.options.keyAttack.isDown()) {
            target = null;
            resetAim();
            return;
        }

        updateTarget();
        if (target == null) {
            resetAim();
            return;
        }
        if (mc.player.distanceTo(target) > range.get()) {
            resetAim();
            return;
        }

        if (System.currentTimeMillis() < aimStart) {
            resetAim();
            return;
        }

        aimAt(target);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || target == null) return;

        event.renderer.box(target.getBoundingBox(), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    private void updateTarget() {
        if (target != null) {
            boolean valid = isValidTarget(target) && inFov(target) && mc.player.distanceTo(target) <= range.get();

            if (valid) {
                lastValidTick = tick;
                return;
            }

            if (stickiness.get() > 0 && canStick(target) && tick - lastValidTick <= stickiness.get()) {
                return;
            }

            target = null;
            resetAim();
        }

        Player previous = target;
        target = findBestTarget();

        if (target != previous) {
            resetAim();

            if (target != null) {
                aimStart = System.currentTimeMillis() + randomReactionDelay();
            }
        }

        if (target != null) lastValidTick = tick;
    }

    private int randomReactionDelay() {
        int min = reactionDelayMin.get();
        int max = reactionDelayMax.get();
        if (max < min) max = min;
        return min + (int) (Math.random() * (max - min + 1));
    }

    private void resetAim() {
        yawVelocity = 0.0F;
        pitchVelocity = 0.0F;
    }

    private boolean canStick(Player player) {
        if (!player.isAlive() || player.isDeadOrDying() || player.isSpectator()) return false;
        if (ignoreInvisible.get() && player.isInvisible()) return false;
        if (ignoreFriends.get() && Friends.get().isFriend(player)) return false;
        if (onlyTargetOnGround.get() && !player.onGround()) return false;
        return !checkLineOfSight.get() || canSee(player);
    }

    private Player findBestTarget() {
        Player best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 look = mc.player.getViewVector(1.0F);
        double fovCos = Math.cos(Math.toRadians(fov.get()));

        for (Player player : mc.level.players()) {
            if (!isValidTarget(player)) continue;

            Vec3 direction = aimPos(player).subtract(mc.player.getEyePosition());
            if (direction.lengthSqr() < 1.0E-6) continue;

            double dot = look.dot(direction.normalize());
            if (dot < fovCos) continue;

            double score = switch (targetMode.get()) {
                case Crosshair -> -dot;
                case Nearest -> mc.player.distanceTo(player);
                case LowestHealth -> player.getHealth();
            };

            if (score < bestScore) {
                bestScore = score;
                best = player;
            }
        }

        return best;
    }

    private boolean inFov(Player player) {
        Vec3 direction = aimPos(player).subtract(mc.player.getEyePosition());
        if (direction.lengthSqr() < 1.0E-6) return true;

        return mc.player.getViewVector(1.0F).dot(direction.normalize()) >= Math.cos(Math.toRadians(fov.get()));
    }

    private boolean isValidTarget(Player player) {
        if (player == null || player == mc.player) return false;
        if (!player.isAlive() || player.isDeadOrDying()) return false;
        if (player.isSpectator()) return false;
        if (ignoreInvisible.get() && player.isInvisible()) return false;
        if (ignoreFriends.get() && Friends.get().isFriend(player)) return false;
        if (mc.player.distanceTo(player) > range.get()) return false;
        if (onlyTargetOnGround.get() && !player.onGround()) return false;
        return !checkLineOfSight.get() || canSee(player);
    }

    private boolean canSee(Player player) {
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 aim = aimPos(player);

        BlockHitResult hit = mc.level.clip(new ClipContext(
            eyes, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player
        ));

        return hit.getType() == HitResult.Type.MISS;
    }

    private Vec3 aimPos(Player player) {
        Vec3 pos = switch (aimPoint.get()) {
            case Head -> player.getEyePosition();
            case Body -> player.position().add(0, player.getBbHeight() * 0.5, 0);
            case Feet -> player.position().add(0, 0.1, 0);
        };

        if (predictMovement.get()) {
            double ticks = predictionTicks.get();
            if (predictPing.get()) ticks += Math.min(PlayerUtils.getPing() / 50.0, 5.0);

            double dx = player.getX() - player.xo;
            double dy = player.getY() - player.yo;
            double dz = player.getZ() - player.zo;

            pos = pos.add(dx * ticks, dy * ticks, dz * ticks);
        }

        return pos;
    }

    private void aimAt(Player player) {
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 aim = aimPos(player);

        double dx = aim.x - eyes.x;
        double dy = aim.y - eyes.y;
        double dz = aim.z - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float desiredYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float desiredPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));

        double jitter = randomness.get();
        if (jitter > 0) {
            desiredYaw += (float) ((Math.random() * 2.0 - 1.0) * jitter);
            desiredPitch += (float) ((Math.random() * 2.0 - 1.0) * jitter);
        }

        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        float yawDelta = Mth.wrapDegrees(desiredYaw - currentYaw);
        float pitchDelta = Mth.wrapDegrees(desiredPitch - currentPitch);

        float factor = (float) (speed.get() / 100.0);
        float yawStep = yawDelta * factor;
        float pitchStep = pitchDelta * factor * (float) (verticalSpeed.get() / 100.0);

        float limit = maxSpeed.get().floatValue();
        if (limit > 0) {
            yawStep = Mth.clamp(yawStep, -limit, limit);
            pitchStep = Mth.clamp(pitchStep, -limit, limit);
        }

        float response = 1.0F - (float) (smoothing.get() / 100.0);
        yawVelocity += (yawStep - yawVelocity) * response;
        pitchVelocity += (pitchStep - pitchVelocity) * response;

        yawVelocity = Mth.clamp(yawVelocity, -Math.abs(yawDelta), Math.abs(yawDelta));
        pitchVelocity = Mth.clamp(pitchVelocity, -Math.abs(pitchDelta), Math.abs(pitchDelta));

        mc.player.setYRot(currentYaw + yawVelocity);
        mc.player.setXRot(Mth.clamp(currentPitch + pitchVelocity, -90.0F, 90.0F));
    }

    public enum TargetMode {
        Crosshair,
        Nearest,
        LowestHealth
    }

    public enum AimPoint {
        Head,
        Body,
        Feet
    }
}
