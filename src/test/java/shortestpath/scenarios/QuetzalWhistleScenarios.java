package shortestpath.scenarios;

import static shortestpath.profiles.Profiles.ALL;
import static shortestpath.scenarios.Scenario.scenario;

import java.util.List;

/**
 * Quetzal whistle routes. Suite {@code quetzal_whistle_routes}; exact lengths in
 * {@code scenarios/expected-lengths/quetzal_whistle_routes.json}.
 */
final class QuetzalWhistleScenarios {
    private QuetzalWhistleScenarios() { }

    static List<Scenario.Builder> all() {
        return List.of(
            scenario("Varrock → Auburnvale", "quetzal-whistle")
                .from(3213, 3428, 0).to(1411, 3361, 0)
                .profile(ALL),
            scenario("Lumbridge → Hunter Guild", "quetzal-whistle")
                .from(3222, 3218, 0).to(1585, 3053, 0)
                .profile(ALL),
            scenario("GE → Civitas illa Fortis", "primo-quetzal")
                .from(3165, 3487, 0).to(1697, 3140, 0)
                .profile(ALL),
            scenario("Camelot → Aldarin", "quetzal-whistle")
                .from(2757, 3478, 0).to(1389, 2901, 0)
                .profile(ALL),
            scenario("Ardougne → Sunset Coast", "quetzal-whistle")
                .from(2662, 3305, 0).to(1548, 2995, 0)
                .profile(ALL),
            scenario("Falador → Quetzacalli Gorge", "quetzal-whistle")
                .from(2964, 3378, 0).to(1510, 3222, 0)
                .profile(ALL),
            scenario("Canifis → Tal Teklan", "quetzal-whistle")
                .from(3507, 3496, 0).to(1226, 3091, 0)
                .profile(ALL),
            scenario("Edgeville → The Teomat", "quetzal-whistle")
                .from(3087, 3496, 0).to(1437, 3171, 0)
                .profile(ALL),
            scenario("Hunter Guild → Auburnvale", "quetzal")
                .from(1593, 3053, 0).to(1411, 3361, 0)
                .profile(ALL)
                .settings(s -> s.setCostQuetzalWhistle(0)),
            scenario("Civitas illa Fortis → Hunter Guild", "quetzal")
                .from(1697, 3148, 0).to(1585, 3053, 0)
                .profile(ALL),
            scenario("Auburnvale → Civitas illa Fortis", "quetzal")
                .from(1411, 3353, 0).to(1697, 3140, 0)
                .profile(ALL)
                .settings(s -> s.setCostQuetzalWhistle(0)),
            scenario("Sunset Coast → Aldarin", "quetzal")
                .from(1548, 3003, 0).to(1389, 2901, 0)
                .profile(ALL)
                .settings(s -> s.setCostQuetzalWhistle(0)),
            scenario("Hunter Guild → Outer Fortis", "quetzal")
                .from(1593, 3053, 0).to(1700, 3037, 0)
                .profile(ALL)
                .settings(s -> s.setCostQuetzalWhistle(0)),
            scenario("Quetzacalli Gorge → The Teomat", "quetzal")
                .from(1518, 3222, 0).to(1437, 3171, 0)
                .profile(ALL)
                .settings(s -> s.setCostQuetzalWhistle(0)),
            scenario("The Teomat → Tal Teklan", "quetzal")
                .from(1445, 3171, 0).to(1226, 3091, 0)
                .profile(ALL)
                .settings(s -> s.setCostQuetzalWhistle(0))
        );
    }
}
