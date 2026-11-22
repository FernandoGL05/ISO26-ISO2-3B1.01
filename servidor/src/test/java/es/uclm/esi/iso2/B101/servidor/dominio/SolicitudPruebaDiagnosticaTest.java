package es.uclm.esi.iso2.B101.servidor.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SolicitudPruebaDiagnosticaTest {
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 22, 10, 0);

    @Test
    void conservaLosDatosYComienzaEnEstadoPendienteParaCadaPrioridad() {
        for (PrioridadPrueba prioridad : PrioridadPrueba.values()) {
            SolicitudPruebaDiagnostica solicitud = new SolicitudPruebaDiagnostica(
                    "solicitud-1",
                    "medico-1",
                    "paciente-1",
                    "Resonancia",
                    "Dolor persistente",
                    "Descartar lesión",
                    prioridad,
                    FECHA);

            assertEquals("solicitud-1", solicitud.getId());
            assertEquals("medico-1", solicitud.getMedicoId());
            assertEquals("paciente-1", solicitud.getPacienteId());
            assertEquals(prioridad, solicitud.getPrioridad());
            assertEquals(FECHA, solicitud.getFecha());
            assertEquals(EstadoSolicitudPrueba.PENDIENTE, solicitud.getEstado());
        }
    }

    @Test
    void rechazaCamposObligatoriosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> crear(null, "m", "p", PrioridadPrueba.URGENTE));
        assertThrows(IllegalArgumentException.class, () -> crear("id", " ", "p", PrioridadPrueba.URGENTE));
        assertThrows(IllegalArgumentException.class, () -> crear("id", "m", null, PrioridadPrueba.URGENTE));
        assertThrows(IllegalArgumentException.class, () -> crear("id", "m", "p", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SolicitudPruebaDiagnostica(
                        "id", "m", "p", " ", "indicación", "justificación",
                        PrioridadPrueba.ORDINARIA, FECHA));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SolicitudPruebaDiagnostica(
                        "id", "m", "p", "TAC", " ", "justificación",
                        PrioridadPrueba.ORDINARIA, FECHA));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SolicitudPruebaDiagnostica(
                        "id", "m", "p", "TAC", "indicación", " ",
                        PrioridadPrueba.ORDINARIA, FECHA));
    }

    private static SolicitudPruebaDiagnostica crear(
            String id, String medicoId, String pacienteId, PrioridadPrueba prioridad) {
        return new SolicitudPruebaDiagnostica(
                id, medicoId, pacienteId, "TAC", "indicación", "justificación", prioridad, FECHA);
    }
}
