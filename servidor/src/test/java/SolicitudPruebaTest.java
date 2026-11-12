import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class SolicitudPruebaTest {
    @Test
    void sePuedeCrearUnaSolicitudPrueba() {
        assertNotNull(new SolicitudPrueba());
    }
}
