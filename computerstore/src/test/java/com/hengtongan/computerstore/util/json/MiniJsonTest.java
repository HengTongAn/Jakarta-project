package com.hengtongan.computerstore.util.json;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gateway exchange has no JSON library behind it, so these tests are the
 * safety net. The QR image case is the one that matters: a base64 payload is
 * long, contains '+' and '/' and '=', and a sloppy parser would truncate it and
 * render a broken code with no error anywhere.
 */
class MiniJsonTest {

    @Test
    void roundTripsAFlatObject() {
        String json = "{\"a\":\"x\",\"b\":1}";
        Map<String, Object> parsed = MiniJson.parseObject(json);
        assertEquals("x", parsed.get("a"));
        assertEquals(1L, parsed.get("b"));
    }

    @Test
    void handlesNestedObjectsAndArrays() {
        String json = "{\"data\":{\"n\":[1,2,3],\"deep\":{\"k\":\"v\"}}}";
        Map<String, Object> parsed = MiniJson.parseObject(json);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) parsed.get("data");
        assertEquals(List.of(1L, 2L, 3L), data.get("n"));
        @SuppressWarnings("unchecked")
        Map<String, Object> deep = (Map<String, Object>) data.get("deep");
        assertEquals("v", deep.get("k"));
    }

    @Test
    void parsesAnEmptyObjectAndArray() {
        assertTrue(MiniJson.parseObject("{}").isEmpty());
        assertEquals(List.of(), MiniJson.parse("[]"));
    }

    @Test
    void distinguishesTypes() {
        Map<String, Object> parsed = MiniJson.parseObject(
                "{\"s\":\"1\",\"n\":1,\"d\":1.5,\"t\":true,\"f\":false,\"z\":null}");
        assertEquals("1", parsed.get("s"));
        assertEquals(1L, parsed.get("n"));
        assertEquals(1.5d, parsed.get("d"));
        assertEquals(Boolean.TRUE, parsed.get("t"));
        assertEquals(Boolean.FALSE, parsed.get("f"));
        assertNull(parsed.get("z"));
        assertTrue(parsed.containsKey("z"), "an explicit null must be a present key, not a missing one");
    }

    @Test
    void survivesABase64QrPayload() {
        // The realistic shape of a gateway QR field: long, and full of the
        // characters that break naive splitting on ',' or '"'.
        StringBuilder b64 = new StringBuilder();
        for (int i = 0; i < 4000; i++) {
            b64.append("ABCdef+/0123456789".charAt(i % 17));
        }
        String qr = b64.toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transId", "TX-1");
        body.put("qrImage", qr);
        String written = MiniJson.write(body);
        assertEquals(qr, MiniJson.parseObject(written).get("qrImage"));
    }

    @Test
    void escapesAndUnescapesCorrectly() {
        String awkward = "quote\" backslash\\ newline\n tab\t unicodeé emoji😀";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("description", awkward);
        String written = MiniJson.write(body);
        assertEquals(awkward, MiniJson.parseObject(written).get("description"));
    }

    @Test
    void escapesControlCharactersAsUnicode() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("k", "a\u0001b");
        String written = MiniJson.write(body);
        assertTrue(written.contains("\\u0001"), "control chars must be escaped, got: " + written);
        assertEquals("a\u0001b", MiniJson.parseObject(written).get("k"));
    }

    @Test
    void toleratesWhitespaceEverywhere() {
        Map<String, Object> parsed = MiniJson.parseObject(
                "  {\n  \"a\" :  \"x\" ,\n  \"b\" : [ 1 , 2 ]\n}  ");
        assertEquals("x", parsed.get("a"));
        assertEquals(List.of(1L, 2L), parsed.get("b"));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"a\":\"x\""));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"a\":1}trailing"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse(""));
    }

    @Test
    void rejectsATopLevelNonObject() {
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parseObject("[1,2]"));
    }

    @Test
    void firstStringPrefersTheEarlierKeyAndSkipsBlanks() {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("TRANS_ID", "  ");
        obj.put("transId", "TX-9");
        assertEquals("TX-9", MiniJson.firstString(obj, "TRANS_ID", "transId"));
    }

    @Test
    void firstStringIgnoresNestedValues() {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("transId", Map.of("inner", "nope"));
        assertNull(MiniJson.firstString(obj, "transId"));
    }
}
