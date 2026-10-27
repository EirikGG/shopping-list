package no.eirikgg.shoppinglist;

import no.eirikgg.shoppinglist.web.filter.IngressFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MainTest {

    @Test
    void trustedProxy_isHomeAssistantsIngressUnlessSet() {
        assertEquals(IngressFilter.HA_INGRESS_PROXY, Main.trustedProxy(null));
        assertEquals(IngressFilter.HA_INGRESS_PROXY, Main.trustedProxy(" "));
        assertEquals("10.0.0.5", Main.trustedProxy(" 10.0.0.5 "));
    }

    @Test
    void trustedProxy_anyTrustsEveryAddress() {
        assertNull(Main.trustedProxy("any"));
        assertNull(Main.trustedProxy("ANY"));
    }
}
