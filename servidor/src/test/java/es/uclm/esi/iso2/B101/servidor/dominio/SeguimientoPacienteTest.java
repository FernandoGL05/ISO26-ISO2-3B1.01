package es.uclm.esi.iso2.B101.servidor.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SeguimientoPacienteTest {
    @Test
    void creaSeguimientoConFechasYConCamposOpcionalesAusentes() {
        SeguimientoPaciente completo = new SeguimientoPaciente(
                "s1",
                "p1",
                "Resonancia",
                "Revisión periódica",
                12,
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2027, 1, 10),
                "Acudir en ayunas");
        SeguimientoPaciente sinFechas = seguimiento("s2", null, null);

        assertEquals("s1", completo.getId());
        assertEquals("p1", completo.getPacienteId());
        assertEquals(12, completo.getPeriodicidadMeses());
        assertEquals(LocalDate.of(2027, 1, 10), completo.getFechaProximaRevision());
        assertEquals(LocalDate.of(2026, 1, 10), completo.getFechaUltimaPrueba());
        assertNull(sinFechas.getFechaProximaRevision());
        assertNull(sinFechas.getFechaUltimaPrueba());
    }

    @Test
    void validaLosDatosObligatoriosYLaPeriodicidad() {
        assertThrows(IllegalArgumentException.class, () -> new SeguimientoPaciente(
                null, "p1", "Tipo", "Descripción", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new SeguimientoPaciente(
                "s1", " ", "Tipo", "Descripción", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new SeguimientoPaciente(
                "s1", "p1", "", "Descripción", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new SeguimientoPaciente(
                "s1", "p1", "Tipo", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new SeguimientoPaciente(
                "s1", "p1", "Tipo", "Descripción", 0, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new SeguimientoPaciente(
                "s1", "p1", "Tipo", "Descripción", -3, null, null, null));
    }

    private static SeguimientoPaciente seguimiento(String id, LocalDate ultima, LocalDate proxima) {
        return new SeguimientoPaciente(id, "p1", "Prueba", "Descripción", null, ultima, proxima, null);
    }
}
