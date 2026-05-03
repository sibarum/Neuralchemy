package lab.project.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

class JsonRoundTripTest {

    private static String write(java.util.function.Consumer<JsonWriter> body) {
        StringWriter sw = new StringWriter();
        try (JsonWriter w = new JsonWriter(sw)) {
            body.accept(w);
        }
        return sw.toString();
    }

    @Test void singleScalarObjectRoundTrip() {
        String s = write(w -> w.beginObject()
                .name("id").value("net-001")
                .name("zoom").value(1.25)
                .name("count").value(42L)
                .name("active").value(true)
                .endObject());
        try (JsonReader r = new JsonReader(new StringReader(s))) {
            r.beginObject();
            assertEquals("id",     r.nextName()); assertEquals("net-001", r.nextString());
            assertEquals("zoom",   r.nextName()); assertEquals(1.25, r.nextDouble(), 1e-9);
            assertEquals("count",  r.nextName()); assertEquals(42L, r.nextLong());
            assertEquals("active", r.nextName()); assertEquals(true, r.nextBoolean());
            assertFalse(r.hasNext());
            r.endObject();
        }
    }

    @Test void nestedArraysAndObjects() {
        String s = write(w -> w.beginObject()
                .name("nodes").beginArray()
                    .beginObject().name("kind").value("a").endObject()
                    .beginObject().name("kind").value("b").endObject()
                .endArray()
            .endObject());
        try (JsonReader r = new JsonReader(new StringReader(s))) {
            r.beginObject();
            assertEquals("nodes", r.nextName());
            r.beginArray();
            int seen = 0;
            while (r.hasNext()) {
                r.beginObject();
                assertEquals("kind", r.nextName());
                String k = r.nextString();
                assertTrue(k.equals("a") || k.equals("b"));
                r.endObject();
                seen++;
            }
            r.endArray();
            r.endObject();
            assertEquals(2, seen);
        }
    }

    @Test void skipValueIgnoresUnknownDeep() {
        String s = "{\"known\": 1, \"weird\": { \"a\": [1, 2, 3], \"b\": null }, \"also\": 2}";
        try (JsonReader r = new JsonReader(new StringReader(s))) {
            r.beginObject();
            int known = 0, also = 0;
            while (r.hasNext()) {
                String name = r.nextName();
                switch (name) {
                    case "known" -> known = (int) r.nextLong();
                    case "also"  -> also  = (int) r.nextLong();
                    default      -> r.skipValue();
                }
            }
            r.endObject();
            assertEquals(1, known);
            assertEquals(2, also);
        }
    }

    @Test void escapeSequencesRoundTrip() {
        String s = write(w -> w.beginObject().name("text").value("hi\n\"quoted\"\\").endObject());
        try (JsonReader r = new JsonReader(new StringReader(s))) {
            r.beginObject();
            assertEquals("text", r.nextName());
            assertEquals("hi\n\"quoted\"\\", r.nextString());
            r.endObject();
        }
    }

    @Test void scientificNumbers() {
        String s = "{\"a\": 1.5e-3, \"b\": -2.5e2}";
        try (JsonReader r = new JsonReader(new StringReader(s))) {
            r.beginObject();
            assertEquals("a", r.nextName());
            assertEquals(1.5e-3, r.nextDouble(), 1e-12);
            assertEquals("b", r.nextName());
            assertEquals(-2.5e2, r.nextDouble(), 1e-9);
            r.endObject();
        }
    }

    @Test void prettyOutputIsValidJson() {
        String s = write(w -> w.beginObject()
                .name("a").value(1L)
                .name("b").beginArray().value(2L).value(3L).endArray()
                .endObject());
        // Just confirm it's valid by re-parsing.
        try (JsonReader r = new JsonReader(new StringReader(s))) {
            r.beginObject();
            assertEquals("a", r.nextName()); assertEquals(1, r.nextLong());
            assertEquals("b", r.nextName());
            r.beginArray();
            assertEquals(2, r.nextLong());
            assertEquals(3, r.nextLong());
            r.endArray();
            r.endObject();
        }
    }

    @Test void malformedInputThrows() {
        assertThrows(JsonException.class, () -> {
            try (JsonReader r = new JsonReader(new StringReader("{\"a\":}"))) {
                r.beginObject();
                r.nextName();
                r.nextString();
            }
        });
    }
}
