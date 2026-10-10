package shortestpath.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ItemCatalogTest {
    @Test
    void resolvesIdsAndSearchesNames() {
        assertEquals("995", ItemCatalog.find(null, "995").get(0).key);
        assertTrue(ItemCatalog.find("coins", null).stream().anyMatch(item -> "995".equals(item.key)));
        assertTrue(ItemCatalog.find("teleport to house", null).stream()
            .anyMatch(item -> "8013".equals(item.key) && "Teleport to house".equals(item.name)));
        assertFalse(ItemCatalog.find("teleporttohouse", null).stream().anyMatch(item -> "8013".equals(item.key)));
        assertTrue(ItemCatalog.find("dwarf remains", null).isEmpty());
    }

    @Test
    void includesItemsCheckedOutsideTransportData() {
        assertEquals("Dramen staff", ItemCatalog.find(null, "772").get(0).name);
        assertTrue(ItemCatalog.find("lunar staff", null).stream().anyMatch(item -> "9084".equals(item.key)));
        assertTrue(ItemCatalog.find("rune pouch", null).stream().anyMatch(item -> "12791".equals(item.key)));
    }
}
