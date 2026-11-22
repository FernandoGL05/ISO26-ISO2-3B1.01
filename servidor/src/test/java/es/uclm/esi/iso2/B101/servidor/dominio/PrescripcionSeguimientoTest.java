package es.uclm.esi.iso2.B101.servidor.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PrescripcionSeguimientoTest {
    @Test
    void creaPrescripcionActivaParaCadaPrioridad() {
        for (PrioridadSeguimiento prioridad : PrioridadSeguimiento.values()) {
            PrescripcionSeguimiento prescripcion = new PrescripcionSeguimiento(
                    "pres-1",
                    "medico-1",
                    "paciente-1",
                    "Resonancia",
                    "Control de lesión",
                    3,
                    LocalDate.of(2028, 2, 29),
                    prioridad,
                    LocalDate.of(2028, 1, 1));

            assertEquals(prioridad, prescripcion.getPrioridad());
            assertEquals(EstadoPrescripcionSeguimiento.ACTIVA, prescripcion.getEstado());
            assertEquals(LocalDate.of(2028, 2, 29), prescripcion.getFechaPrimeraRevision());
            assertEquals(3, prescripcion.getPeriodicidadMeses());
        }
    }

    @Test
    void validaDatosObligatoriosPeriodicidadFechasYPrioridad() {
        assertThrows(IllegalArgumentException.class, () -> nueva(null, "m", "p", 1, PrioridadSeguimiento.URGENTE));
        assertThrows(IllegalArgumentException.class, () -> nueva("id", " ", "p", 1, PrioridadSeguimiento.URGENTE));
        assertThrows(IllegalArgumentException.class, () -> nueva("id", "m", null, 1, PrioridadSeguimiento.URGENTE));
        assertThrows(IllegalArgumentException.class, () -> nueva("id", "m", "p", 0, PrioridadSeguimiento.URGENTE));
        assertThrows(IllegalArgumentException.class, () -> nueva("id", "m", "p", -2, PrioridadSeguimiento.URGENTE));
        assertThrows(IllegalArgumentException.class, () -> nueva("id", "m", "p", 1, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PrescripcionSeguimiento(
                        "id", "m", "p", "TAC", " ", 1, LocalDate.now(),
                        PrioridadSeguimiento.ORDINARIA, LocalDate.now()));
    }

    @Test
    void necesidadSeMantienePendienteYNoRepresentaUnaCitaAsignada() {
        NecesidadCitaSeguimiento necesidad = new NecesidadCitaSeguimiento(
                "necesidad-1",
                "pres-1",
                "medico-1",
                "paciente-1",
                "TAC",
                LocalDate.of(2028, 3, 31));

        assertEquals(EstadoNecesidadCitaSeguimiento.PENDIENTE_PROGRAMACION, necesidad.getEstado());
        assertEquals(null, necesidad.getCitaId());
    }

    private static PrescripcionSeguimiento nueva(
            String id, String medicoId, String pacienteId, int periodicidad, PrioridadSeguimiento prioridad) {
        return new PrescripcionSeguimiento(
                id,
                medicoId,
                pacienteId,
                "TAC",
                "Control",
                periodicidad,
                LocalDate.of(2028, 3, 1),
                prioridad,
                LocalDate.of(2028, 1, 1));
    }
}
