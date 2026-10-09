package shortestpath.corpus.profiles;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import shortestpath.accounts.Account;

/** The canonical profiles as {@link Account}s: the same state the generated corpus JSON describes. */
public final class CanonicalAccounts {
    public static final List<String> NAMES = List.of("early", "mid", "end", "maxed");

    private static final Map<String, Supplier<ProfileSpec>> PROFILES = Map.of(
        "early", CanonicalProfiles::early,
        "mid", CanonicalProfiles::mid,
        "end", CanonicalProfiles::end,
        "maxed", CanonicalProfiles::maxed);

    private CanonicalAccounts() { }

    /** The game time every canonical profile is evaluated at, in minutes. */
    public static long benchmarkNowMinutes() {
        return RoutingVariables.BENCHMARK_NOW_MINUTES;
    }

    public static Account account(String name) {
        Supplier<ProfileSpec> profile = PROFILES.get(name);
        if (profile == null) {
            throw new IllegalArgumentException("unknown canonical profile: " + name);
        }
        return account(profile.get());
    }

    static Account account(ProfileSpec source) {
        CompiledProfile compiled = ProfileCompiler.compile(source);
        Account.Builder account = Account.builder()
            .defaultLevel(1)
            .defaultQuestState(QuestState.NOT_STARTED)
            .nowMinutes(RoutingVariables.BENCHMARK_NOW_MINUTES)
            .inventoryContainer().equipmentContainer().bankContainer();
        for (Map.Entry<Skill, Integer> level : source.levels().entrySet()) {
            account.level(level.getKey(), level.getValue());
        }
        if (source.totalLevel() != null) {
            account.totalLevel(source.totalLevel());
        }

        compiled.variables.varbits.forEach(account::varbit);
        if (!compiled.variables.varbits.containsKey(VarbitID.FAIRY2_QUEENCURE_QUEST)) {
            account.varbit(VarbitID.FAIRY2_QUEENCURE_QUEST, source.fairyRingsUnlocked() ? 100 : 0);
        }
        compiled.variables.varplayers.forEach(account::varplayer);
        account.varplayer(VarPlayerID.QP, source.questPoints() == null ? 0 : source.questPoints());

        Set<String> completed = Set.copyOf(compiled.quests);
        for (Quest quest : Quest.values()) {
            if (completed.contains(quest.getName())) {
                account.quest(quest, QuestState.FINISHED);
            }
        }

        source.inventory().forEach(account::inventory);
        source.runePouch().forEach(account::inventory);
        source.equipment().forEach(account::equipment);
        source.bank().forEach(account::bank);

        Set<String> trees = new LinkedHashSet<>();
        for (PlantedSpiritTree tree : PlantedSpiritTree.values()) {
            if (source.plantedSpiritTrees().contains(tree)) {
                trees.add(SPIRIT_TREE_NAMES.get(tree));
            }
        }
        account.plantedSpiritTrees(trees);

        PohSpec poh = source.poh();
        account.poh(new Account.Poh(poh.fairyRing(), poh.spiritTree(), poh.obelisk(),
            Account.JewelleryBox.valueOf(poh.jewelleryBox().name()),
            poh.mountedGlory(), poh.mountedXerics(), poh.mountedDigsite(), poh.mountedMythical(),
            poh.portalMode() == PortalMode.ALL ? null : sorted(poh.portalDestinations())));
        return account.build();
    }

    private static final Map<PlantedSpiritTree, String> SPIRIT_TREE_NAMES = new LinkedHashMap<>();

    static {
        SPIRIT_TREE_NAMES.put(PlantedSpiritTree.FARMING_GUILD, "Farming Guild");
        SPIRIT_TREE_NAMES.put(PlantedSpiritTree.PORT_SARIM, "Port Sarim");
        SPIRIT_TREE_NAMES.put(PlantedSpiritTree.ETCETERIA, "Etceteria");
        SPIRIT_TREE_NAMES.put(PlantedSpiritTree.BRIMHAVEN, "Brimhaven");
        SPIRIT_TREE_NAMES.put(PlantedSpiritTree.HOSIDIUS, "Hosidius");
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().collect(java.util.stream.Collectors.toList());
    }
}
