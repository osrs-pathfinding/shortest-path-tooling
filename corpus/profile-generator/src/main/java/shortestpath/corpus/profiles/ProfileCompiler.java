package shortestpath.corpus.profiles;

import net.runelite.api.Quest;
import net.runelite.api.Skill;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Turns semantic profiles into the stable v1 interchange representation. */
final class ProfileCompiler {
    private ProfileCompiler() { }

    static CompiledProfile compile(ProfileSpec source) {
        CompiledVariables variables = RoutingVariables.compile(source);
        Map<String, Integer> levels = new TreeMap<>();
        for (Map.Entry<Skill, Integer> entry : source.levels.entrySet()) {
            levels.put(entry.getKey().getName(), entry.getValue());
        }
        if (source.name.equals("end") || source.name.equals("maxed")) levels.put("Quest", 327);
        if (source.name.equals("maxed")) levels.put("Total", 2376);
        List<String> quests = new ArrayList<>();
        for (Quest quest : source.completedQuests) quests.add(quest.getName());
        for (CorpusQuest quest : source.completedCorpusQuests) quests.add(quest.name);
        quests.sort(String::compareTo);
        return new CompiledProfile(source, levels, quests, variables);
    }
}

final class CompiledProfile {
    final ProfileSpec source;
    final Map<String, Integer> levels;
    final List<String> quests;
    final CompiledVariables variables;
    CompiledProfile(ProfileSpec source, Map<String, Integer> levels, List<String> quests,
                    CompiledVariables variables) {
        this.source = source;
        this.levels = levels;
        this.quests = quests;
        this.variables = variables;
    }
}
