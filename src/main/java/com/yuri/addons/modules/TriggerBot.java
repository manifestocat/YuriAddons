package com.yuri.addons.modules;

import com.yuri.addons.YuriAddons;
import com.yuri.addons.utils.ScreenUtils;
import com.yuri.addons.utils.SwingUtils;
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
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class TriggerBot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> chance = sgGeneral.add(new DoubleSetting.Builder()
        .name("chance")
        .description("Chance percentage to attack when a target is available.")
        .defaultValue(90.0)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Integer> delayMin = sgGeneral.add(new IntSetting.Builder()
        .name("delay-min")
        .description("Minimum delay between attacks in milliseconds.")
        .defaultValue(60)
        .min(0)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Integer> delayMax = sgGeneral.add(new IntSetting.Builder()
        .name("delay-max")
        .description("Maximum delay between attacks in milliseconds.")
        .defaultValue(160)
        .min(0)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Boolean> waitForCooldown = sgGeneral.add(new BoolSetting.Builder()
        .name("wait-for-cooldown")
        .description("Wait for the vanilla attack cooldown before attacking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyWhileAttacking = sgGeneral.add(new BoolSetting.Builder()
        .name("only-while-attacking")
        .description("Only attack while the attack key is held.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing your hand when attacking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseInGui = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-in-gui")
        .description("Do not attack while a screen is open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<TargetMode> targetMode = sgTargeting.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .description("How the target is selected. Crosshair only attacks the entity under your crosshair.")
        .defaultValue(TargetMode.Crosshair)
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance to target a player.")
        .defaultValue(3.0)
        .min(1)
        .sliderRange(1, 6)
        .build()
    );

    private final Setting<Double> fov = sgTargeting.add(new DoubleSetting.Builder()
        .name("fov")
        .description("Maximum angle between your crosshair and the target for non-crosshair modes.")
        .defaultValue(60.0)
        .min(1)
        .sliderRange(1, 180)
        .visible(() -> targetMode.get() != TargetMode.Crosshair)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Do not attack friends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreInvisible = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-invisible")
        .description("Do not attack invisible players.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyTargetOnGround = sgTargeting.add(new BoolSetting.Builder()
        .name("only-target-on-ground")
        .description("Only attack players who are standing on the ground.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> checkLineOfSight = sgTargeting.add(new BoolSetting.Builder()
        .name("check-line-of-sight")
        .description("Only attack players you can see.")
        .defaultValue(true)
        .visible(() -> targetMode.get() != TargetMode.Crosshair)
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
        .defaultValue(new SettingColor(255, 200, 90, 35))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Line color of the target box.")
        .defaultValue(new SettingColor(255, 200, 90, 255))
        .visible(render::get)
        .build()
    );

    private Player target;
    private long nextAttack;
    private long tick;

    public TriggerBot() {
        super(YuriAddons.CATEGORY, "trigger-bot",
            "Automatically attacks the target under your crosshair with randomized timing.",
            "triggerbot", "trigger-bot-plus");
    }

    @Override
    public void onActivate() {
        target = null;
        nextAttack = 0;
        tick = 0;
    }

    @Override
    public void onDeactivate() {
        target = null;
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
            return;
        }

        if (onlyWhileAttacking.get() && !mc.options.keyAttack.isDown()) {
            target = null;
            return;
        }

        target = findTarget();
        if (target == null) return;

        long now = System.currentTimeMillis();
        if (now < nextAttack) return;
        if (waitForCooldown.get() && mc.player.getAttackStrengthScale(0.5F) < 1.0F) return;

        nextAttack = now + randomDelay();
        if (Math.random() * 100.0 >= chance.get()) return;

        attack(target);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || target == null) return;

        event.renderer.box(target.getBoundingBox(), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    private Player findTarget() {
        if (targetMode.get() == TargetMode.Crosshair) {
            if (mc.crosshairPickEntity instanceof Player player && isValidTarget(player)) return player;
            return null;
        }

        Player best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 look = mc.player.getViewVector(1.0F);
        double fovCos = Math.cos(Math.toRadians(fov.get()));

        for (Player player : mc.level.players()) {
            if (!isValidTarget(player)) continue;

            Vec3 direction = player.getEyePosition().subtract(mc.player.getEyePosition());
            if (direction.lengthSqr() < 1.0E-6) continue;

            double dot = look.dot(direction.normalize());
            if (dot < fovCos) continue;

            double score = targetMode.get() == TargetMode.Nearest
                ? mc.player.distanceTo(player)
                : player.getHealth();

            if (score < bestScore) {
                bestScore = score;
                best = player;
            }
        }

        return best;
    }

    private boolean isValidTarget(Player player) {
        if (player == null || player == mc.player) return false;
        if (!player.isAlive() || player.isDeadOrDying()) return false;
        if (player.isSpectator()) return false;
        if (ignoreInvisible.get() && player.isInvisible()) return false;
        if (ignoreFriends.get() && Friends.get().isFriend(player)) return false;
        if (mc.player.distanceTo(player) > range.get()) return false;
        if (onlyTargetOnGround.get() && !player.onGround()) return false;
        if (targetMode.get() != TargetMode.Crosshair && checkLineOfSight.get() && !canSee(player)) return false;
        return true;
    }

    private boolean canSee(Player player) {
        BlockHitResult hit = mc.level.clip(new ClipContext(
            mc.player.getEyePosition(), player.getEyePosition(),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player
        ));

        return hit.getType() == HitResult.Type.MISS;
    }

    private int randomDelay() {
        int min = delayMin.get();
        int max = delayMax.get();
        if (max < min) max = min;
        return min + (int) (Math.random() * (max - min + 1));
    }

    private void attack(Player player) {
        mc.gameMode.attack(mc.player, player);

        if (swing.get()) SwingUtils.swing(InteractionHand.MAIN_HAND);
    }

    public enum TargetMode {
        Crosshair,
        Nearest,
        LowestHealth
    }
}
