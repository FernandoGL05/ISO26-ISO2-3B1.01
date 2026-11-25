package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CitaDuracionTest {
    private static final LocalDateTime FECHA =
            LocalDateTime.of(2026, 12, 3, 10, 0);

    @Test
    void conservaLaDuracionIndicada() {
        Cita cita = new Cita(
                "cita-1", "paciente-1", "medico-1", "TAC", FECHA, 45);

        assertEquals(45, cita.getDuracionMinutos().orElseThrow());
        assertEquals(FECHA, cita.getFechaHora());
        assertEquals("medico-1", cita.getMedicoId());
        assertEquals(EstadoCita.PROGRAMADA, cita.getEstado());

        cita.confirmar();

        assertEquals(45, cita.getDuracionMinutos().orElseThrow());
        assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
    }

    @Test
    void constructoresAnterioresMantienenDuracionDesconocida() {
        Cita sinMedico = new Cita(
                "cita-1", "paciente-1", "TAC", FECHA);

        Cita conMedico = new Cita(
                "cita-2", "paciente-1", "medico-1", "TAC", FECHA);

        assertTrue(sinMedico.getDuracionMinutos().isEmpty());
        assertTrue(conMedico.getDuracionMinutos().isEmpty());
        assertNull(sinMedico.getMedicoId());
        assertEquals("medico-1", conMedico.getMedicoId());
    }

    @Test
    void aceptaLosLimitesDeDuracion() {
        Cita minima = new Cita(
                "cita-1", "paciente-1", "medico-1", "TAC", FECHA, 1);

        Cita maxima = new Cita(
                "cita-2", "paciente-2", "medico-1", "TAC", FECHA, 1440);

        assertEquals(1, minima.getDuracionMinutos().orElseThrow());
        assertEquals(1440, maxima.getDuracionMinutos().orElseThrow());
    }

    @Test
    void rechazaDuracionesFueraDeLosLimites() {
        for (int duracion : new int[] {-1, 0, 1441}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Cita(
                            "cita-1", "paciente-1", "medico-1",
                            "TAC", FECHA, duracion));
        }
    }
}