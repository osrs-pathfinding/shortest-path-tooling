package shortestpath.accounts;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import shortestpath.ItemVariations;

/**
 * Reads the web planner's current account format ({@link AccountBuild}, account-build-v1) as an
 * {@link Account}. Its {@code routingVariables} become unlocks where they match one, are dropped
 * where the account's facts imply them, and stay raw otherwise.
 */
public final class AccountBuilds {
    private AccountBuilds() { }

    public static Account toAccount(AccountBuild build) {
        Account.Builder account = Account.builder()
            .nowMinutes(build.benchmarkNowMinutes)
            .inventoryContainer().equipmentContainer().bankContainer()
            .spellbook(Spellbook.valueOf(build.runtime.spellbook.toUpperCase(Locale.ROOT)))
            .poh(poh(build.poh))
            .questPoints(build.levels.getOrDefault("Quest", 0));
        if (!"ready".equals(build.runtime.minigameTeleport.state)) {
            account.minigameTeleportUsedAt(build.runtime.minigameTeleport.minutes);
        }

        int total = 0;
        for (Map.Entry<String, Integer> entry : build.levels.entrySet()) {
            if (entry.getKey().equals("Quest") || entry.getKey().equals("Total")) {
                continue;
            }
            account.level(skill(entry.getKey()), entry.getValue());
            total += entry.getValue();
        }
        account.totalLevel(total);

        for (Quest quest : Quest.values()) {
            if (build.completedQuests.contains(quest.getName())) {
                account.quest(quest, QuestState.FINISHED);
            }
        }
        if (build.completedQuests.contains("Architectural Alliance")) account.unlock(Unlock.ARCHITECTURAL_ALLIANCE);
        if (build.completedQuests.contains("The Tale of the Righteous")) account.unlock(Unlock.TALE_OF_THE_RIGHTEOUS);
        build.diaries.forEach((region, tier) -> account.diary(diary(region), diaryTier(tier)));
        if (build.fairyRingsUnlocked) account.unlock(Unlock.FAIRY_RINGS);
        PlantedSpiritTree[] trees = build.plantedSpiritTrees.stream()
            .map(PlantedSpiritTree::valueOf).toArray(PlantedSpiritTree[]::new);
        account.plantedSpiritTrees(trees);

        items("inventory", build.inventory).forEach(account::inventory);
        items("runePouch", build.runePouch).forEach(account::runePouch);
        items("equipment", build.equipment).forEach(account::equipment);
        items("bank", build.bank).forEach(account::bank);

        // Variables the facts imply are the facts' business; the rest are unlocks or raw values.
        ClientState implied = AccountCompiler.compile(account.build());
        Map<Integer, Integer> varbits = new HashMap<>(build.routingVariables.varbits);
        int quetzalPlatforms = build.routingVariables.varplayers.getOrDefault(VarPlayerID.QUETZALS_UNLOCKED, 0);
        for (Unlock unlock : Unlock.values()) {
            if (unlock.kind == Unlock.Kind.VARPLAYER_BIT) {
                if ((quetzalPlatforms & unlock.value) != 0) account.unlock(unlock);
            } else if (varbits.getOrDefault(unlock.id, 0) == unlock.value) {
                account.unlock(unlock);
            }
        }
        varbits.forEach((id, value) -> {
            if (!implied.varbits().containsKey(id) && value != 0) account.varbit(id, value);
        });
        build.routingVariables.varplayers.forEach((id, value) -> {
            if (!implied.varplayers().containsKey(id) && value != 0) account.varplayer(id, value);
        });
        return account.build();
    }

    private static Poh poh(AccountBuild.Poh poh) {
        return new Poh(Poh.Location.valueOf(poh.location.toUpperCase(Locale.ROOT)), poh.fairyRing, poh.spiritTree,
            poh.obelisk, jewelleryBox(poh.jewelleryBox), poh.mountedGlory, poh.mountedXerics, poh.mountedDigsite,
            poh.mountedMythical, "all".equals(poh.portals.mode) ? null : poh.portals.destinations);
    }

    private static Poh.JewelleryBox jewelleryBox(String value) {
        switch (value) {
            case "NoJewelleryBox": return Poh.JewelleryBox.NONE;
            case "FancyJewelleryBox": return Poh.JewelleryBox.FANCY;
            case "OrnateJewelleryBox": return Poh.JewelleryBox.ORNATE;
            default: throw new IllegalArgumentException("unknown POH jewellery box: " + value);
        }
    }

    private static Diary diary(String region) {
        switch (region) {
            case "KourendKebos": return Diary.KOUREND_KEBOS;
            case "LumbridgeDraynor": return Diary.LUMBRIDGE_DRAYNOR;
            case "WesternProvinces": return Diary.WESTERN_PROVINCES;
            default: return Diary.valueOf(region.toUpperCase(Locale.ROOT));
        }
    }

    private static Diary.Tier diaryTier(String tier) {
        return "NoDiary".equals(tier) ? Diary.Tier.NONE : Diary.Tier.valueOf(tier.toUpperCase(Locale.ROOT));
    }

    private static Skill skill(String name) {
        for (Skill skill : Skill.values()) {
            if (skill.getName().equals(name)) {
                return skill;
            }
        }
        throw new IllegalArgumentException("unknown skill: " + name);
    }

    /** Items by id; a key may also name an {@link ItemVariations} constant, which means its first id. */
    private static Map<Integer, Integer> items(String field, Map<String, Integer> source) {
        Map<Integer, Integer> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            int id;
            try {
                id = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException notAnId) {
                ItemVariations variation = ItemVariations.fromName(entry.getKey());
                if (variation == null) {
                    throw new IllegalArgumentException("unknown item in " + field + ": " + entry.getKey());
                }
                id = variation.getIds()[0];
            }
            result.merge(id, entry.getValue(), Math::addExact);
        }
        return result;
    }
}
