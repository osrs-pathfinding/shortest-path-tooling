package shortestpath.accounts;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;

/**
 * A logged-in RuneLite {@link Client} that reports a {@link ClientState}.
 *
 * <p>Only the calls the pathfinder makes while refreshing its config are answered from the
 * state. Every other method returns an empty value (0, {@code false}, an empty collection or
 * {@code null}). Boosted and real skill levels are both the state's levels.
 */
public final class HeadlessClient {
    private HeadlessClient() { }

    public static Client of(ClientState state) {
        ItemContainer inventory = container(state.inventory());
        ItemContainer equipment = container(state.equipment());
        Player player = state.location() == null ? null : player(new WorldPoint(
            state.location().x, state.location().y, state.location().plane));
        int totalLevel = state.totalLevel();
        return proxy(Client.class, (method, args) -> {
            switch (method.getName()) {
                case "getGameState": return GameState.LOGGED_IN;
                case "getClientThread": return Thread.currentThread();
                case "getWorldType": return state.worldTypes().isEmpty()
                    ? EnumSet.noneOf(WorldType.class) : EnumSet.copyOf(state.worldTypes());
                case "getVarbitValue": return state.varbits().getOrDefault((Integer) args[0], 0);
                case "getVarpValue": return state.varplayers().getOrDefault((Integer) args[0], 0);
                case "getBoostedSkillLevel":
                case "getRealSkillLevel": return state.level((Skill) args[0]);
                case "getTotalLevel": return totalLevel;
                case "getLocalPlayer": return player;
                case "getItemContainer":
                    if (args[0] instanceof Integer) {
                        int id = (Integer) args[0];
                        if (id == InventoryID.INV) return inventory;
                        if (id == InventoryID.WORN) return equipment;
                    }
                    return null;
                default: return EMPTY;
            }
        });
    }

    /** A container holding {@code items}; {@code null} for {@code null}. */
    public static ItemContainer container(Map<Integer, Integer> items) {
        if (items == null) {
            return null;
        }
        List<Item> list = new ArrayList<>(items.size());
        items.forEach((id, quantity) -> list.add(new Item(id, quantity)));
        Item[] array = list.toArray(new Item[0]);
        return proxy(ItemContainer.class, (method, args) ->
            method.getName().equals("getItems") ? array.clone() : EMPTY);
    }

    private static Player player(WorldPoint location) {
        return proxy(Player.class, (method, args) ->
            method.getName().equals("getWorldLocation") ? location : EMPTY);
    }

    private interface Answer {
        Object answer(Method method, Object[] args);
    }

    /** Returned by an {@link Answer} for "no answer": the caller gets the type's empty value. */
    private static final Object EMPTY = new Object();

    private static <T> T proxy(Class<T> type, Answer answer) {
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "equals": return self == args[0];
                    case "hashCode": return System.identityHashCode(self);
                    default: return type.getSimpleName() + " for an account";
                }
            }
            Object result = answer.answer(method, args);
            return result == EMPTY ? empty(method.getReturnType()) : result;
        });
        return type.cast(proxy);
    }

    private static Object empty(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == int.class || type == Integer.class) return 0;
        if (type == long.class || type == Long.class) return 0L;
        if (type == double.class || type == Double.class) return 0.0;
        if (type == float.class || type == Float.class) return 0.0f;
        if (type == short.class || type == Short.class) return (short) 0;
        if (type == byte.class || type == Byte.class) return (byte) 0;
        if (type == char.class || type == Character.class) return '\0';
        if (type == List.class || type == Collection.class) return Collections.emptyList();
        if (type == Set.class) return Collections.emptySet();
        if (type == Map.class) return Collections.emptyMap();
        if (type == Optional.class) return Optional.empty();
        if (type == Stream.class) return Stream.empty();
        return null;
    }
}
