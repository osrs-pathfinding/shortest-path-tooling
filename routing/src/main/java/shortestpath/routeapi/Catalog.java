package shortestpath.routeapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import shortestpath.accounts.Diary;
import shortestpath.accounts.PlantedSpiritTree;
import shortestpath.accounts.Poh;
import shortestpath.accounts.Spellbook;
import shortestpath.accounts.Unlock;
import shortestpath.transport.PohNexusPortal;

/**
 * Everything the web planner's editors offer, as {@code GET /v1/catalog} returns it: every account
 * fact's possible values and every planner setting. The editors are built from it, so a new
 * quest, unlock or plugin setting reaches the planner without frontend changes.
 */
public final class Catalog {
    /** A value an editor can pick: the id the JSON uses and a label to show. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Option {
        public final String id;
        public final String name;
        /** For unlocks: the group an editor shows it in. */
        public final String group;

        Option(String id, String name) {
            this(id, name, null);
        }

        Option(String id, String name, String group) {
            this.id = id;
            this.name = name;
            this.group = group;
        }
    }

    public final List<Option> skills = options(Arrays.stream(Skill.values()).filter(skill -> skill != Skill.OVERALL),
        Skill::getName);
    public final List<Option> quests = options(Arrays.stream(Quest.values()), Quest::getName);
    public final List<Option> diaries = options(Arrays.stream(Diary.values()), Catalog::title);
    public final List<Option> diaryTiers = options(Arrays.stream(Diary.Tier.values()), Catalog::title);
    public final List<Option> unlocks = Arrays.stream(Unlock.values())
        .map(unlock -> new Option(unlock.name(), title(unlock), title(unlock.group))).collect(Collectors.toList());
    public final List<Option> spellbooks = options(Arrays.stream(Spellbook.values()), Catalog::title);
    public final List<Option> houseLocations = options(Arrays.stream(Poh.Location.values()), Catalog::title);
    public final List<Option> jewelleryBoxes = options(Arrays.stream(Poh.JewelleryBox.values()), Catalog::title);
    public final List<Option> portals = Arrays.stream(PohNexusPortal.values())
        .map(portal -> new Option(portal.getDisplayInfos().get(0), portal.toString())).collect(Collectors.toList());
    public final List<Option> plantedSpiritTrees = Arrays.stream(PlantedSpiritTree.values())
        .map(tree -> new Option(tree.name(), tree.pluginName)).collect(Collectors.toList());
    public final List<PlannerSettings.Setting> settings = PlannerSettings.catalog();

    private static <T extends Enum<T>> List<Option> options(java.util.stream.Stream<T> values, Function<T, String> name) {
        return values.map(value -> new Option(value.name(), name.apply(value))).collect(Collectors.toList());
    }

    /** {@code KOUREND_KEBOS} as {@code Kourend kebos}. */
    private static String title(Enum<?> value) {
        String words = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
