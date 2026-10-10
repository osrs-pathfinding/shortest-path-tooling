package shortestpath.accounts;

/** A spirit tree the player has planted, by the name the plugin uses for it. */
public enum PlantedSpiritTree {
    FARMING_GUILD("Farming Guild"),
    PORT_SARIM("Port Sarim"),
    ETCETERIA("Etceteria"),
    BRIMHAVEN("Brimhaven"),
    HOSIDIUS("Hosidius");

    public final String pluginName;

    PlantedSpiritTree(String pluginName) {
        this.pluginName = pluginName;
    }
}
