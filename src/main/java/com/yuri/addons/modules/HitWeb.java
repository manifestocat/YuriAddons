package com.yuri.addons.modules;

import com.yuri.addons.YuriAddons;
import com.yuri.addons.utils.InteractionUtils;
import com.yuri.addons.utils.ScreenUtils;
import com.yuri.addons.utils.WebPlacement;
import com.yuri.addons.utils.WebUtils;
import com.yuri.addons.utils.WebUtils.WebPosition;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
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
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.List;

public class HitWeb extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgPlacement = settings.createGroup("Placement");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> chance = sgGeneral.add(new DoubleSetting.Builder()
        .name("chance")
        .description("Chance percentage that a hit places a web.")
        .defaultValue(100.0)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-ms")
        .description("Minimum time between hit-triggered webs in milliseconds.")
        .defaultValue(300)
        .min(0)
        .sliderRange(0, 2000)
        .build()
    );

    private final Setting<Integer> reactionDelayMin = sgGeneral.add(new IntSetting.Builder()
        .name("reaction-delay-min")
        .description("Minimum human-like delay after a hit before placing in milliseconds.")
        .defaultValue(30)
        .min(0)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Integer> reactionDelayMax = sgGeneral.add(new IntSetting.Builder()
        .name("reaction-delay-max")
        .description("Maximum human-like delay after a hit before placing in milliseconds.")
        .defaultValue(90)
        .min(0)
        .sliderRange(0, 500)
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
        .description("Show a chat message when a hit web is placed.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notifyFailures = sgGeneral.add(new BoolSetting.Builder()
        .name("notify-failures")
        .description("Show a chat message when a hit web could not be placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Do not web friends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreInvisible = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-invisible")
        .description("Do not web invisible players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyTargetOnGround = sgTargeting.add(new BoolSetting.Builder()
        .name("only-target-on-ground")
        .description("Only web players who are standing on the ground.")
        .defaultValue(false)
        .build()
    );

    private final Setting<WebPosition> webPosition = sgPlacement.add(new EnumSetting.Builder<WebPosition>()
        .name("web-position")
        .description("Where to place the web relative to the target.")
        .defaultValue(WebPosition.Feet)
        .build()
    );

    private final Setting<Boolean> checkEntities = sgPlacement.add(new BoolSetting.Builder()
        .name("check-entities")
        .description("Do not place webs inside entities.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> predictMovement = sgPlacement.add(new BoolSetting.Builder()
        .name("predict-movement")
        .description("Predict the target's movement before choosing the web position.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> predictionTicks = sgPlacement.add(new IntSetting.Builder()
        .name("prediction-ticks")
        .description("How many ticks ahead to predict.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 10)
        .visible(predictMovement::get)
        .build()
    );

    private final Setting<Double> placeRange = sgPlacement.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Maximum distance from your eyes to the web position, capped at the vanilla block interaction range.")
        .defaultValue(4.5)
        .min(0)
        .sliderRange(0, 6)
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
        .description("Render the last placed web position.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> renderTime = sgRender.add(new IntSetting.Builder()
        .name("render-time")
        .description("Ticks to keep rendering the last web position.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 100)
        .visible(render::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the web box is rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Side color of the web box.")
        .defaultValue(new SettingColor(180, 220, 255, 35))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Line color of the web box.")
        .defaultValue(new SettingColor(180, 220, 255, 255))
        .visible(render::get)
        .build()
    );

    private BlockPos renderPos;
    private long renderUntil;
    private long tick;
    private long lastHit;
    private PendingHit pending;

    public HitWeb() {
        super(YuriAddons.CATEGORY, "hit-web",
            "Places a web on a player immediately after you hit them.",
            "hitweb", "web-hit", "hit-web-plus");
    }

    @Override
    public void onActivate() {
        renderPos = null;
        renderUntil = 0;
        tick = 0;
        lastHit = 0;
        pending = null;
    }

    @Override
    public void onDeactivate() {
        renderPos = null;
        renderUntil = 0;
        cancelPending(true);
    }

    @Override
    public String getInfoString() {
        return "Ready";
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tick++;
        handlePending();

        if (renderPos != null && tick > renderUntil) renderPos = null;
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onAttack(AttackEntityEvent event) {
        if (!Utils.canUpdate() || event.isCancelled()) return;
        if (pending != null) return;
        if (pauseInGui.get() && ScreenUtils.isOpen()) return;
        if (!(event.entity instanceof Player target) || target == mc.player) return;
        if (!isValidTarget(target)) return;
        if (Math.random() * 100.0 >= chance.get()) return;

        long now = System.currentTimeMillis();
        if (now - lastHit < cooldown.get()) return;
        lastHit = now;

        startHitWeb(target);
    }

    private void startHitWeb(Player target) {
        if (InteractionUtils.isPlayerInWeb()) return;

        List<BlockPos> positions = WebUtils.getPlacePositions(
            target, webPosition.get(), predictMovement.get(), predictionTicks.get(),
            checkEntities.get(), placeRange.get()
        );

        if (positions.isEmpty()) {
            fail("Could not place a web.");
            return;
        }

        FindItemResult web = InvUtils.findInHotbar(Items.COBWEB);
        if (!web.found()) {
            fail("No cobwebs in your hotbar or offhand.");
            return;
        }

        int originalSlot = -1;
        int swapTicks = 0;

        if (web.isHotbar() && !web.isMainHand()) {
            originalSlot = mc.player.getInventory().getSelectedSlot();

            if (legitSwap.get() && InvUtils.swap(web.slot(), false)) {
                swapTicks = Math.max(1, swapDelay.get());
            }
        }

        int reactionTicks = (randomReactionDelay() + 49) / 50;
        pending = new PendingHit(target, originalSlot, tick + reactionTicks + swapTicks);
    }

    private void handlePending() {
        if (pending == null) return;

        if (pauseInGui.get() && ScreenUtils.isOpen()) {
            cancelPending(true);
            return;
        }

        if (!pending.placed) {
            if (tick < pending.placeAtTick) return;

            if (tick - pending.placeAtTick > 100 || !Utils.canUpdate() || !pending.target.isAlive()) {
                cancelPending(true);
                return;
            }

            if (pending.scheduled) return;

            List<BlockPos> positions = WebUtils.getPlacePositions(
                pending.target, webPosition.get(), predictMovement.get(), predictionTicks.get(),
                checkEntities.get(), placeRange.get()
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

            BlockPos pos = positions.get(0);
            PendingHit task = pending;

            if (!WebPlacement.place(pos, web, rotate.get(), rotationPriority.get(), checkEntities.get(), placeRange.get(),
                () -> pending == task && Utils.canUpdate() && task.target.isAlive(),
                success -> {
                    if (pending != task) return;

                    if (success) {
                        task.placed = true;
                        task.restoreAtTick = tick + Math.max(0, swapBackDelay.get());
                        renderPos = pos;
                        renderUntil = tick + renderTime.get();

                        if (notify.get()) info("Webbed %s.", task.target.getName().getString());
                    } else {
                        cancelPending(true);
                        fail("Could not place a web.");
                    }
                })) {
                task.scheduled = true;
            } else {
                cancelPending(true);
                fail("Could not place a web.");
            }

            return;
        }

        if (tick >= pending.restoreAtTick) {
            if (swapBack.get() && pending.originalSlot >= 0) InvUtils.swap(pending.originalSlot, false);
            pending = null;
        }
    }

    private int randomReactionDelay() {
        int min = reactionDelayMin.get();
        int max = reactionDelayMax.get();
        if (max < min) max = min;
        return min + (int) (Math.random() * (max - min + 1));
    }

    private void cancelPending(boolean restore) {
        if (pending == null) return;

        if (restore && pending.originalSlot >= 0 && mc.player != null) {
            InvUtils.swap(pending.originalSlot, false);
        }

        pending = null;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || renderPos == null) return;

        event.renderer.box(renderPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    private boolean isValidTarget(Player target) {
        if (!target.isAlive() || target.isDeadOrDying() || target.isSpectator()) return false;
        if (ignoreInvisible.get() && target.isInvisible()) return false;
        if (ignoreFriends.get() && Friends.get().isFriend(target)) return false;
        return !onlyTargetOnGround.get() || target.onGround();
    }

    private void fail(String message) {
        if (notifyFailures.get()) warning(message);
    }

    private static final class PendingHit {
        final Player target;
        final int originalSlot;
        final long placeAtTick;
        long restoreAtTick;
        boolean placed;
        boolean scheduled;

        PendingHit(Player target, int originalSlot, long placeAtTick) {
            this.target = target;
            this.originalSlot = originalSlot;
            this.placeAtTick = placeAtTick;
        }
    }
}
