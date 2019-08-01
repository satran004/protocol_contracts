package org.aion.unity.decimal.model;

import java.math.BigInteger;

@SuppressWarnings({"unused", "WeakerAccess"})
public class DecimalCoin {

    // class settings
    // ==============
    private final static int precision = 18;
    // bytes required to represent the above precision
    // Ceiling[Log2[999 999 999 999 999 999]]
    private final static int DecimalPrecisionBits = 60;

    private static BigInteger precisionInt = BigInteger.TEN.pow(precision);
    private final BigInteger value;

    private DecimalCoin(BigInteger v) {
        assert (v != null);
        assert (v.signum() == 1 || v.signum() == 0); // coin must be either positive or 0

        this.value = v;
    }

    public static DecimalCoin valueOf(long v) {
        // important to do the decimal expansion here!
        return new DecimalCoin(BigInteger.valueOf(v).multiply(precisionInt));
    }

    public BigInteger getTruncated() {
        return chopPrecisionAndTruncate(value);
    }

    /**
     * If the value {@code BigInteger} is out of the range of the {@code long} type,
     * then an {@code ArithmeticException} is thrown.
     */
    public long longValue() {
        return value.longValueExact();
    }

    // common values
    public static DecimalCoin ZERO = valueOf(0);
    public static DecimalCoin ONE = valueOf(1);
    public static DecimalCoin SMALLEST_DECIMAL = valueOf(1);

    // utility functions
    public boolean equals(DecimalCoin d) { return value.compareTo(d.value) == 0; }
    public boolean greaterThan(DecimalCoin d) { return value.compareTo(d.value) > 0; }
    public boolean greaterThanOrEqualTo(DecimalCoin d) { return value.compareTo(d.value) >= 0; }
    public boolean lessThan(DecimalCoin d) { return value.compareTo(d.value) < 0; }
    public boolean lessThanOrEqualTo(DecimalCoin d) { return value.compareTo(d.value) <= 0; }

    // addition
    public DecimalCoin add(DecimalCoin d) {
        BigInteger r = value.add(d.value);

        if (r.bitLength() > 255+DecimalPrecisionBits)
            throw new RuntimeException("Int overflow");

        return new DecimalCoin(r);
    }

    // subtraction
    public DecimalCoin subtract(DecimalCoin d) {
        BigInteger r = value.subtract(d.value);

        if (r.bitLength() > 255+DecimalPrecisionBits)
            throw new RuntimeException("Int overflow");

        return new DecimalCoin(r);
    }

    // multiplication truncate
    public DecimalCoin multiplyTruncate(DecimalCoin d) {
        // multiply precision twice
        BigInteger mul = value.multiply(d.value);
        BigInteger chopped = chopPrecisionAndTruncate(mul);

        if (chopped.bitLength() > 255+DecimalPrecisionBits)
            throw new RuntimeException("Int overflow");

        return new DecimalCoin(chopped);
    }

    // multiplication truncate
    public DecimalCoin divideTruncate(DecimalCoin d) {
        // multiply precision twice
        BigInteger mul = value.multiply(precisionInt).multiply(precisionInt);
        BigInteger quo = mul.divide(d.value);
        BigInteger chopped = chopPrecisionAndTruncate(quo);

        if (chopped.bitLength() > 255+DecimalPrecisionBits)
            throw new RuntimeException("Int overflow");

        return new DecimalCoin(chopped);
    }

    private BigInteger chopPrecisionAndTruncate(BigInteger d) {
        return d.divide(precisionInt);
    }
}
