package com.yuri.addons;

import com.mojang.logging.LogUtils;
import com.yuri.addons.modules.AutoDrain;
import com.yuri.addons.modules.AutoWebPlus;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.function.Supplier;

public class YuriAddons extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    public static final Category CATEGORY = createCategory();

    @Override
    public void onInitialize() {
        LOG.info("Initializing Yuri Addons");

        Modules.get().add(new AutoDrain());
        Modules.get().add(new AutoWebPlus());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.yuri.addons";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("manifestocat", "YuriAddons");
    }

    @SuppressWarnings("unchecked")
    private static Category createCategory() {
        ItemStack icon = new ItemStack(Items.WATER_BUCKET);

        try {
            return Category.class.getConstructor(String.class, Supplier.class)
                .newInstance("Yuri Addons", (Supplier<ItemStack>) () -> icon);
        } catch (ReflectiveOperationException e) {
            try {
                return Category.class.getConstructor(String.class, ItemStack.class)
                    .newInstance("Yuri Addons", icon);
            } catch (ReflectiveOperationException ex) {
                return new Category("Yuri Addons");
            }
        }
    }
}
