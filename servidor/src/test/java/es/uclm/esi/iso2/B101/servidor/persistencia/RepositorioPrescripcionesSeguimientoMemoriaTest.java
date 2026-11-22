package es.uclm.esi.iso2.B101.servidor.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.NecesidadCitaSeguimiento;
import es.uclm.esi.iso2.B101.servidor.dominio.PrescripcionSeguimiento;
import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadSeguimiento;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositorioPrescripcionesSeguimientoMemoriaTest {
    @Test
    void guardaYFiltraPrescripcionesPorMedico() {
        RepositorioPrescripcionesSeguimientoMemoria repositorio =
                new RepositorioPrescripcionesSeguimientoMemoria();
        PrescripcionSeguimiento una = prescripcion("p-1", "m-1");
        PrescripcionSeguimiento otra = prescripcion("p-2", "m-2");
        repositorio.guardar(una);
        repositorio.guardar(otra);

        assertEquals(una, repositorio.buscarPorId("p-1").orElseThrow());
        assertEquals(List.of(una), repositorio.buscarPorMedicoId("m-1"));
        assertEquals(2, repositorio.buscarTodas().size());
        assertTrue(repositorio.buscarPorMedicoId("sin-prescripciones").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> repositorio.buscarTodas().clear());
        assertThrows(IllegalStateException.class, () -> repositorio.guardar(prescripcion("p-1", "m-2")));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorId(" "));
    }

    @Test
    void haceUnicaLaCombinacionPrescripcionYFechaPrevista() {
        RepositorioNecesidadesCitaSeguimientoMemoria repositorio =
                new RepositorioNecesidadesCitaSeguimientoMemoria();
        LocalDate fecha = LocalDate.of(2028, 2, 29);
        NecesidadCitaSeguimiento primera = necesidad("n-1", "pres-1", fecha);

        assertTrue(repositorio.guardarSiNoExiste(primera));
        assertFalse(repositorio.guardarSiNoExiste(necesidad("n-2", "pres-1", fecha)));
        assertEquals(primera, repositorio.buscarPorPrescripcionYFecha("pres-1", fecha).orElseThrow());
        assertEquals(List.of(primera), repositorio.buscarPorPrescripcionId("pres-1"));
        assertTrue(repositorio.guardarSiNoExiste(necesidad("n-2", "pres-1", fecha.plusDays(1))));
        assertThrows(
                IllegalStateException.class,
                () -> repositorio.guardarSiNoExiste(necesidad("n-1", "pres-1", fecha.plusDays(2))));
        assertThrows(IllegalArgumentException.class, () -> repositorio.guardarSiNoExiste(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> repositorio.buscarPorPrescripcionYFecha("pres-1", null));
    }

    private static PrescripcionSeguimiento prescripcion(String id, String medicoId) {
        return new PrescripcionSeguimiento(
                id,
                medicoId,
                "paciente-1",
                "TAC",
                "Control clínico",
                6,
                LocalDate.of(2028, 1, 31),
                PrioridadSeguimiento.PREFERENTE,
                LocalDate.of(2028, 1, 1));
    }

    private static NecesidadCitaSeguimiento necesidad(String id, String prescripcionId, LocalDate fecha) {
        return new NecesidadCitaSeguimiento(
                id, prescripcionId, "m-1", "paciente-1", "TAC", fecha);
    }
}
