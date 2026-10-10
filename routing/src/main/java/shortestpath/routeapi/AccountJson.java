package shortestpath.routeapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import shortestpath.accounts.Account;
import shortestpath.accounts.Diary;
import shortestpath.accounts.PlantedSpiritTree;
import shortestpath.accounts.Poh;
import shortestpath.accounts.Spellbook;
import shortestpath.accounts.Unlock;

/**
 * An {@link Account} as JSON ({@code corpus/schemas/account-v1}): the web planner edits these and
 * sends them with each route request. Values are the Java enum constant names, so Jackson rejects
 * anything the account model does not know.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AccountJson {
    public String name;
    public Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
    public int questPoints;
    /** Finished quests; the plugin only ever checks whether a quest is finished. */
    public Set<Quest> completedQuests = EnumSet.noneOf(Quest.class);
    public Map<Diary, Diary.Tier> diaries = new EnumMap<>(Diary.class);
    public Set<Unlock> unlocks = EnumSet.noneOf(Unlock.class);
    public Spellbook spellbook = Spellbook.STANDARD;
    /** When a minigame teleport was last used, in game minutes; absent means it is ready. */
    public Long minigameTeleportUsedAt;
    /** The game clock in minutes; absent means the wall clock. */
    public Long nowMinutes;
    public Map<Integer, Integer> inventory = new LinkedHashMap<>();
    public Map<Integer, Integer> runePouch = new LinkedHashMap<>();
    public Map<Integer, Integer> equipment = new LinkedHashMap<>();
    public Map<Integer, Integer> bank = new LinkedHashMap<>();
    /** The player's house; absent means none. */
    public House house;
    public Set<PlantedSpiritTree> plantedSpiritTrees = EnumSet.noneOf(PlantedSpiritTree.class);

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class House {
        public Poh.Location location;
        public Poh.JewelleryBox jewelleryBox = Poh.JewelleryBox.NONE;
        public boolean fairyRing;
        public boolean spiritTree;
        public boolean obelisk;
        public boolean mountedGlory;
        public boolean mountedXerics;
        public boolean mountedDigsite;
        public boolean mountedMythical;
        /** Nexus portal destinations by plugin display name; absent means every portal. */
        public List<String> portals;
    }

    public Account toAccount() {
        Account.Builder account = Account.builder()
            .questPoints(questPoints)
            .spellbook(spellbook)
            .inventoryContainer().equipmentContainer().bankContainer()
            .plantedSpiritTrees(plantedSpiritTrees.toArray(new PlantedSpiritTree[0]));
        levels.forEach(account::level);
        completedQuests.forEach(quest -> account.quest(quest, QuestState.FINISHED));
        diaries.forEach(account::diary);
        account.unlock(unlocks.toArray(new Unlock[0]));
        if (minigameTeleportUsedAt != null) account.minigameTeleportUsedAt(minigameTeleportUsedAt);
        if (nowMinutes != null) account.nowMinutes(nowMinutes);
        inventory.forEach(account::inventory);
        runePouch.forEach(account::runePouch);
        equipment.forEach(account::equipment);
        bank.forEach(account::bank);
        if (house != null) {
            account.poh(new Poh(house.location, house.fairyRing, house.spiritTree, house.obelisk, house.jewelleryBox,
                house.mountedGlory, house.mountedXerics, house.mountedDigsite, house.mountedMythical, house.portals));
        }
        return account.build();
    }

    /** The JSON form of {@code account}, which must have no raw variables (JSON has none). */
    public static AccountJson of(String name, Account account) {
        if (!account.varbits().isEmpty() || !account.varplayers().isEmpty()) {
            throw new IllegalArgumentException("an account with raw variables has no JSON form");
        }
        AccountJson json = new AccountJson();
        json.name = name;
        json.levels.putAll(account.levels());
        json.questPoints = account.questPoints();
        for (Quest quest : Quest.values()) {
            if (account.questState(quest) == QuestState.FINISHED) json.completedQuests.add(quest);
        }
        json.diaries.putAll(account.diaries());
        json.unlocks.addAll(account.unlocks());
        json.spellbook = account.spellbook();
        json.minigameTeleportUsedAt = account.minigameTeleportUsedAt();
        json.nowMinutes = account.nowMinutes();
        putAll(json.inventory, account.inventory());
        putAll(json.runePouch, account.runePouch());
        putAll(json.equipment, account.equipment());
        putAll(json.bank, account.bank());
        if (account.poh() != null) {
            Poh poh = account.poh();
            json.house = new House();
            json.house.location = poh.location;
            json.house.jewelleryBox = poh.jewelleryBox;
            json.house.fairyRing = poh.fairyRing;
            json.house.spiritTree = poh.spiritTree;
            json.house.obelisk = poh.obelisk;
            json.house.mountedGlory = poh.mountedGlory;
            json.house.mountedXerics = poh.mountedXerics;
            json.house.mountedDigsite = poh.mountedDigsite;
            json.house.mountedMythical = poh.mountedMythical;
            json.house.portals = poh.portals == null ? null : new ArrayList<>(poh.portals);
        }
        if (account.plantedSpiritTrees() != null) json.plantedSpiritTrees.addAll(account.plantedSpiritTrees());
        return json;
    }

    private static void putAll(Map<Integer, Integer> target, Map<Integer, Integer> items) {
        if (items != null) target.putAll(items);
    }
}
