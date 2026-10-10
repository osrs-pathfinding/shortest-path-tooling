package shortestpath.accounts;

/** An achievement diary region; each tier's completion is a varbit. */
public enum Diary {
    ARDOUGNE, DESERT, FALADOR, FREMENNIK, KANDARIN, KARAMJA, KOUREND_KEBOS, LUMBRIDGE_DRAYNOR,
    MORYTANIA, VARROCK, WESTERN_PROVINCES, WILDERNESS;

    /** How far a diary is completed; each tier implies the ones below it. */
    public enum Tier { NONE, EASY, MEDIUM, HARD, ELITE }
}
