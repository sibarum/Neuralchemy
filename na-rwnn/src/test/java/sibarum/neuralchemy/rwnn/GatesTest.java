package sibarum.neuralchemy.rwnn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatesTest {

    @Test
    void truthTableEncoding() {
        // AND
        assertEquals(0, Gates.eval(Gates.AND, 0, 0));
        assertEquals(0, Gates.eval(Gates.AND, 0, 1));
        assertEquals(0, Gates.eval(Gates.AND, 1, 0));
        assertEquals(1, Gates.eval(Gates.AND, 1, 1));

        // XOR
        assertEquals(0, Gates.eval(Gates.XOR, 0, 0));
        assertEquals(1, Gates.eval(Gates.XOR, 0, 1));
        assertEquals(1, Gates.eval(Gates.XOR, 1, 0));
        assertEquals(0, Gates.eval(Gates.XOR, 1, 1));

        // NAND
        assertEquals(1, Gates.eval(Gates.NAND, 0, 0));
        assertEquals(1, Gates.eval(Gates.NAND, 0, 1));
        assertEquals(1, Gates.eval(Gates.NAND, 1, 0));
        assertEquals(0, Gates.eval(Gates.NAND, 1, 1));
    }

    @Test
    void setBitProducesValidGate() {
        byte g = Gates.AND;
        // Flip combo (0,0) bit from 0 to 1 → AND becomes "AND or-equal-zero" (XNOR of NAND... whatever)
        byte g2 = Gates.setBit(g, 0, 1);
        assertEquals(1, Gates.eval(g2, 0, 0));
        assertEquals(0, Gates.eval(g2, 0, 1));
        assertEquals(0, Gates.eval(g2, 1, 0));
        assertEquals(1, Gates.eval(g2, 1, 1));
    }
}
