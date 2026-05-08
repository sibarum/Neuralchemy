package sibarum.neuralchemy.rwnn;

/**
 * 2-input boolean gate types encoded as a 4-bit truth table in a single byte.
 * Bit at position {@code c = a*2 + b} of the byte holds the gate's output for
 * inputs {@code (a, b)}.
 */
public final class Gates {

    private Gates() {}

    public static final byte FALSE = 0b0000;
    public static final byte AND   = 0b1000;  // 8
    public static final byte A_GT_B = 0b0100; // 4
    public static final byte A     = 0b1100;  // 12
    public static final byte A_LT_B = 0b0010; // 2
    public static final byte B     = 0b1010;  // 10
    public static final byte XOR   = 0b0110;  // 6
    public static final byte OR    = 0b1110;  // 14
    public static final byte NOR   = 0b0001;  // 1
    public static final byte XNOR  = 0b1001;  // 9
    public static final byte NOT_B = 0b0101;  // 5
    public static final byte A_GE_B = 0b1101; // 13
    public static final byte NOT_A = 0b0011;  // 3
    public static final byte A_LE_B = 0b1011; // 11
    public static final byte NAND  = 0b0111;  // 7
    public static final byte TRUE  = 0b1111;  // 15

    public static int eval(byte gateType, int a, int b) {
        return (gateType >>> (a * 2 + b)) & 1;
    }

    public static byte setBit(byte gateType, int combo, int value) {
        if (value == 0) {
            return (byte) (gateType & ~(1 << combo));
        } else {
            return (byte) ((gateType | (1 << combo)) & 0x0F);
        }
    }

    public static int getBit(byte gateType, int combo) {
        return (gateType >>> combo) & 1;
    }
}
