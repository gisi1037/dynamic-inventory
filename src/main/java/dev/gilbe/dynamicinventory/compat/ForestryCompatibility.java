package dev.gilbe.dynamicinventory.compat;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import dev.gilbe.dynamicinventory.api.InventoryUi;
import dev.gilbe.dynamicinventory.api.InventoryUiAdapter;
import dev.gilbe.dynamicinventory.api.NativeUiLayout;

/** Exact inventory-bearing Forestry hosts, including the item and paged containers. */
public final class ForestryCompatibility {

    private static final java.util.List<Host> HOSTS = buildHosts();

    private ForestryCompatibility() {}

    private static java.util.List<Host> buildHosts() {
        java.util.List<Host> hosts = new java.util.ArrayList<>();
        hosts.add(machine("core", "Analyzer", 90));
        for (String name : new String[] { "BeeHousing", "Alveary", "AlvearyHygroregulator", "AlvearySieve",
            "AlvearySwarmer" }) hosts.add(machine("apiculture", name, 0));
        for (String name : new String[] { "EngineBiogas", "EngineElectric", "EnginePeat", "Generator" })
            hosts.add(machine("energy", name, 80));
        for (String name : new String[] { "Bottler", "Carpenter", "Centrifuge", "Fabricator", "Fermenter", "Moistener",
            "Raintank", "Squeezer", "Still", "Worktable" }) hosts.add(machine("factory", name, 0));
        hosts.add(machine("farming", "Farm", 0));
        hosts.add(pair("apiculture.gui.ContainerMinecartBeehouse", false, "apiculture.gui.GuiBeeHousing"));
        hosts.add(machine("core", "Escritoire", 0));
        hosts.add(machine("core", "NaturalistInventory", 0));
        hosts.add(
            pair(
                "core.gui.ContainerAlyzer",
                true,
                "apiculture.gui.GuiBeealyzer",
                "arboriculture.gui.GuiTreealyzer",
                "lepidopterology.gui.GuiFlutterlyzer"));
        for (String name : new String[] { "HabitatLocator", "Imprinter" })
            hosts.add(pair("apiculture.gui.Container" + name, true, "apiculture.gui.Gui" + name));
        hosts.add(pair("core.circuits.ContainerSolderingIron", true, "core.circuits.GuiSolderingIron"));
        hosts.add(pair("food.gui.ContainerInfuser", true, "food.gui.GuiInfuser"));
        for (String name : new String[] { "Mailbox", "StampCollector", "Trader" }) hosts.add(machine("mail", name, 0));
        hosts.add(pair("mail.gui.ContainerLetter", true, "mail.gui.GuiLetter"));
        hosts.add(pair("storage.gui.ContainerBackpack", true, "storage.gui.GuiBackpack", "storage.gui.GuiBackpackT2"));
        hosts.add(pair("storage.gui.ContainerNaturalistBackpack", true, "core.gui.GuiNaturalistInventory"));
        return java.util.Collections.unmodifiableList(hosts);
    }

    private static Host machine(String module, String name, int minimumHeader) {
        return new Host(module + ".gui.Container" + name, false, minimumHeader, module + ".gui.Gui" + name);
    }

    private static Host pair(String container, boolean item, String... screens) {
        return new Host(container, item, 0, screens);
    }

    private static final class Host {

        final String id, container;
        final boolean item;
        final String[] screens;
        final NativeUiLayout layout;

        Host(String container, boolean item, int minimumHeader, String... screens) {
            id = "dynamicinventory:forestry/"
                + container.substring(container.lastIndexOf(".Container") + ".Container".length())
                    .toLowerCase(java.util.Locale.ROOT);
            this.container = "forestry." + container;
            this.item = item;
            this.screens = screens;
            layout = NativeUiLayout.builder()
                .backgroundInset(4)
                .fixedPlayerColumns(9)
                .minimumHeaderHeight(minimumHeader)
                .build();
        }

        boolean matches(InventoryUiAdapter adapter, String container) {
            return id.equals(adapter.getId()) && this.container.equals(container)
                && this.container.equals(adapter.getContainerClassName())
                && ("forestry." + screens[0]).equals(adapter.getGuiClassName());
        }
    }

    public interface ItemSlots {

        Slot dynamicinventory$additionalSlot(InventoryPlayer inventory, int raw);
    }

    public static void registerInventoryAdapters() {
        for (Host host : HOSTS) {
            var adapter = InventoryUiAdapter.builder(host.id, host.container)
                .gui("forestry." + host.screens[0])
                .layout(host.layout)
                .compatibilityRevision(2)
                .nativeTransfers();
            if (host.item) adapter.additionalSlots(
                (container, inventory, raw) -> ((ItemSlots) container).dynamicinventory$additionalSlot(inventory, raw));
            InventoryUi.register(adapter.build());
            for (int i = 1; i < host.screens.length; i++)
                InventoryUi.registerPresentation(host.id, "forestry." + host.screens[i], host.layout);
        }
    }

    public static boolean handles(Container container) {
        InventoryUiAdapter adapter = InventoryUi.findAdapter(container);
        return adapter != null && panelHost(
            adapter,
            container.getClass()
                .getName());
    }

    static boolean panelHost(InventoryUiAdapter adapter, String container) {
        for (Host host : HOSTS) if (host.matches(adapter, container)) return true;
        return false;
    }

    static boolean alternateScreen(InventoryUiAdapter adapter, String screen) {
        for (Host host : HOSTS) {
            if (!host.matches(adapter, adapter.getContainerClassName())) continue;
            for (int i = 1; i < host.screens.length; i++)
                if (("forestry." + host.screens[i]).equals(screen)) return true;
        }
        return false;
    }
}
