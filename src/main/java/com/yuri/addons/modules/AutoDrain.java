package com.yuri.addons.modules;

import com.yuri.addons.YuriAddons;
import com.yuri.addons.utils.InteractionUtils;
import com.yuri.addons.utils.ScreenUtils;
import meteordevelopment.meteorclient.events.entity.player.InteractItemEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
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
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpongeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AutoDrain extends Module {
    private static final int WARN_INTERVAL = 100;
    private static final double TRACK_DISTANCE_SQ = 32 * 32;
    private static final int SPONGE_PLACE_GRACE = 40;
    private static final int SPONGE_TASK_TIMEOUT = 100;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgPriority = settings.createGroup("Priority");
    private final SettingGroup sgBlock = settings.createGroup("Block");
    private final SettingGroup sgSponge = settings.createGroup("Sponge");
    private final SettingGroup sgRender = settings.createGroup("Render");

    

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How water should be removed. Bucket picks water up, Block replaces it, Web places cobwebs, Sponge absorbs it. Auto tries Bucket, Sponge, Web, then Block.")
        .defaultValue(Mode.Bucket)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("action-delay")
        .description("Ticks between drain actions.")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> actionsPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("actions-per-tick")
        .description("Maximum number of water blocks to handle each tick.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Integer> retryDelay = sgGeneral.add(new IntSetting.Builder()
        .name("retry-delay")
        .description("Ticks to wait before trying the same water block again.")
        .defaultValue(15)
        .min(1)
        .sliderRange(1, 60)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Silently rotates towards the water block before interacting with it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> rotationPriority = sgPriority.add(new IntSetting.Builder()
        .name("rotation-priority")
        .description("Priority used when other modules are also rotating.")
        .defaultValue(50)
        .sliderRange(-100, 100)
        .visible(rotate::get)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Switches back to the previously selected hotbar slot after an action.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseInGui = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-in-gui")
        .description("Stops draining while a screen is open.")
        .defaultValue(true)
        .build()
    );

    

    private final Setting<Boolean> onlyPlayerPlaced = sgTargeting.add(new BoolSetting.Builder()
        .name("only-player-placed")
        .description("Only drain water that appeared while the module was running. Prevents draining lakes and oceans.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyEnemyPlaced = sgTargeting.add(new BoolSetting.Builder()
        .name("only-enemy-placed")
        .description("Only target water placed by an enemy. The placer is estimated from nearby non-friend players (closest, holding a bucket, or looking at the new source).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> adoptExisting = sgTargeting.add(new BoolSetting.Builder()
        .name("adopt-isolated-water")
        .description("On activation, also target isolated source blocks that were placed before the module was enabled.")
        .defaultValue(true)
        .visible(onlyPlayerPlaced::get)
        .build()
    );

    private final Setting<Integer> trackSeconds = sgTargeting.add(new IntSetting.Builder()
        .name("track-seconds")
        .description("How long a player placed water block stays tracked. 0 = forever.")
        .defaultValue(60)
        .min(0)
        .sliderRange(0, 300)
        .visible(onlyPlayerPlaced::get)
        .build()
    );

    private final Setting<Integer> radius = sgTargeting.add(new IntSetting.Builder()
        .name("radius")
        .description("Horizontal radius to search for water.")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> verticalRadius = sgTargeting.add(new IntSetting.Builder()
        .name("vertical-radius")
        .description("Vertical radius to search for water.")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Boolean> customRange = sgTargeting.add(new BoolSetting.Builder()
        .name("custom-range")
        .description("Use a custom maximum interaction range, capped at the vanilla block interaction range.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("Custom interaction range, capped at the vanilla block interaction range.")
        .defaultValue(4.5)
        .min(0)
        .sliderRange(0, 6)
        .visible(customRange::get)
        .build()
    );

    private final Setting<SortMode> sortMode = sgPriority.add(new EnumSetting.Builder<SortMode>()
        .name("sort-mode")
        .description("Target priority. Nearest = closest to you, ClosestEnemy = closest to an enemy, EnemyFeet = water at enemy feet first, Lowest/Highest = Y level.")
        .defaultValue(SortMode.Nearest)
        .build()
    );

    private final Setting<Double> priorityEnemyRange = sgPriority.add(new DoubleSetting.Builder()
        .name("priority-enemy-range")
        .description("How far away an enemy can be and still be used for ClosestEnemy/EnemyFeet priorities.")
        .defaultValue(8.0)
        .min(1)
        .sliderRange(1, 16)
        .visible(() -> sortMode.get() == SortMode.ClosestEnemy || sortMode.get() == SortMode.EnemyFeet)
        .build()
    );

    private final Setting<Boolean> ignoreSelf = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Do not drain water attributed to you. Water with no known owner is also skipped within self-radius. Useful if you use water buckets to MLG.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> selfRadius = sgTargeting.add(new DoubleSetting.Builder()
        .name("self-radius")
        .description("Water this close to you is treated as your own.")
        .defaultValue(1.5)
        .min(0)
        .sliderRange(0, 5)
        .visible(ignoreSelf::get)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Do not drain water placed by friends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> ownerRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("owner-range")
        .description("How close a player must be to a new water block to be considered its owner.")
        .defaultValue(6.0)
        .min(1)
        .sliderRange(1, 12)
        .build()
    );

    

    private final Setting<List<Block>> blocks = sgBlock.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Blocks that block mode (and the block fallback of Auto mode) may use.")
        .defaultValue(Blocks.COBBLESTONE, Blocks.DIRT, Blocks.NETHERRACK, Blocks.COBBLED_DEEPSLATE, Blocks.COBWEB)
        .build()
    );

    private final Setting<Boolean> checkEntities = sgBlock.add(new BoolSetting.Builder()
        .name("check-entities")
        .description("Do not place blocks inside entities, including yourself.")
        .defaultValue(true)
        .build()
    );

    

    private final Setting<Boolean> reclaimSponge = sgSponge.add(new BoolSetting.Builder()
        .name("reclaim-sponge")
        .description("Breaks placed sponges again so you can pick them up.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> reclaimDelay = sgSponge.add(new IntSetting.Builder()
        .name("reclaim-delay")
        .description("Ticks to wait before breaking a placed sponge.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 20)
        .visible(reclaimSponge::get)
        .build()
    );

    

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders the water blocks that are being targeted.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the target boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Side color of the target boxes.")
        .defaultValue(new SettingColor(0, 200, 255, 40))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Line color of the target boxes.")
        .defaultValue(new SettingColor(0, 220, 255, 255))
        .visible(render::get)
        .build()
    );

    private final Setting<Integer> maxRendered = sgRender.add(new IntSetting.Builder()
        .name("max-rendered")
        .description("Maximum number of target boxes to render.")
        .defaultValue(64)
        .min(1)
        .sliderRange(1, 256)
        .visible(render::get)
        .build()
    );

    private final Map<BlockPos, TrackedWater> tracked = new HashMap<>();
    private final Map<BlockPos, Long> retryAt = new HashMap<>();
    private final List<SpongeTask> spongeTasks = new ArrayList<>();
    private final List<BlockPos> renderPositions = new ArrayList<>();

    private long tick;
    private long nextActionTick;
    private long lastWarnTick = -WARN_INTERVAL;
    private long selfWaterTick = -100;
    private BlockPos selfWaterOrigin;
    private ClientLevel lastLevel;

    public AutoDrain() {
        super(YuriAddons.CATEGORY, "auto-drain",
            "Automatically removes water that enemy players place around you.",
            "drain", "water-drain", "autodrain");
    }

    @Override
    public void onActivate() {
        tick = 0;
        nextActionTick = 0;
        lastWarnTick = -WARN_INTERVAL;
        selfWaterTick = -100;
        selfWaterOrigin = null;
        tracked.clear();
        retryAt.clear();
        spongeTasks.clear();
        renderPositions.clear();

        if (!Utils.canUpdate()) return;

        lastLevel = mc.level;

        if (onlyPlayerPlaced.get() && adoptExisting.get()) adoptNearbyWater();

        checkRequiredItems();
    }

    @Override
    public void onDeactivate() {
        tracked.clear();
        retryAt.clear();
        spongeTasks.clear();
        renderPositions.clear();
        lastLevel = null;
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }

    @EventHandler
    private void onInteractItem(InteractItemEvent event) {
        if (mc.player == null) return;

        
        
        
        if (mc.player.getItemInHand(event.hand).is(Items.WATER_BUCKET)) {
            selfWaterTick = tick;
            selfWaterOrigin = mc.player.blockPosition().immutable();
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (!Utils.canUpdate() || event.pos == null) return;

        BlockPos pos = event.pos.immutable();
        BlockState newState = event.newState;

        if (newState == null || !isWaterSource(newState)) {
            tracked.remove(pos);
            return;
        }

        
        
        if (event.oldState != null && isWaterSource(event.oldState)) return;
        if (PlayerUtils.squaredDistanceTo(pos) > TRACK_DISTANCE_SQ) return;

        if (ignoreSelf.get() && isRecentSelfWater(pos)) return;

        Player owner = onlyEnemyPlaced.get()
            ? findEnemyPlacer(pos)
            : nearestPlayer(pos, ownerRange.get());

        
        
        if (onlyEnemyPlaced.get() && owner == null) return;

        tracked.put(pos, new TrackedWater(
            tick,
            owner == null ? null : owner.getName().getString(),
            owner != null && Friends.get().isFriend(owner)
        ));
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!Utils.canUpdate()) return;

        
        if (lastLevel != null && mc.level != lastLevel) {
            tracked.clear();
            retryAt.clear();
            spongeTasks.clear();
            renderPositions.clear();

            if (onlyPlayerPlaced.get() && adoptExisting.get()) adoptNearbyWater();
        }
        lastLevel = mc.level;

        tick++;
        cleanRetries();
        processSpongeTasks();

        List<BlockPos> candidates = collectCandidates();
        updateRenderPositions(candidates);

        if (tick < nextActionTick) return;
        if (pauseInGui.get() && ScreenUtils.isOpen()) return;
        if (candidates.isEmpty()) return;

        int actions = 0;

        for (BlockPos pos : candidates) {
            if (actions >= actionsPerTick.get()) break;
            if (performAction(pos)) {
                retryAt.put(pos, tick + retryDelay.get());
                actions++;
            }
        }

        if (actions > 0) nextActionTick = tick + Math.max(1, delay.get());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || renderPositions.isEmpty()) return;

        for (BlockPos pos : renderPositions) {
            event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    

    private List<BlockPos> collectCandidates() {
        List<BlockPos> candidates = new ArrayList<>();

        if (onlyPlayerPlaced.get()) {
            long expireTicks = trackSeconds.get() * 20L;

            for (Iterator<Map.Entry<BlockPos, TrackedWater>> it = tracked.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<BlockPos, TrackedWater> entry = it.next();
                BlockPos pos = entry.getKey();

                if (!isWaterSource(mc.level.getBlockState(pos))) {
                    it.remove();
                    continue;
                }

                if (expireTicks > 0 && tick - entry.getValue().tick() > expireTicks) {
                    it.remove();
                    continue;
                }

                if (isValidTarget(pos, entry.getValue())) candidates.add(pos);
            }
        } else {
            BlockPos center = mc.player.blockPosition();
            int r = radius.get();
            int vr = verticalRadius.get();

            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > r * r) continue;

                    for (int y = -vr; y <= vr; y++) {
                        BlockPos pos = center.offset(x, y, z);

                        if (!isWaterSource(mc.level.getBlockState(pos))) continue;
                        if (isValidTarget(pos, tracked.get(pos))) candidates.add(pos);
                    }
                }
            }
        }

        candidates.sort(comparator());
        return candidates;
    }

    private boolean isValidTarget(BlockPos pos, TrackedWater trackedWater) {
        if (isOnRetryCooldown(pos)) return false;

        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        if (dx * dx + dz * dz > radius.get() * radius.get()) return false;

        if (Math.abs(pos.getY() - mc.player.blockPosition().getY()) > verticalRadius.get()) return false;

        Vec3 center = Vec3.atCenterOf(pos);
        double reach = Math.min(customRange.get() ? range.get() : mc.player.blockInteractionRange(), mc.player.blockInteractionRange());
        if (mc.player.getEyePosition().distanceToSqr(center) > reach * reach) return false;

        if (!canInteractWith(pos)) return false;

        if (onlyEnemyPlaced.get() && trackedWater == null && nearestEnemy(pos, ownerRange.get()) == null) return false;

        if (ignoreSelf.get()) {
            String selfName = mc.player.getName().getString();

            if (trackedWater != null && trackedWater.owner() != null) {
                if (trackedWater.owner().equals(selfName)) return false;
            } else {
                
                double sx = pos.getX() + 0.5 - mc.player.getX();
                double sy = pos.getY() + 0.5 - mc.player.getY();
                double sz = pos.getZ() + 0.5 - mc.player.getZ();
                double selfRadiusSq = selfRadius.get() * selfRadius.get();

                if (sx * sx + sy * sy + sz * sz <= selfRadiusSq) return false;
            }
        }

        if (ignoreFriends.get()) {
            if (trackedWater != null && trackedWater.friend()) return false;

            if (trackedWater == null || trackedWater.owner() == null) {
                Player nearest = nearestPlayer(pos, ownerRange.get());
                if (nearest != null && Friends.get().isFriend(nearest)) return false;
            }
        }

        return true;
    }

    private Comparator<BlockPos> comparator() {
        return switch (sortMode.get()) {
            case Nearest -> Comparator.comparingDouble(this::playerPriorityDistance)
                .thenComparingDouble((BlockPos pos) -> pos.getY());
            case ClosestEnemy -> Comparator.comparingDouble((BlockPos pos) -> enemyPriorityDistance(pos, false))
                .thenComparingDouble(this::playerPriorityDistance);
            case EnemyFeet -> Comparator.comparingDouble((BlockPos pos) -> enemyPriorityDistance(pos, true))
                .thenComparingDouble(this::playerPriorityDistance);
            case Lowest -> Comparator.comparingDouble((BlockPos pos) -> pos.getY())
                .thenComparingDouble(this::playerPriorityDistance);
            case Highest -> Comparator.comparingDouble((BlockPos pos) -> pos.getY()).reversed()
                .thenComparingDouble(this::playerPriorityDistance);
        };
    }

    private double playerPriorityDistance(BlockPos pos) {
        return mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
    }

    private double enemyPriorityDistance(BlockPos pos, boolean feet) {
        Player enemy = nearestEnemy(pos, priorityEnemyRange.get());
        if (enemy == null) return Double.MAX_VALUE;

        Vec3 target = feet ? enemy.position() : enemy.getEyePosition();
        return target.distanceToSqr(Vec3.atCenterOf(pos));
    }

    private boolean canInteractWith(BlockPos pos) {
        if (InteractionUtils.isPlayerInWeb()) return false;

        double maxRange = customRange.get() ? range.get() : mc.player.blockInteractionRange();

        return switch (mode.get()) {
            case Bucket -> InteractionUtils.canPickupWater(pos, maxRange);
            case Block -> InteractionUtils.canPlaceAt(pos, checkEntities.get(), Blocks.OBSIDIAN, maxRange);
            case Sponge -> InteractionUtils.canPlaceAt(pos, checkEntities.get(), Blocks.SPONGE, maxRange);
            case Web -> InteractionUtils.canPlaceAt(pos, checkEntities.get(), Blocks.COBWEB, maxRange);
            case Auto -> InteractionUtils.canPickupWater(pos, maxRange)
                || InteractionUtils.canPlaceAt(pos, checkEntities.get(), Blocks.SPONGE, maxRange)
                || InteractionUtils.canPlaceAt(pos, checkEntities.get(), Blocks.COBWEB, maxRange)
                || InteractionUtils.canPlaceAt(pos, checkEntities.get(), Blocks.OBSIDIAN, maxRange);
        };
    }

    private Player nearestPlayer(BlockPos pos, double maxDistance) {
        Player nearest = null;
        double nearestDistance = maxDistance * maxDistance;

        for (Player player : mc.level.players()) {
            if (!player.isAlive()) continue;

            double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

            if (distance <= nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }

        return nearest;
    }

    
    private Player findEnemyPlacer(BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        double range = ownerRange.get();
        double rangeSq = range * range;
        Player best = null;
        double bestScore = -Double.MAX_VALUE;

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || Friends.get().isFriend(player)) continue;

            double distanceSq = player.distanceToSqr(center);
            if (distanceSq > rangeSq) continue;

            double score = 2.0 * (1.0 - distanceSq / rangeSq);

            if (isHoldingBucket(player)) score += 1.5;

            Vec3 direction = center.subtract(player.getEyePosition());
            if (direction.lengthSqr() > 1.0E-6) {
                double dot = player.getViewVector(1.0F).dot(direction.normalize());
                if (dot > 0.25) score += dot;
            }

            if (score > bestScore) {
                bestScore = score;
                best = player;
            }
        }

        return best;
    }

    private boolean isRecentSelfWater(BlockPos pos) {
        if (selfWaterOrigin == null || tick - selfWaterTick > 5) return false;
        return pos.distSqr(selfWaterOrigin) <= 36;
    }

    private static boolean isHoldingBucket(Player player) {
        return player.getMainHandItem().is(Items.BUCKET)
            || player.getMainHandItem().is(Items.WATER_BUCKET)
            || player.getOffhandItem().is(Items.BUCKET)
            || player.getOffhandItem().is(Items.WATER_BUCKET);
    }

    private Player nearestEnemy(BlockPos pos, double maxDistance) {
        Player nearest = null;
        double nearestDistance = maxDistance * maxDistance;

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive() || Friends.get().isFriend(player)) continue;

            double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

            if (distance <= nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }

        return nearest;
    }

    private boolean isOnRetryCooldown(BlockPos pos) {
        Long retry = retryAt.get(pos);
        return retry != null && tick < retry;
    }

    private void cleanRetries() {
        if (retryAt.isEmpty()) return;

        retryAt.entrySet().removeIf(entry -> tick >= entry.getValue());
    }

    private void updateRenderPositions(List<BlockPos> candidates) {
        renderPositions.clear();
        if (!render.get()) return;

        int max = maxRendered.get();
        for (int i = 0; i < candidates.size() && i < max; i++) {
            renderPositions.add(candidates.get(i));
        }
    }

    

    private boolean performAction(BlockPos pos) {
        return switch (mode.get()) {
            case Bucket -> drainWithBucket(pos);
            case Block -> drainWithBlock(pos);
            case Web -> drainWithWeb(pos);
            case Sponge -> drainWithSponge(pos);
            case Auto -> drainWithBucket(pos) || drainWithSponge(pos) || drainWithWeb(pos) || drainWithBlock(pos);
        };
    }

    private boolean drainWithBucket(BlockPos pos) {
        FindItemResult emptyBucket = InvUtils.findInHotbar(Items.BUCKET);

        if (!emptyBucket.found()) {
            warn("No empty buckets in your hotbar or offhand.");
            return false;
        }

        Vec3 center = Vec3.atCenterOf(pos);

        
        
        
        
        if (!rotate.get() && !isAimingAt(center)) return false;

        float previousYaw = mc.player.getYRot();
        float previousPitch = mc.player.getXRot();
        InteractionHand hand = emptyBucket.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean swapped = false;
        boolean restoreRotation = rotate.get();

        try {
            if (restoreRotation) {
                mc.player.setYRot((float) Rotations.getYaw(center));
                mc.player.setXRot((float) Rotations.getPitch(center));
            }

            if (hand == InteractionHand.MAIN_HAND) {
                swapped = InvUtils.swap(emptyBucket.slot(), swapBack.get());
                if (!swapped || !mc.player.getMainHandItem().is(Items.BUCKET)) return false;
            } else if (!mc.player.getOffhandItem().is(Items.BUCKET)) {
                return false;
            }

            InteractionResult result = mc.gameMode.useItem(mc.player, hand);
            return result.consumesAction();
        } finally {
            if (swapped && swapBack.get()) InvUtils.swapBack();
            if (restoreRotation) {
                mc.player.setYRot(previousYaw);
                mc.player.setXRot(previousPitch);
            }
        }
    }

    private boolean isAimingAt(Vec3 target) {
        Vec3 direction = target.subtract(mc.player.getEyePosition());
        if (direction.lengthSqr() < 1.0E-6) return true;

        return mc.player.getViewVector(1.0F).dot(direction.normalize()) > 0.98;
    }

    private boolean drainWithBlock(BlockPos pos) {
        FindItemResult item = InvUtils.findInHotbar(this::isAllowedBlock);

        if (!item.found()) {
            warn("No allowed blocks in your hotbar or offhand.");
            return false;
        }

        return BlockUtils.place(pos, item, rotate.get(), rotationPriority.get(), true, checkEntities.get(), swapBack.get());
    }

    private boolean drainWithWeb(BlockPos pos) {
        FindItemResult web = InvUtils.findInHotbar(Items.COBWEB);

        if (!web.found()) {
            warn("No cobwebs in your hotbar or offhand.");
            return false;
        }

        return BlockUtils.place(pos, web, rotate.get(), rotationPriority.get(), true, checkEntities.get(), swapBack.get());
    }

    private boolean drainWithSponge(BlockPos pos) {
        FindItemResult sponge = InvUtils.findInHotbar(Items.SPONGE);

        if (!sponge.found()) {
            warn("No dry sponges in your hotbar or offhand.");
            return false;
        }

        if (!BlockUtils.place(pos, sponge, rotate.get(), rotationPriority.get(), true, checkEntities.get(), swapBack.get())) {
            return false;
        }

        if (reclaimSponge.get()) {
            spongeTasks.add(new SpongeTask(pos, tick + Math.max(1, reclaimDelay.get())));
        }

        return true;
    }

    private boolean isAllowedBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        return blocks.get().contains(blockItem.getBlock());
    }

    private void processSpongeTasks() {
        if (spongeTasks.isEmpty()) return;

        for (Iterator<SpongeTask> it = spongeTasks.iterator(); it.hasNext(); ) {
            SpongeTask task = it.next();
            BlockState state = mc.level.getBlockState(task.pos());

            if (state.isAir()) {
                it.remove();
                continue;
            }

            if (!(state.getBlock() instanceof SpongeBlock)) {
                
                if (tick - task.breakAt() > SPONGE_PLACE_GRACE) it.remove();
                continue;
            }

            if (tick - task.breakAt() > SPONGE_TASK_TIMEOUT) {
                it.remove();
                continue;
            }

            if (tick >= task.breakAt()) BlockUtils.breakBlock(task.pos(), true);
        }
    }

    

    private void adoptNearbyWater() {
        BlockPos center = mc.player.blockPosition();
        int r = radius.get();
        int vr = verticalRadius.get();

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) continue;

                for (int y = -vr; y <= vr; y++) {
                    BlockPos pos = center.offset(x, y, z);

                    if (!isWaterSource(mc.level.getBlockState(pos))) continue;
                    if (isPartOfWaterBody(pos)) continue;

                    Player enemy = nearestEnemy(pos, ownerRange.get());

                    
                    
                    if (onlyEnemyPlaced.get() && enemy == null) continue;

                    tracked.put(pos, new TrackedWater(
                        tick,
                        enemy == null ? null : enemy.getName().getString(),
                        false
                    ));
                }
            }
        }
    }

    private boolean isPartOfWaterBody(BlockPos pos) {
        int sources = 0;

        for (Direction direction : Direction.values()) {
            if (isWaterSource(mc.level.getBlockState(pos.relative(direction)))) {
                sources++;
                if (sources >= 2) return true;
            }
        }

        return false;
    }

    private static boolean isWaterSource(BlockState state) {
        return state.getBlock() == Blocks.WATER && state.getFluidState().isSourceOfType(Fluids.WATER);
    }

    private void checkRequiredItems() {
        switch (mode.get()) {
            case Bucket -> {
                if (!InvUtils.findInHotbar(Items.BUCKET).found()) {
                    warning("No empty buckets in your hotbar or offhand - bucket mode will not work.");
                }
            }
            case Web -> {
                if (!InvUtils.findInHotbar(Items.COBWEB).found()) {
                    warning("No cobwebs in your hotbar or offhand - web mode will not work.");
                }
            }
            case Sponge -> {
                if (!InvUtils.findInHotbar(Items.SPONGE).found()) {
                    warning("No dry sponges in your hotbar or offhand - sponge mode will not work.");
                }
            }
            case Block -> {
                if (!InvUtils.findInHotbar(this::isAllowedBlock).found()) {
                    warning("No allowed blocks in your hotbar or offhand - block mode will not work.");
                }
            }
            case Auto -> {
            }
        }
    }

    private void warn(String message) {
        if (tick - lastWarnTick < WARN_INTERVAL) return;

        lastWarnTick = tick;
        warning(message);
    }

    public enum Mode {
        Bucket,
        Block,
        Web,
        Sponge,
        Auto
    }

    public enum SortMode {
        Nearest,
        ClosestEnemy,
        EnemyFeet,
        Lowest,
        Highest
    }

    private record TrackedWater(long tick, String owner, boolean friend) {
    }

    private record SpongeTask(BlockPos pos, long breakAt) {
    }
}
