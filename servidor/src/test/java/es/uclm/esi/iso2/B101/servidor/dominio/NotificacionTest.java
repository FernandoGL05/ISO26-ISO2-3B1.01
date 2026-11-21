package es.uclm.esi.iso2.B101.servidor.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NotificacionTest {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 3, 15, 10, 0);

    @Test
    void creaNotificacionesDeLosTresTipos() {
        Notificacion recordatorio = new Notificacion(
                "r1", "p1", TipoNotificacion.RECORDATORIO_CITA,
                "Tienes una cita próxima.", "c1", AHORA, AHORA.plusDays(1));
        Notificacion preparacion = new Notificacion(
                "p1", "p1", TipoNotificacion.PREPARACION_PRUEBA,
                "Acude en ayunas.", "prueba1", AHORA, null);
        Notificacion informe = new Notificacion(
                "i1", "p1", TipoNotificacion.INFORME_DISPONIBLE,
                "Tu informe está disponible.", "informe1", AHORA, null);

        assertEquals(TipoNotificacion.RECORDATORIO_CITA, recordatorio.getTipo());
        assertEquals(TipoNotificacion.PREPARACION_PRUEBA, preparacion.getTipo());
        assertEquals(TipoNotificacion.INFORME_DISPONIBLE, informe.getTipo());
        assertFalse(recordatorio.estaLeida());
    }

    @Test
    void rechazaLosDatosObligatoriosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new Notificacion(
                "", "p1", TipoNotificacion.INFORME_DISPONIBLE, "Mensaje", "i1", AHORA, null));
        assertThrows(IllegalArgumentException.class, () -> new Notificacion(
                "n1", " ", TipoNotificacion.INFORME_DISPONIBLE, "Mensaje", "i1", AHORA, null));
        assertThrows(IllegalArgumentException.class, () -> new Notificacion(
                "n1", "p1", null, "Mensaje", "i1", AHORA, null));
        assertThrows(IllegalArgumentException.class, () -> new Notificacion(
                "n1", "p1", TipoNotificacion.INFORME_DISPONIBLE, " ", "i1", AHORA, null));
        assertThrows(IllegalArgumentException.class, () -> new Notificacion(
                "n1", "p1", TipoNotificacion.INFORME_DISPONIBLE, "Mensaje", "i1", null, null));
        assertThrows(IllegalArgumentException.class, () -> new Notificacion(
                "n1", "p1", TipoNotificacion.RECORDATORIO_CITA, "Mensaje", "c1", AHORA, null));
    }
}
