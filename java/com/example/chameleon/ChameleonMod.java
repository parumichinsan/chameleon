package com.example.chameleon;

import com.example.chameleon.item.ChameleonItem;
import com.example.chameleon.event.ChameleonHandler;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(ChameleonMod.MOD_ID)
public final class ChameleonMod {
    public static final String MOD_ID = "chameleon";

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final RegistryObject<Item> CHAMELEON =
            ITEMS.register("chameleon", () -> new ChameleonItem(new Item.Properties()));

    public ChameleonMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ITEMS.register(modEventBus);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ChameleonHandler());
        CREATIVE_TABS.register(modEventBus);
        CREATIVE_TABS.register("chameleon", () -> CreativeModeTab.builder()
                .title(net.minecraft.network.chat.Component.literal("Chameleon"))
                .icon(() -> CHAMELEON.get().getDefaultInstance())
                .displayItems((parameters, output) -> output.accept(CHAMELEON.get()))
                .build());
    }
}
