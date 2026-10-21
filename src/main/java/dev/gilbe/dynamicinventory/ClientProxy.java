package dev.gilbe.dynamicinventory;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import dev.gilbe.dynamicinventory.client.BaselineClientDiagnostics;
import dev.gilbe.dynamicinventory.client.ClientGateAccess;
import dev.gilbe.dynamicinventory.client.InventoryGuiTheme;
import dev.gilbe.dynamicinventory.client.InventoryViewSettings;
import dev.gilbe.dynamicinventory.client.PlayerInventoryEvents;
import dev.gilbe.dynamicinventory.client.editor.InventoryLayoutEditor;
import dev.gilbe.dynamicinventory.client.editor.NativeLayoutPatches;
import dev.gilbe.dynamicinventory.compat.PackClientCompatibility;
import dev.gilbe.dynamicinventory.compat.PackCompatibility;

public class ClientProxy extends CommonProxy {

    @Override
    public net.minecraft.entity.player.InventoryPlayer getClientInventory() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        return player == null ? null : player.inventory;
    }

    @Override
    public dev.gilbe.dynamicinventory.api.NativeUiLayout automaticLayout(net.minecraft.inventory.Container container,
        Class<?> guiClass) {
        return dev.gilbe.dynamicinventory.client.AutomaticScrollPresentation.layoutFor(container, guiClass);
    }

    @Override
    public boolean isClientOpeningReady(EntityPlayer player) {
        Minecraft client = Minecraft.getMinecraft();
        return player == client.thePlayer && client.getNetHandler() != null
            && ((ClientGateAccess) client.getNetHandler()).dynamicinventory$openingGate()
                .allowsInput(player, player.openContainer.windowId);
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        InventoryViewSettings.initialize(new File(event.getModConfigurationDirectory(), "dynamicinventory-client.cfg"));
        NativeLayoutPatches.initialize(new File(event.getModConfigurationDirectory(), "dynamicinventory/ui-layouts"));
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        InventoryGuiTheme.initialize(
            Minecraft.getMinecraft()
                .getResourceManager());
        MinecraftForge.EVENT_BUS.register(new PlayerInventoryEvents());
        InventoryLayoutEditor editor = new InventoryLayoutEditor();
        MinecraftForge.EVENT_BUS.register(editor);
        FMLCommonHandler.instance()
            .bus()
            .register(editor);
        if (Loader.isModLoaded("modularui2")) {
            try {
                Object inputEvents = Class.forName("dev.gilbe.dynamicinventory.client.Mui2PendingInputEvents")
                    .getDeclaredConstructor()
                    .newInstance();
                MinecraftForge.EVENT_BUS.register(inputEvents);
            } catch (ReflectiveOperationException missingOptionalHandler) {
                throw new IllegalStateException(
                    "Could not register the ModularUI2 input guard",
                    missingOptionalHandler);
            }
        }
        FMLCommonHandler.instance()
            .bus()
            .register(new BaselineClientDiagnostics());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        if (PackCompatibility.enabled()) PackClientCompatibility.initialize();
    }

}
