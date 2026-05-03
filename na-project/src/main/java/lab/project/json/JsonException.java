package lab.project.json;

/**
 * Thrown by {@link JsonReader} on malformed input or by {@link JsonWriter} on illegal call
 * sequences ({@code endObject()} when the current container is an array, etc.). The message
 * carries enough context to point at a region of source — line + column for reads.
 */
public final class JsonException extends RuntimeException {
    public JsonException(String msg)               { super(msg); }
    public JsonException(String msg, Throwable t)  { super(msg, t); }
}
