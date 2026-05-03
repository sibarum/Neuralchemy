package lab.project.codec;

import lab.graph.Value;
import lab.project.json.JsonReader;
import lab.project.json.JsonWriter;

/**
 * JSON codec for {@link Value}. The shape is tagged-union — every value object carries a
 * {@code type} field, plus the components (named exactly as the record's accessors so the
 * format reads naturally on disk):
 *
 * <pre>{@code
 * { "type": "scalar",       "v": 0.5 }
 * { "type": "vec2",         "x": 1.0, "y": 2.0 }
 * { "type": "complex",      "re": 1.0, "im": 0.5 }
 * { "type": "splitcomplex", "re": 1.0, "im": 0.5 }
 * { "type": "quaternion",   "w": 1, "x": 0, "y": 0, "z": 0 }
 * { "type": "coquat",       "a": 1, "b": 0, "c": 0, "d": 0 }
 * { "type": "mat2",         "a00": 1, "a01": 0, "a10": 0, "a11": 1 }
 * { "type": "str",          "s": "hello" }
 * { "type": "int",          "n": 42 }
 * }</pre>
 *
 * <p>Per project-format.md: skip unknown fields silently; missing fields default to zero / "".
 */
public final class ValueCodec {

    private ValueCodec() {}

    public static void write(Value v, JsonWriter w) {
        w.beginObject();
        switch (v) {
            case Value.Scalar s -> w.name("type").value("scalar")
                    .name("v").value(s.v());
            case Value.Vec2 vv -> w.name("type").value("vec2")
                    .name("x").value(vv.x()).name("y").value(vv.y());
            case Value.Complex c -> w.name("type").value("complex")
                    .name("re").value(c.re()).name("im").value(c.im());
            case Value.SplitComplex c -> w.name("type").value("splitcomplex")
                    .name("re").value(c.re()).name("im").value(c.im());
            case Value.Quaternion q -> w.name("type").value("quaternion")
                    .name("w").value(q.w()).name("x").value(q.x())
                    .name("y").value(q.y()).name("z").value(q.z());
            case Value.Coquat q -> w.name("type").value("coquat")
                    .name("a").value(q.a()).name("b").value(q.b())
                    .name("c").value(q.c()).name("d").value(q.d());
            case Value.Mat2 m -> w.name("type").value("mat2")
                    .name("a00").value(m.a00()).name("a01").value(m.a01())
                    .name("a10").value(m.a10()).name("a11").value(m.a11());
            case Value.Str s -> w.name("type").value("str")
                    .name("s").value(s.s());
            case Value.Int i -> w.name("type").value("int")
                    .name("n").value((long) i.n());
        }
        w.endObject();
    }

    public static Value read(JsonReader r) {
        r.beginObject();
        // Order on disk isn't guaranteed; collect every component then construct at the end.
        String type = "scalar";
        String s = "";
        long n = 0;
        float v=0, x=0, y=0, re=0, im=0, w=0, z=0;
        float a=0, b=0, c=0, d=0;
        float a00=0, a01=0, a10=0, a11=0;
        while (r.hasNext()) {
            String name = r.nextName();
            switch (name) {
                case "type" -> type = r.nextString();
                case "v"    -> v   = (float) r.nextDouble();
                case "x"    -> x   = (float) r.nextDouble();
                case "y"    -> y   = (float) r.nextDouble();
                case "z"    -> z   = (float) r.nextDouble();
                case "w"    -> w   = (float) r.nextDouble();
                case "re"   -> re  = (float) r.nextDouble();
                case "im"   -> im  = (float) r.nextDouble();
                case "a"    -> a   = (float) r.nextDouble();
                case "b"    -> b   = (float) r.nextDouble();
                case "c"    -> c   = (float) r.nextDouble();
                case "d"    -> d   = (float) r.nextDouble();
                case "a00"  -> a00 = (float) r.nextDouble();
                case "a01"  -> a01 = (float) r.nextDouble();
                case "a10"  -> a10 = (float) r.nextDouble();
                case "a11"  -> a11 = (float) r.nextDouble();
                case "s"    -> s   = r.nextString();
                case "n"    -> n   = r.nextLong();
                default     -> r.skipValue();
            }
        }
        r.endObject();
        return switch (type) {
            case "scalar"       -> new Value.Scalar(v);
            case "vec2"         -> new Value.Vec2(x, y);
            case "complex"      -> new Value.Complex(re, im);
            case "splitcomplex" -> new Value.SplitComplex(re, im);
            case "quaternion"   -> new Value.Quaternion(w, x, y, z);
            case "coquat"       -> new Value.Coquat(a, b, c, d);
            case "mat2"         -> new Value.Mat2(a00, a01, a10, a11);
            case "str"          -> new Value.Str(s);
            case "int"          -> new Value.Int((int) n);
            // Forward-compatibility: unknown type → safe scalar zero (loader logs but doesn't crash).
            default             -> new Value.Scalar(0f);
        };
    }
}
