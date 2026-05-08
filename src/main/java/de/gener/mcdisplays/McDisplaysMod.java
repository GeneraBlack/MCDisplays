package de.gener.mcdisplays;

import com.mojang.logging.LogUtils;
import de.gener.mcdisplays.block.DisplayPanelBlock;
import de.gener.mcdisplays.block.DisplayPanelBlockEntity;
import de.gener.mcdisplays.menu.DisplayPanelMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(McDisplaysMod.MODID)
public final class McDisplaysMod {
    public static final String MODID = "mcdisplays";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<DisplayPanelBlock> DISPLAY_PANEL = BLOCKS.register("display_panel", () -> new DisplayPanelBlock());
    public static final DeferredItem<BlockItem> DISPLAY_PANEL_ITEM = ITEMS.register(
        "display_panel",
        () -> new BlockItem(DISPLAY_PANEL.get(), new Item.Properties())
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisplayPanelBlockEntity>> DISPLAY_PANEL_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
        "display_panel",
        () -> BlockEntityType.Builder.of(DisplayPanelBlockEntity::new, DISPLAY_PANEL.get()).build(null)
    );
    public static final DeferredHolder<MenuType<?>, MenuType<DisplayPanelMenu>> DISPLAY_PANEL_MENU = MENU_TYPES.register(
        "display_panel",
        () -> IMenuTypeExtension.create((containerId, playerInventory, data) -> new DisplayPanelMenu(containerId, playerInventory, data.readBlockPos()))
    );
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register(
        MODID,
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MODID))
            .icon(() -> new ItemStack(DISPLAY_PANEL_ITEM.get()))
            .displayItems((parameters, output) -> output.accept(DISPLAY_PANEL_ITEM.get()))
            .build()
    );

    public McDisplaysMod(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, McDisplaysConfig.SPEC);
    }
}