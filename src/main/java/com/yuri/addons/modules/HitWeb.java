package com.yuri.addons.modules;

import com.yuri.addons.YuriAddons;
import com.yuri.addons.utils.ScreenUtils;
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
import meteordevelopment.meteorclient.utils.world.BlockUtils;
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
        .defaultValue(35.0)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-ms")
        .description("Minimum time between hit-triggered webs in milliseconds.")
        .defaultValue(250)
        .min(0)
        .sliderRange(0, 2000)
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
        .defaultValue(false)
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
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyTargetOnGround = sgTargeting.add(new BoolSetting.Builder()
        .name("only-target-on-ground")
        .description("Only web players who are standing on the ground.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> checkLineOfSight = sgTargeting.add(new BoolSetting.Builder()
        .name("check-line-of-sight")
        .description("Only place webs that are visible from your eyes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<WebPosition> webPosition = sgPlacement.add(new EnumSetting.Builder<WebPosition>()
        .name("web-position")
        .description("Where to place the web relative to the target.")
        .defaultValue(WebPosition.Feet)
        .build()
    );

    private final Setting<Boolean> airPlace = sgPlacement.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Allow placing webs without a solid block next to the position.")
        .defaultValue(false)
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
        .description("Maximum distance from your eyes to the web position.")
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
    }

    @Override
    public void onDeactivate() {
        renderPos = null;
        renderUntil = 0;
    }

    @Override
    public String getInfoString() {
        return "Ready";
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tick++;

        if (renderPos != null && tick > renderUntil) renderPos = null;
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onAttack(AttackEntityEvent event) {
        if (!Utils.canUpdate() || event.isCancelled()) return;
        if (pauseInGui.get() && ScreenUtils.isOpen()) return;
        if (!(event.entity instanceof Player target) || target == mc.player) return;
        if (!isValidTarget(target)) return;
        if (Math.random() * 100.0 >= chance.get()) return;

        long now = System.currentTimeMillis();
        if (now - lastHit < cooldown.get()) return;
        lastHit = now;

        List<BlockPos> positions = WebUtils.getPlacePositions(
            target, webPosition.get(), predictMovement.get(), predictionTicks.get(),
            airPlace.get(), checkEntities.get(), placeRange.get(), checkLineOfSight.get()
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

        BlockPos pos = positions.get(0);

        if (BlockUtils.place(pos, web, rotate.get(), rotationPriority.get(), true, checkEntities.get(), swapBack.get())) {
            renderPos = pos;
            renderUntil = tick + renderTime.get();

            if (notify.get()) info("Webbed %s.", target.getName().getString());
        } else {
            fail("Could not place a web.");
        }
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
}
