package shortestpath.dashboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import shortestpath.ShortestPathConfig;
import shortestpath.TestShortestPathConfig;
import shortestpath.profiles.PluginSettings;
import shortestpath.settings.Settings;
import shortestpath.settings.ConfigKey;

/**
 * Config-surface parity lint between {@link ShortestPathConfig} and the dashboard
 * twin {@link PluginSettings}.
 *
 * <p>Three properties are asserted for every runtime-retained {@code @ConfigItem}
 * on the interface:
 * <ol>
 *   <li><b>Presence</b> — {@code PluginSettings} declares a method with
 *       the same name (a fall-through to the interface default is a failure —
 *       the twin exists so scenario config semantics never silently depend on
 *       upstream defaults).</li>
 *   <li><b>Setter</b> — the twin declares a {@code setXxx} setter following its
 *       {@code set} + capitalized-name convention so profiles and scenarios can set it.</li>
 *   <li><b>Default parity</b> — the value returned by a fresh twin equals the
 *       value returned by a fresh {@link TestShortestPathConfig}, unless the
 *       method is named in {@link #ALLOWLIST} with a justification.</li>
 * </ol>
 *
 * <p>Deliberate divergences live in the two allowlist maps below — never silence
 * a fixable miss by allowlisting it. {@link TestShortestPathConfig} is never
 * lint-bound: it participates only as the default-parity reference and its
 * members/setters are never enumerated or asserted.
 *
 * <p>The test is deterministic and read-only: it enumerates reflection metadata
 * and reads fresh twin instances only — no shared state, safe
 * under Gradle parallel test execution.
 */
public class ConfigParityTest {

    /**
     * Methods deliberately NOT twinned: exempt from presence and setter checks (they still run through default parity — a method absent
     * from the twin invokes the same interface default on both sides, so the
     * comparison stays meaningful). Map value = one-line justification.
     */
    private static final Map<String, String> NO_TWIN_ALLOWLIST = new TreeMap<>();

    static {
        // Display-only items — colours are never read during pathfinding.
        NO_TWIN_ALLOWLIST.put("colourBankPickupHighlight", "display colour; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("colourCollisionMap", "display colour; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("colourPath", "display colour; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("colourPathCalculating", "display colour; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("colourPathUnreachable", "display colour; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("colourTeleportPulse", "display colour; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("colourText", "display colour; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("colourTransports", "display colour; never read during pathfinding");
        // Display-only items — overlay/render toggles.
        NO_TWIN_ALLOWLIST.put("drawClickPoints", "overlay toggle; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("drawCollisionMap", "overlay toggle; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("drawDebugPanel", "overlay toggle; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("drawMap", "overlay toggle; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("drawMinimap", "overlay toggle; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("drawTiles", "overlay toggle; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("drawTransports", "overlay toggle; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("highlightBankPickupItems", "overlay highlight; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("highlightInventoryItems", "overlay highlight; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("highlightSpellbookSpells", "overlay highlight; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("pathStyle", "render style; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("showBankPickupInfo", "info display; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("showSidebarPanel", "toolbar-visibility toggle; plugin-side, never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("showTeleportPulse", "render toggle; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("showTileCounter", "info display; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("showTransportInfo", "info display; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("showUnreachableText", "info display; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("tileCounterStep", "display counter stride; never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("unreachableText", "unreachable-banner text; display only");
        NO_TWIN_ALLOWLIST.put("clearPathHotkey", "keybind; never read during pathfinding");
        // Plugin-side runtime knobs — read by ShortestPathPlugin's restart /
        // completion / broadcast logic, never by PathfinderConfig; the harness
        // runs a single pathfinding pass so they cannot affect a route.
        NO_TWIN_ALLOWLIST.put("cancelInstead", "restart-loop knob; plugin-side, never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("recalculateDistance", "restart-loop knob; plugin-side, never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("reachedDistance", "path-completion distance; plugin-side, never read during pathfinding");
        NO_TWIN_ALLOWLIST.put("postTransports", "plugin-message broadcast toggle; never read during pathfinding");
        // Derived sets — their interface defaults delegate to the twinned
        // booleans (useTeleportationPortalsPoh / usePohMountedItems), so the
        // full-on/full-off surface scenarios actually need is already
        // stub-able through the twinned boolean; a partial set override has
        // no harness consumer worth a dedicated twin setter.
        NO_TWIN_ALLOWLIST.put("pohNexusPortals", "interface default derives the set from twinned useTeleportationPortalsPoh; a partial set override has no harness consumer");
        NO_TWIN_ALLOWLIST.put("pohMountedItems", "interface default derives the set from twinned usePohMountedItems; a partial set override has no harness consumer");
        NO_TWIN_ALLOWLIST.put("balloonLogBasketRoutes", "interface default derives the set from twinned unlockBalloonLogBasket; a partial set override has no harness consumer");
    }

    /**
     * Twinned methods whose wire payload is deliberately inert through
     * {@link Settings#effective()} — the {@code ConfigKey} registry declares
     * these keys' coercion NONE, so a payload value never reaches the getter.
     * Map value = one-line justification naming where the value actually
     * lands. Untwinned keys are covered by {@link #NO_TWIN_ALLOWLIST} instead.
     */
    private static final Map<String, String> WIRE_INERT_ALLOWLIST = new TreeMap<>();

    static {
        // Payload-inert (coercion NONE): the backend is a structural choice,
        // not an override surface.
        WIRE_INERT_ALLOWLIST.put("pathfinderBackend",
            "pathfinderAlgorithm payloads are inert (coercion NONE) — backend choice is structural");
        // Serialised write-path state: persisted through the setBuilt*/blocked
        // write methods, never payload-overridable (coercion NONE).
        WIRE_INERT_ALLOWLIST.put("builtTeleportationBoxes",
            "write-path key (setBuiltTeleportationBoxes); payload-inert through effective()");
        WIRE_INERT_ALLOWLIST.put("builtTeleportationPortalsPoh",
            "write-path key (setBuiltTeleportationPortalsPoh); payload-inert through effective()");
        WIRE_INERT_ALLOWLIST.put("blockedTeleportItems",
            "write-path key (persisted state); payload-inert through effective()");
        // The payload pairs with the costQuetzals() base at the driven
        // whistle read, so the effective getter is inert; parity is asserted
        // through coerceInt in the test body instead.
        WIRE_INERT_ALLOWLIST.put("costQuetzalWhistle",
            "payload pairs with the costQuetzals() base at the driven whistle read; asserted via coerceInt");
    }

    /**
     * Twinned methods whose default intentionally differs from
     * {@link TestShortestPathConfig}. Map value = one-line justification; every
     * entry must also be justified on the twin's class doc.
     */
    private static final Map<String, String> ALLOWLIST = new TreeMap<>();

    static {
        ALLOWLIST.put("calculationCutoff",
            "500 vs 5 — dashboard runs need a larger cutoff (documented twin class-doc exception)");
        ALLOWLIST.put("currencyThreshold",
            "10_000_000 vs 100_000 — generous so currency gates never block committed scenarios (documented twin class-doc exception)");
        ALLOWLIST.put("useTeleportationItems",
            "NONE vs INVENTORY_NON_CONSUMABLE — presets always stub it per-row (documented twin class-doc exception)");
    }

    /**
     * The config-item surface: zero-arg, non-void methods carrying
     * {@code @ConfigItem}. The interface also annotates the paired
     * {@code setBuilt*(String)} write methods with the same {@code keyName}
     * (so the values persist through RuneLite's config store) — those carry a
     * parameter and return {@code void} and are the write half of items the
     * getters already cover, so they are filtered out here.
     */
    private static List<Method> configItems() {
        List<Method> items = new ArrayList<>();
        for (Method m : ShortestPathConfig.class.getMethods()) {
            if (m.isAnnotationPresent(ConfigItem.class)
                && m.getParameterCount() == 0
                && m.getReturnType() != void.class) {
                items.add(m);
            }
        }
        items.sort(Comparator.comparing(Method::getName));
        return items;
    }

    private static String keyName(Method m) {
        return m.getAnnotation(ConfigItem.class).keyName();
    }

    private static String setterName(String methodName) {
        return "set" + Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
    }

    @Test
    public void hiddenSettingsReachThePlugin() throws Exception {
        for (Method getter : PluginSettings.class.getDeclaredMethods()) {
            if (getter.getParameterCount() != 0 || getter.getReturnType() == void.class) {
                continue;
            }
            String key = getter.getName().equals("pathfinderBackend")
                ? "pathfinderAlgorithm" : getter.getName().equals("unreachableTargetDistance")
                ? "unreachableTargetDistanceThreshold" : getter.getName();
            ConfigKey row = ConfigKey.forKey(key);
            if (row == null || !row.isKeyed()) {
                continue;
            }
            PluginSettings twin = new PluginSettings();
            assertEquals(key, getter.invoke(twin), Settings.wrap(twin).configuredValue(key));
            Object sample = sampleValue(getter, getter.invoke(twin), getter.invoke(twin));
            if (sample != null) {
                PluginSettings.class.getDeclaredMethod(setterName(getter.getName()), getter.getReturnType())
                    .invoke(twin, sample);
                assertEquals(key, sample, Settings.wrap(twin).configuredValue(key));
            }
        }
    }

    @Test
    public void everyConfigItemHasATwinOverride() {
        List<String> missing = new ArrayList<>();
        for (Method m : configItems()) {
            if (NO_TWIN_ALLOWLIST.containsKey(m.getName())) {
                continue;
            }
            try {
                PluginSettings.class.getDeclaredMethod(m.getName());
            } catch (NoSuchMethodException e) {
                missing.add(m.getName() + " (keyName=" + keyName(m) + ")");
            }
        }
        // Forward-compat report: twin members with no @ConfigItem counterpart
        // are non-fatal — printed for visibility only.
        List<String> orphan = new ArrayList<>();
        for (Method m : PluginSettings.class.getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType() != void.class
                && !m.getName().startsWith("set") && !m.getName().startsWith("is")) {
                try {
                    if (ShortestPathConfig.class.getMethod(m.getName())
                        .isAnnotationPresent(ConfigItem.class)) {
                        continue;
                    }
                } catch (NoSuchMethodException e) {
                    // fall through — not on the interface at all
                }
                orphan.add(m.getName());
            }
        }
        if (!orphan.isEmpty()) {
            Collections.sort(orphan);
            System.out.println("ConfigParityTest forward-compat twin members"
                + " (no @ConfigItem counterpart, non-fatal): " + orphan);
        }
        assertTrue("@ConfigItem methods missing a PluginSettings override: "
            + missing, missing.isEmpty());
    }

    @Test
    public void everyConfigItemHasATwinSetter() {
        List<String> missing = new ArrayList<>();
        for (Method m : configItems()) {
            if (NO_TWIN_ALLOWLIST.containsKey(m.getName())) {
                continue;
            }
            String setter = setterName(m.getName());
            try {
                PluginSettings.class.getDeclaredMethod(setter, m.getReturnType());
            } catch (NoSuchMethodException e) {
                missing.add(setter + "(" + m.getReturnType().getSimpleName() + ")");
            }
        }
        assertTrue("@ConfigItem methods missing a twin setXxx setter: " + missing,
            missing.isEmpty());
    }

    @Test
    public void twinDefaultsMatchTestConfig() throws Exception {
        PluginSettings twin = new PluginSettings();
        TestShortestPathConfig reference = new TestShortestPathConfig();
        List<String> divergent = new ArrayList<>();
        for (Method m : configItems()) {
            if (ALLOWLIST.containsKey(m.getName())) {
                continue;
            }
            Object twinValue = m.invoke(twin);
            Object referenceValue = m.invoke(reference);
            if (!Objects.equals(twinValue, referenceValue)) {
                divergent.add(m.getName() + " (twin=" + twinValue
                    + ", reference=" + referenceValue + ")");
            }
        }
        assertTrue("Config defaults divergent between the dashboard twin and"
            + " TestShortestPathConfig (declare deliberate differences in ALLOWLIST): "
            + divergent, divergent.isEmpty());
    }

    /**
     * Wire-vs-twin parity: for every twinned config item whose payload is
     * live, a {@link Settings#applyOverrides} payload keyed by
     * {@code @ConfigItem.keyName} must move {@code effective()} to the same
     * value the twin's {@code setXxx} setter produces — the surviving
     * unification contract between the plugin-message override seam and the
     * scenario-harness seam.
     */
    @Test
    public void wireOverridesMatchTwinSetters() throws Exception {
        List<String> failures = new ArrayList<>();
        for (Method m : configItems()) {
            if (NO_TWIN_ALLOWLIST.containsKey(m.getName())
                || WIRE_INERT_ALLOWLIST.containsKey(m.getName())) {
                continue;
            }
            Object wireBase = m.invoke(new TestShortestPathConfig());
            PluginSettings twin = new PluginSettings();
            Object twinBase = m.invoke(twin);
            Object expected = sampleValue(m, wireBase, twinBase);
            if (expected == null) {
                failures.add(m.getName() + ": no generic wire sample for return type "
                    + m.getReturnType().getSimpleName());
                continue;
            }
            Settings wire = Settings.wrap(new TestShortestPathConfig());
            wire.applyOverrides(Map.of(keyName(m), wireForm(expected)));
            Object wireValue = m.invoke(wire.effective());
            Method setter;
            try {
                setter = PluginSettings.class.getDeclaredMethod(setterName(m.getName()),
                    m.getReturnType());
            } catch (NoSuchMethodException e) {
                failures.add(m.getName() + ": twin setter " + setterName(m.getName())
                    + "(" + m.getReturnType().getSimpleName() + ") missing");
                continue;
            }
            setter.invoke(twin, expected);
            Object twinValue = m.invoke(twin);
            if (!Objects.equals(wireValue, expected) || !Objects.equals(twinValue, expected)) {
                failures.add(m.getName() + " (keyName=" + keyName(m) + "): wire="
                    + wireValue + ", twin=" + twinValue + ", expected=" + expected);
            }
        }
        // costQuetzalWhistle's payload pairs with the costQuetzals() base at
        // the driven TransportType read for the whistle, so its own effective
        // getter is inert; parity for that key goes through the keyed
        // coerceInt read the production call site uses.
        Settings whistleWire = Settings.wrap(new TestShortestPathConfig());
        whistleWire.applyOverrides(Map.of("costQuetzalWhistle", 7));
        PluginSettings whistleTwin = new PluginSettings();
        whistleTwin.setCostQuetzalWhistle(7);
        assertEquals("costQuetzalWhistle wire/twin divergence",
            whistleTwin.costQuetzalWhistle(),
            whistleWire.coerceInt("costQuetzalWhistle", whistleWire.effective().num(ConfigKey.COST_QUETZALS)));
        assertTrue("Wire-override vs twin-setter divergence (justify in"
            + " WIRE_INERT_ALLOWLIST or fix the seam): " + failures, failures.isEmpty());
    }

    /**
     * A non-default sample of the getter's return type equal to neither
     * side's base — a sample matching a base makes that side's write a
     * no-op and its half of the parity check vacuous. {@code null} when
     * no distinguishing sample exists (the caller records a failure).
     */
    private static Object sampleValue(Method m, Object wireBase, Object twinBase) {
        Class<?> type = m.getReturnType();
        if (type == boolean.class) {
            boolean flipped = !(Boolean) wireBase;
            return flipped == (Boolean) twinBase ? null : flipped;
        }
        if (type == int.class) {
            for (int candidate : new int[]{7, 13, 3}) {
                if (!Integer.valueOf(candidate).equals(wireBase)
                        && !Integer.valueOf(candidate).equals(twinBase)) {
                    return candidate;
                }
            }
            return null;
        }
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                if (!constant.equals(wireBase) && !constant.equals(twinBase)) {
                    return constant;
                }
            }
        }
        return null;
    }

    /**
     * The payload shape {@link Settings#applyOverrides} coerces for a typed
     * sample: exact-type for booleans/ints/colours, the {@code type} string
     * ({@code toString}) for the string-parsed enum coercions.
     */
    private static Object wireForm(Object sample) {
        if (sample instanceof Enum) {
            return sample.toString();
        }
        return sample;
    }
}
