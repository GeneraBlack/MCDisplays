package de.gener.mcdisplays;

import com.mojang.logging.LogUtils;
import de.gener.mcdisplays.block.DisplayPanelBlock;
import de.gener.mcdisplays.block.DisplayPanelBlockEntity;
import de.gener.mcdisplays.client.McDisplaysModClient;
import de.gener.mcdisplays.item.MarkdownPadItem;
import de.gener.mcdisplays.menu.DisplayPanelMenu;
import de.gener.mcdisplays.network.SaveMarkdownPadPayload;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(McDisplaysMod.MODID)
public final class McDisplaysMod {
    public static final String MODID = "mcdisplays";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final String NETWORK_PROTOCOL_VERSION = "1";

    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
        modLocation("main"),
        () -> NETWORK_PROTOCOL_VERSION,
        NETWORK_PROTOCOL_VERSION::equals,
        NETWORK_PROTOCOL_VERSION::equals
    );

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<DisplayPanelBlock> DISPLAY_PANEL = BLOCKS.register("display_panel", DisplayPanelBlock::new);
    public static final RegistryObject<Item> DISPLAY_PANEL_ITEM = ITEMS.register(
        "display_panel",
        () -> new BlockItem(DISPLAY_PANEL.get(), new Item.Properties())
    );
    public static final RegistryObject<MarkdownPadItem> MARKDOWN_PAD = ITEMS.register(
        "markdown_pad",
        MarkdownPadItem::new
    );
    public static final RegistryObject<BlockEntityType<DisplayPanelBlockEntity>> DISPLAY_PANEL_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
        "display_panel",
        () -> BlockEntityType.Builder.of(DisplayPanelBlockEntity::new, DISPLAY_PANEL.get()).build(null)
    );
    public static final RegistryObject<MenuType<DisplayPanelMenu>> DISPLAY_PANEL_MENU = MENU_TYPES.register(
        "display_panel",
        () -> IForgeMenuType.create((containerId, playerInventory, data) -> new DisplayPanelMenu(containerId, playerInventory, data.readBlockPos()))
    );
    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register(
        MODID,
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MODID))
            .icon(() -> new ItemStack(DISPLAY_PANEL_ITEM.get()))
            .displayItems((parameters, output) -> {
                output.accept(DISPLAY_PANEL_ITEM.get());
                output.accept(MARKDOWN_PAD.get());
            })
            .build()
    );

    private static int nextPacketId;

    public McDisplaysMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> McDisplaysModClient.init(modEventBus));

        registerMessages();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, McDisplaysConfig.SPEC);
    }

    private static void registerMessages() {
        NETWORK.registerMessage(
            nextPacketId++,
            SaveMarkdownPadPayload.class,
            SaveMarkdownPadPayload::encode,
            SaveMarkdownPadPayload::decode,
            SaveMarkdownPadPayload::handle
        );
    }

    private static ResourceLocation modLocation(String path) {
        return Objects.requireNonNull(ResourceLocation.tryParse(MODID + ":" + path));
    }
}