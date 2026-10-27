package ar.com.biopus.abismocam;

import java.math.BigInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceIdentityTest {
    @Test public void namesHaveMinimumFourDigitsAndNoCounterWrap() {
        assertEquals("abismoCam_Li_0001", SourceIdentity.name("Li", BigInteger.ONE));
        assertEquals("abismoCam_0001", SourceIdentity.name("", BigInteger.ONE));
        assertEquals("abismoCam_Ana_10000", SourceIdentity.name("Ana", new BigInteger("9999").add(BigInteger.ONE)));
        assertEquals("abismoCam_Li_9223372036854775808", SourceIdentity.name("Li", new BigInteger("9223372036854775808")));
    }
    @Test public void validatesIdentifierAndRecoversInvalidCounter() {
        assertEquals("Li", SourceIdentity.identifier(" Li "));
        assertThrows(IllegalArgumentException.class, () -> SourceIdentity.identifier("Li/PC"));
        assertThrows(IllegalArgumentException.class, () -> SourceIdentity.identifier("x".repeat(25)));
        assertEquals(BigInteger.ONE, SourceIdentity.counter("invalid"));
        assertEquals(BigInteger.ONE, SourceIdentity.counter("0"));
        assertEquals(BigInteger.ONE, SourceIdentity.counter("-2"));
    }
}
