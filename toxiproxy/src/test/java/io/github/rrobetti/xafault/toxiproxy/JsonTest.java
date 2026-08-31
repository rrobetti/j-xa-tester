package io.github.rrobetti.xafault.toxiproxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void writesAndReadsPrimitives() {
        assertEquals("null", Json.write(null));
        assertEquals("true", Json.write(true));
        assertEquals("false", Json.write(false));
        assertEquals("42", Json.write(42L));
        assertEquals("3.5", Json.write(3.5));

        assertNull(Json.read("null"));
        assertEquals(true, Json.read("true"));
        assertEquals(42L, Json.read("42"));
        assertEquals(3.5, Json.read("3.5"));
    }

    @Test
    void escapesAndUnescapesSpecialCharactersInStrings() {
        String value = "line1\nline2\ttab\"quote\\backslash";
        String written = Json.write(value);
        assertEquals("\"line1\\nline2\\ttab\\\"quote\\\\backslash\"", written);
        assertEquals(value, Json.read(written));
    }

    @Test
    void roundTripsNestedObjectsAndArrays() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("latency", 100L);
        attributes.put("jitter", 10L);

        Map<String, Object> toxic = new LinkedHashMap<>();
        toxic.put("name", "slow-down");
        toxic.put("enabled", true);
        toxic.put("toxicity", 1.0);
        toxic.put("attributes", attributes);
        toxic.put("tags", List.of("a", "b", "c"));
        toxic.put("nothing", null);

        String written = Json.write(toxic);
        Object parsed = Json.read(written);

        assertTrue(parsed instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedMap = (Map<String, Object>) parsed;
        assertEquals("slow-down", parsedMap.get("name"));
        assertEquals(true, parsedMap.get("enabled"));
        assertEquals(1.0, parsedMap.get("toxicity"));
        assertEquals(List.of("a", "b", "c"), parsedMap.get("tags"));
        assertTrue(parsedMap.containsKey("nothing"));
        assertNull(parsedMap.get("nothing"));

        @SuppressWarnings("unchecked")
        Map<String, Object> parsedAttributes = (Map<String, Object>) parsedMap.get("attributes");
        assertEquals(100L, parsedAttributes.get("latency"));
        assertEquals(10L, parsedAttributes.get("jitter"));
    }

    @Test
    void parsesEmptyObjectsAndArrays() {
        assertEquals(Map.of(), Json.read("{}"));
        assertEquals(List.of(), Json.read("[]"));
        assertEquals(Map.of(), Json.read("  {  }  "));
    }

    @Test
    void rejectsMalformedInput() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> Json.read("{"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> Json.read("[1,2"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> Json.read("{\"a\":1} garbage"));
    }
}
