package es.uclm.esi.iso2.B101.servidor.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class IncidenciaTest {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 21, 12, 0);

    @Test
    void creaIncidenciaRecibidaConOCitaRelacionada() {
        Incidencia conCita = crearIncidencia("cita-123");
        Incidencia sinCita = crearIncidencia(null);

        assertEquals(EstadoIncidencia.RECIBIDA, conCita.getEstado());
        assertEquals("cita-123", conCita.getCitaId());
        assertEquals(EstadoIncidencia.RECIBIDA, sinCita.getEstado());
        assertNull(sinCita.getCitaId());
    }

    @Test
    void validaLosCamposObligatoriosYLosLimitesDeLongitud() {
        assertThrows(IllegalArgumentException.class, () -> new Incidencia(
                null, "paciente", "Asunto", "Descripción", null, AHORA));
        assertThrows(IllegalArgumentException.class, () -> new Incidencia(
                "id", null, "Asunto", "Descripción", null, AHORA));
        assertThrows(IllegalArgumentException.class, () -> new Incidencia(
                "id", "paciente", " ", "Descripción", null, AHORA));
        assertThrows(IllegalArgumentException.class, () -> new Incidencia(
                "id", "paciente", "Asunto", "", null, AHORA));
        assertThrows(IllegalArgumentException.class, () -> new Incidencia(
                "id", "paciente", "x".repeat(Incidencia.MAX_LONGITUD_ASUNTO + 1), "Descripción", null, AHORA));
        assertThrows(IllegalArgumentException.class, () -> new Incidencia(
                "id",
                "paciente",
                "Asunto",
                "x".repeat(Incidencia.MAX_LONGITUD_DESCRIPCION + 1),
                null,
                AHORA));
        assertThrows(IllegalArgumentException.class, () -> new Incidencia(
                "id", "paciente", "Asunto", "Descripción", " ", AHORA));
        assertThrows(IllegalArgumentException.class, () -> new Incidencia(
                "id", "paciente", "Asunto", "Descripción", null, null));
    }

    @Test
    void aceptaLosLimitesMaximosDeAsuntoYDescripcion() {
        Incidencia incidencia = new Incidencia(
                "id",
                "paciente",
                "a".repeat(Incidencia.MAX_LONGITUD_ASUNTO),
                "d".repeat(Incidencia.MAX_LONGITUD_DESCRIPCION),
                null,
                AHORA);

        assertEquals(Incidencia.MAX_LONGITUD_ASUNTO, incidencia.getAsunto().length());
        assertEquals(Incidencia.MAX_LONGITUD_DESCRIPCION, incidencia.getDescripcion().length());
    }

    private Incidencia crearIncidencia(String citaId) {
        return new Incidencia("id-1", "paciente-1", "Problema con la cita", "No aparece confirmada.", citaId, AHORA);
    }
}
