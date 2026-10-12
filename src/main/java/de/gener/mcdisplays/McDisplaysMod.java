package de.gener.mcdisplays;

import com.mojang.logging.LogUtils;
import de.gener.mcdisplays.block.CinemaProjectorBlock;
import de.gener.mcdisplays.block.CinemaProjectorBlockEntity;
import de.gener.mcdisplays.block.DisplayPanelBlock;
import de.gener.mcdisplays.block.DisplayPanelBlockEntity;
import de.gener.mcdisplays.item.Ae2CpuMonitorItem;
import de.gener.mcdisplays.item.Ae2CpuMonitorItem.Ae2MonitorData;
import de.gener.mcdisplays.item.CinemaRemoteData;
import de.gener.mcdisplays.item.CinemaRemoteItem;
import de.gener.mcdisplays.item.ColonyDashboardItem;
import de.gener.mcdisplays.item.ColonyDashboardItem.ColonyDashboardData;
import de.gener.mcdisplays.item.MarkdownPadItem;
import de.gener.mcdisplays.item.MarkdownPadItemData.MarkdownPadData;
import de.gener.mcdisplays.menu.CinemaProjectorMenu;
import de.gener.mcdisplays.menu.DisplayPanelMenu;
import de.gener.mcdisplays.network.SaveMarkdownPadPayload;
import de.gener.mcdisplays.network.UpdateCinemaProjectorPayload;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
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
    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);



    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MarkdownPadData>> MARKDOWN_PAD_DATA = DATA_COMPONENTS.registerComponentType(
            "markdown_pad_data",
            builder -> builder
                    .persistent(MarkdownPadData.CODEC)
                    .networkSynchronized(MarkdownPadData.STREAM_CODEC)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ColonyDashboardData>> COLONY_DASHBOARD_DATA = DATA_COMPONENTS.registerComponentType(
            "colony_dashboard_data",
            builder -> builder
                    .persistent(ColonyDashboardData.CODEC)
                    .networkSynchronized(ColonyDashboardData.STREAM_CODEC)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Ae2MonitorData>> AE2_MONITOR_DATA = DATA_COMPONENTS.registerComponentType(
            "ae2_monitor_data",
            builder -> builder
                    .persistent(Ae2MonitorData.CODEC)
                    .networkSynchronized(Ae2MonitorData.STREAM_CODEC)
    );

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CinemaRemoteData>> CINEMA_REMOTE_DATA = DATA_COMPONENTS.registerComponentType(
            "cinema_remote_data",
            builder -> builder
                    .persistent(CinemaRemoteData.CODEC)
                    .networkSynchronized(CinemaRemoteData.STREAM_CODEC)
    );

    public static final DeferredBlock<DisplayPanelBlock> DISPLAY_PANEL = BLOCKS.register("display_panel",
        () -> new DisplayPanelBlock());
    public static final DeferredItem<Item> DISPLAY_PANEL_ITEM = ITEMS.register(
        "display_panel",
        () -> new BlockItem(DISPLAY_PANEL.get(), new Item.Properties())
    );
    public static final DeferredBlock<CinemaProjectorBlock> CINEMA_PROJECTOR = BLOCKS.register("cinema_projector",
        () -> new CinemaProjectorBlock());
    public static final DeferredItem<Item> CINEMA_PROJECTOR_ITEM = ITEMS.register(
        "cinema_projector",
        () -> new BlockItem(CINEMA_PROJECTOR.get(), new Item.Properties())
    );
    public static final DeferredItem<CinemaRemoteItem> CINEMA_REMOTE = ITEMS.register(
        "cinema_remote",
        () -> new CinemaRemoteItem(new Item.Properties())
    );
    public static final DeferredItem<MarkdownPadItem> MARKDOWN_PAD = ITEMS.register(
        "markdown_pad",
        MarkdownPadItem::new
    );
    public static final DeferredItem<ColonyDashboardItem> COLONY_DASHBOARD = ITEMS.register(
        "colony_dashboard",
        ColonyDashboardItem::new
    );
    public static final DeferredItem<Ae2CpuMonitorItem> AE2_CPU_MONITOR = ITEMS.register(
        "ae2_cpu_monitor",
        Ae2CpuMonitorItem::new
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisplayPanelBlockEntity>> DISPLAY_PANEL_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
        "display_panel",
        () -> BlockEntityType.Builder.of(DisplayPanelBlockEntity::new, DISPLAY_PANEL.get()).build(null)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CinemaProjectorBlockEntity>> CINEMA_PROJECTOR_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
        "cinema_projector",
        () -> BlockEntityType.Builder.of(CinemaProjectorBlockEntity::new, CINEMA_PROJECTOR.get()).build(null)
    );
    public static final DeferredHolder<MenuType<?>, MenuType<DisplayPanelMenu>> DISPLAY_PANEL_MENU = MENU_TYPES.register(
        "display_panel",
        () -> IMenuTypeExtension.create((containerId, playerInventory, data) -> new DisplayPanelMenu(containerId, playerInventory, data.readBlockPos()))
    );
    public static final DeferredHolder<MenuType<?>, MenuType<CinemaProjectorMenu>> CINEMA_PROJECTOR_MENU = MENU_TYPES.register(
        "cinema_projector",
        () -> IMenuTypeExtension.create((containerId, playerInventory, data) -> new CinemaProjectorMenu(containerId, playerInventory, data.readBlockPos()))
    );
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register(
        MODID,
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MODID))
            .icon(() -> new ItemStack(DISPLAY_PANEL_ITEM.get()))
            .displayItems((parameters, output) -> {
                output.accept(DISPLAY_PANEL_ITEM.get());
                output.accept(CINEMA_PROJECTOR_ITEM.get());
                output.accept(CINEMA_REMOTE.get());
                output.accept(MARKDOWN_PAD.get());
                output.accept(COLONY_DASHBOARD.get());
                output.accept(AE2_CPU_MONITOR.get());
            })
            .build()
    );

    public McDisplaysMod(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);


        modEventBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            PayloadRegistrar registrar = event.registrar(MODID);
            registrar.playToServer(SaveMarkdownPadPayload.TYPE, SaveMarkdownPadPayload.STREAM_CODEC, SaveMarkdownPadPayload::handle);
            registrar.playToServer(UpdateCinemaProjectorPayload.TYPE, UpdateCinemaProjectorPayload.STREAM_CODEC, UpdateCinemaProjectorPayload::handle);
        });

        modContainer.registerConfig(ModConfig.Type.SERVER, McDisplaysConfig.SPEC);
    }

    public static ResourceLocation modLocation(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}