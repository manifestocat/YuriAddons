package com.yuri.addons.modules;

import com.yuri.addons.YuriAddons;
import com.yuri.addons.utils.ScreenUtils;
import com.yuri.addons.utils.WebUtils;
import com.yuri.addons.utils.WebUtils.WebPosition;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutoWebPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgPlacement = settings.createGroup("Placement");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Keybind> triggerKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("trigger-key")
        .description("Key that places a web under the target. Enable the module once, then press this key.")
        .defaultValue(Keybind.none())
        .action(this::trigger)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-ms")
        .description("Minimum time between web placements in milliseconds.")
        .defaultValue(150)
        .min(0)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Boolean> pauseInGui = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-in-gui")
        .description("Do not place webs while a screen is open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Show a chat message when a web is placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifyFailures = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-failures")
        .description("Show a chat message when the target cannot be webbed.")
        .defaultValue(false)
        .build()
    );

    private final Setting<TargetMode> targetMode = sgTargeting.add(new EnumSetting.Builder<TargetMode>()
        .name("target-mode")
        .description("How the target is selected. Crosshair uses the player closest to your view.")
        .defaultValue(TargetMode.Crosshair)
        .build()
    );

    private final Setting<Double> targetRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum distance to target a player.")
        .defaultValue(6.0)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Double> crosshairFov = sgTargeting.add(new DoubleSetting.Builder()
        .name("crosshair-fov")
        .description("Maximum angle from your crosshair for crosshair targeting.")
        .defaultValue(25.0)
        .min(1)
        .sliderRange(1, 90)
        .visible(() -> targetMode.get() == TargetMode.Crosshair)
        .build()
    );

    private final Setting<Boolean> fallbackNearest = sgTargeting.add(new BoolSetting.Builder()
        .name("fallback-nearest")
        .description("Use the nearest enemy if nothing is within the crosshair FOV.")
        .defaultValue(true)
        .visible(() -> targetMode.get() == TargetMode.Crosshair)
        .build()
    );

    private final Setting<Boolean> ignoreInvisible = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-invisible")
        .description("Do not target invisible players.")
        .defaultValue(false)
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

    private final Setting<WebPosition> webPosition = sgPlacement.add(new EnumSetting.Builder<WebPosition>()
        .name("web-position")
        .description("Where to place the web relative to the target.")
        .defaultValue(WebPosition.Feet)
        .build()
    );

    private final Setting<Boolean> predictMovement = sgPlacement.add(new BoolSetting.Builder()
        .name("predict-movement")
        .description("Predict the target's movement before choosing the web position.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> predictionTicks = sgPlacement.add(new IntSetting.Builder()
        .name("prediction-ticks")
        .description("How many ticks ahead to predict.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 10)
        .visible(predictMovement::get)
        .build()
    );

    private final Setting<Double> placeRange = sgPlacement.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Maximum distance from your eyes to the web position.")
        .defaultValue(4.5)
        .min(0)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Boolean> checkLineOfSight = sgPlacement.add(new BoolSetting.Builder()
        .name("check-line-of-sight")
        .description("Only place webs that are visible from your eyes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> checkEntities = sgPlacement.add(new BoolSetting.Builder()
        .name("check-entities")
        .description("Do not place webs inside entities. Usually off so the web can be placed in the target's hitbox.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> airPlace = sgPlacement.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Allow placing webs without a solid block next to the position.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rotate = sgPlacement.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Silently rotate towards the web position.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> rotationPriority = sgPlacement.add(new IntSetting.Builder()
        .name("rotation-priority")
        .description("Rotation priority when other modules are also rotating.")
        .defaultValue(50)
        .sliderRange(-100, 100)
        .visible(rotate::get)
        .build()
    );

    private final Setting<Boolean> swapBack = sgPlacement.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Return to the previously selected hotbar slot after placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> legitSwap = sgPlacement.add(new BoolSetting.Builder()
        .name("legit-swap")
        .description("Visibly select the web hotbar slot before placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> swapDelay = sgPlacement.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("Ticks to hold the web slot before placing.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 10)
        .visible(legitSwap::get)
        .build()
    );

    private final Setting<Integer> swapBackDelay = sgPlacement.add(new IntSetting.Builder()
        .name("swap-back-delay")
        .description("Ticks to wait after placing before swapping back.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 10)
        .visible(() -> legitSwap.get() && swapBack.get())
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Render the current web position.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the preview box is rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Side color of the preview box.")
        .defaultValue(new SettingColor(220, 190, 255, 40))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Line color of the preview box.")
        .defaultValue(new SettingColor(220, 190, 255, 255))
        .visible(render::get)
        .build()
    );

    private final List<Player> candidates = new ArrayList<>();
    private Player previewTarget;
    private BlockPos previewPos;
    private PendingWeb pending;
    private long lastTrigger;
    private long tick;

    public AutoWebPlus() {
        super(YuriAddons.CATEGORY, "auto-web-plus",
            "Places a web under the target when you press the trigger key.",
            "web-plus", "webplus", "autowebplus");
    }

    @Override
    public void onActivate() {
        candidates.clear();
        previewTarget = null;
        previewPos = null;
        pending = null;
        lastTrigger = 0;
        tick = 0;

        if (!triggerKey.get().isSet()) warning("Set trigger-key in Auto Web Plus settings.");
    }

    @Override
    public void onDeactivate() {
        candidates.clear();
        previewTarget = null;
        previewPos = null;
        cancelPending(true);
    }

    @Override
    public String getInfoString() {
        return previewTarget != null ? previewTarget.getName().getString() : "Ready";
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tick++;
        updatePreview();
        handlePending();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || previewPos == null) return;

        event.renderer.box(previewPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    private void trigger() {
        if (!Utils.canUpdate()) return;
        if (pending != null) return;
        if (pauseInGui.get() && ScreenUtils.isOpen()) return;

        long now = System.currentTimeMillis();
        if (now - lastTrigger < cooldown.get()) return;
        lastTrigger = now;

        List<Player> targets = collectTargets();
        if (targets.isEmpty()) {
            fail("No target found.");
            return;
        }

        if (!InvUtils.findInHotbar(Items.COBWEB).found()) {
            fail("No cobwebs in your hotbar or offhand.");
            return;
        }

        for (Player target : targets) {
            if (placeOnTarget(target)) return;
        }

        fail("Could not place a web.");
    }

    private boolean placeOnTarget(Player target) {
        List<BlockPos> positions = WebUtils.getPlacePositions(
            target, webPosition.get(), predictMovement.get(), predictionTicks.get(),
            airPlace.get(), checkEntities.get(), placeRange.get(), checkLineOfSight.get()
        );
        if (positions.isEmpty()) return false;

        BlockPos pos = positions.get(0);
        FindItemResult web = InvUtils.findInHotbar(Items.COBWEB);
        if (!web.found()) return false;

        if (legitSwap.get() && web.isHotbar() && !web.isMainHand()) {
            int originalSlot = mc.player.getInventory().getSelectedSlot();

            if (InvUtils.swap(web.slot(), false)) {
                pending = new PendingWeb(target, originalSlot, tick + Math.max(1, swapDelay.get()));
                return true;
            }
        }

        if (BlockUtils.place(pos, web, rotate.get(), rotationPriority.get(), true, checkEntities.get(), swapBack.get())) {
            if (notify.get()) info("Webbed %s.", target.getName().getString());
            return true;
        }

        return false;
    }

    private void handlePending() {
        if (pending == null) return;

        if (pauseInGui.get() && ScreenUtils.isOpen()) {
            cancelPending(true);
            return;
        }

        if (!pending.placed) {
            if (tick < pending.placeAtTick) return;

            if (!Utils.canUpdate() || !pending.target.isAlive()) {
                cancelPending(true);
                return;
            }

            List<BlockPos> positions = WebUtils.getPlacePositions(
                pending.target, webPosition.get(), predictMovement.get(), predictionTicks.get(),
                airPlace.get(), checkEntities.get(), placeRange.get(), checkLineOfSight.get()
            );
            if (positions.isEmpty()) {
                cancelPending(true);
                fail("Could not place a web.");
                return;
            }

            FindItemResult web = InvUtils.findInHotbar(Items.COBWEB);
            if (!web.found()) {
                cancelPending(true);
                fail("No cobwebs in your hotbar or offhand.");
                return;
            }

            if (BlockUtils.place(positions.get(0), web, rotate.get(), rotationPriority.get(), true, checkEntities.get(), false)) {
                pending.placed = true;
                pending.restoreAtTick = tick + Math.max(0, swapBackDelay.get());

                if (notify.get()) info("Webbed %s.", pending.target.getName().getString());
            } else {
                cancelPending(true);
                fail("Could not place a web.");
            }

            return;
        }

        if (tick >= pending.restoreAtTick) {
            if (swapBack.get()) InvUtils.swap(pending.originalSlot, false);
            pending = null;
        }
    }

    private void cancelPending(boolean restore) {
        if (pending == null) return;

        if (restore && pending.originalSlot >= 0 && mc.player != null) {
            InvUtils.swap(pending.originalSlot, false);
        }

        pending = null;
    }

    private void updatePreview() {
        previewTarget = null;
        previewPos = null;

        if (!render.get() || !Utils.canUpdate()) return;

        List<Player> targets = collectTargets();

        for (Player target : targets) {
            List<BlockPos> positions = WebUtils.getPlacePositions(
                target, webPosition.get(), predictMovement.get(), predictionTicks.get(),
                airPlace.get(), checkEntities.get(), placeRange.get(), checkLineOfSight.get()
            );
            if (!positions.isEmpty()) {
                previewTarget = target;
                previewPos = positions.get(0);
                return;
            }
        }
    }

    private List<Player> collectTargets() {
        candidates.clear();

        double fovCos = Math.cos(Math.toRadians(crosshairFov.get()));

        for (Player player : mc.level.players()) {
            if (!isValidTarget(player)) continue;
            if (targetMode.get() == TargetMode.Crosshair && angleScore(player) < fovCos) continue;

            candidates.add(player);
        }

        boolean fallback = false;
        if (candidates.isEmpty() && targetMode.get() == TargetMode.Crosshair && fallbackNearest.get()) {
            fallback = true;

            for (Player player : mc.level.players()) {
                if (isValidTarget(player)) candidates.add(player);
            }
        }

        candidates.sort(fallback ? Comparator.comparingDouble(mc.player::distanceTo) : comparator());
        return candidates;
    }

    private Comparator<Player> comparator() {
        return switch (targetMode.get()) {
            case Crosshair -> Comparator.comparingDouble((Player player) -> -angleScore(player));
            case Nearest -> Comparator.comparingDouble(mc.player::distanceTo);
            case LowestHealth -> Comparator.comparingDouble(Player::getHealth);
        };
    }

    private boolean isValidTarget(Player player) {
        if (player == null || player == mc.player) return false;
        if (!player.isAlive() || player.isDeadOrDying()) return false;
        if (player.isSpectator()) return false;
        if (ignoreInvisible.get() && player.isInvisible()) return false;
        if (ignoreFriends.get() && Friends.get().isFriend(player)) return false;
        if (mc.player.distanceTo(player) > targetRange.get()) return false;
        return !onlyTargetOnGround.get() || player.onGround();
    }

    private double angleScore(Player player) {
        Vec3 direction = player.getEyePosition().subtract(mc.player.getEyePosition());
        if (direction.lengthSqr() < 1.0E-6) return 1.0;

        return mc.player.getViewVector(1.0F).dot(direction.normalize());
    }

    private void fail(String message) {
        if (notifyFailures.get()) warning(message);
    }

    private static final class PendingWeb {
        final Player target;
        final int originalSlot;
        final long placeAtTick;
        long restoreAtTick;
        boolean placed;

        PendingWeb(Player target, int originalSlot, long placeAtTick) {
            this.target = target;
            this.originalSlot = originalSlot;
            this.placeAtTick = placeAtTick;
        }
    }

    public enum TargetMode {
        Crosshair,
        Nearest,
        LowestHealth
    }
}
