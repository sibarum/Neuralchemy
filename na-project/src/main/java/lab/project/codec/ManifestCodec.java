package lab.project.codec;

import java.util.ArrayList;
import java.util.List;

import lab.project.Manifest;
import lab.project.json.JsonReader;
import lab.project.json.JsonWriter;

/**
 * JSON codec for {@link Manifest}. Keys match project-format.md §Manifest verbatim
 * ({@code format}, {@code format_version}, {@code created}, etc.). Skip-unknown on read,
 * write-only-known on write.
 */
public final class ManifestCodec {

    private ManifestCodec() {}

    public static void write(Manifest m, JsonWriter w) {
        w.beginObject();
        w.name("format").value(m.format());
        w.name("format_version").value((long) m.formatVersion());
        w.name("created").value(m.created());
        w.name("last_saved").value(m.lastSaved());
        w.name("name").value(m.name());
        w.name("description").value(m.description());
        w.name("min_app_version").value(m.minAppVersion());

        w.name("contents").beginObject();
        w.name("networks").beginArray();
        for (String path : m.contents().networks()) w.value(path);
        w.endArray();
        w.endObject();

        w.endObject();
    }

    public static Manifest read(JsonReader r) {
        String format = Manifest.FORMAT;
        int version = Manifest.FORMAT_VERSION;
        String name = "", desc = "", created = "", lastSaved = "", minVer = Manifest.MIN_APP_VERSION;
        List<String> networks = new ArrayList<>();

        r.beginObject();
        while (r.hasNext()) {
            switch (r.nextName()) {
                case "format"          -> format    = r.nextString();
                case "format_version"  -> version   = (int) r.nextLong();
                case "created"         -> created   = r.nextString();
                case "last_saved"      -> lastSaved = r.nextString();
                case "name"            -> name      = r.nextString();
                case "description"     -> desc      = r.nextString();
                case "min_app_version" -> minVer    = r.nextString();
                case "contents" -> {
                    r.beginObject();
                    while (r.hasNext()) {
                        switch (r.nextName()) {
                            case "networks" -> {
                                r.beginArray();
                                while (r.hasNext()) networks.add(r.nextString());
                                r.endArray();
                            }
                            default -> r.skipValue();
                        }
                    }
                    r.endObject();
                }
                default -> r.skipValue();
            }
        }
        r.endObject();

        return new Manifest(format, version, name, desc, created, lastSaved, minVer,
                new Manifest.Contents(networks));
    }
}
