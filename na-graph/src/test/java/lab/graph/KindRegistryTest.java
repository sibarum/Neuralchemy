package lab.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import lab.dsl.Type;

class KindRegistryTest {

    @Test void builtinHasStarterCatalog() {
        KindRegistry r = KindRegistry.builtin();
        assertNotNull(r.get(BuiltinKinds.SLIDER));
        assertNotNull(r.get(BuiltinKinds.OUTPUT_S));
        assertNotNull(r.get(BuiltinKinds.ADD_S));
        assertNotNull(r.get(BuiltinKinds.MUL_S));
        assertNotNull(r.get(BuiltinKinds.SPLIT));
        assertNotNull(r.get(BuiltinKinds.JOIN));
    }

    @Test void localRegistryDelegatesToBuiltin() {
        KindRegistry local = new KindRegistry();
        assertSame(KindRegistry.builtin().get(BuiltinKinds.SLIDER), local.get(BuiltinKinds.SLIDER));
        assertEquals(0, local.local().size());
    }

    @Test void duplicateRegistrationRejected() {
        KindRegistry r = new KindRegistry();
        NodeKind k = new NodeKind("x.foo", "Foo", List.of(), List.of(), Map.of());
        r.register(k);
        assertThrows(IllegalStateException.class, () -> r.register(k));
    }

    @Test void replaceUpdatesExistingEntry() {
        KindRegistry r = new KindRegistry();
        r.register(new NodeKind("x.foo", "Foo v1", List.of(), List.of(), Map.of()));
        r.replace(new NodeKind("x.foo", "Foo v2", List.of(), List.of(), Map.of()));
        assertEquals("Foo v2", r.get("x.foo").displayName());
    }

    @Test void unregisterRemoves() {
        KindRegistry r = new KindRegistry();
        r.register(new NodeKind("x.foo", "Foo", List.of(), List.of(), Map.of()));
        assertNotNull(r.unregister("x.foo"));
        assertNull(r.get("x.foo"));
    }

    @Test void duplicatePortNamesRejectedByNodeKindCtor() {
        // Port names must be unique across both directions so PortRef.portName is unambiguous.
        assertThrows(IllegalArgumentException.class, () -> new NodeKind(
                "x.bad", "Bad",
                List.of(PortSpec.required("p", Type.SCALAR, 0)),
                List.of(PortSpec.required("p", Type.SCALAR, 0)),
                Map.of()
        ));
    }

    @Test void portIndexesMustBeSequential() {
        assertThrows(IllegalArgumentException.class, () -> new NodeKind(
                "x.gappy", "Gappy",
                List.of(PortSpec.required("a", Type.SCALAR, 0),
                        PortSpec.required("b", Type.SCALAR, 5)),     // skips 1..4
                List.of(),
                Map.of()
        ));
    }

    @Test void valueZeroProducesCorrectShape() {
        assertEquals(new Value.Scalar(0f),     Value.zero(Type.SCALAR));
        assertEquals(new Value.Vec2(0f, 0f),   Value.zero(Type.VEC2));
        assertEquals(new Value.Complex(0f, 0f), Value.zero(Type.COMPLEX));
        assertEquals(new Value.Quaternion(0f, 0f, 0f, 0f), Value.zero(Type.QUATERNION));
    }

    @Test void valueTypeMatchesAlgebra() {
        assertEquals(Type.SCALAR,  new Value.Scalar(1f).type());
        assertEquals(Type.COMPLEX, new Value.Complex(1f, 0f).type());
        assertNull(new Value.Str("hello").type());
        assertTrue(new Value.Int(0).type() == null);
    }
}
