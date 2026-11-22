package es.uclm.esi.iso2.B101.servidor.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadPrueba;
import es.uclm.esi.iso2.B101.servidor.dominio.SolicitudPruebaDiagnostica;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositorioPrescripcionesDiagnosticasMemoriaTest {
    private final RepositorioPrescripcionesDiagnosticasMemoria repositorio =
            new RepositorioPrescripcionesDiagnosticasMemoria();

    @Test
    void guardaBuscaYSeparaLasSolicitudesPorMedico() {
        SolicitudPruebaDiagnostica propia = solicitud("s-1", "m-1");
        SolicitudPruebaDiagnostica ajena = solicitud("s-2", "m-2");
        repositorio.guardar(propia);
        repositorio.guardar(ajena);

        assertEquals(propia, repositorio.buscarPorId("s-1").orElseThrow());
        assertEquals(List.of(propia), repositorio.buscarPorMedicoId("m-1"));
        assertEquals(List.of(ajena), repositorio.buscarPorMedicoId("m-2"));
        assertTrue(repositorio.buscarPorMedicoId("m-sin-solicitudes").isEmpty());
        assertFalse(repositorio.buscarPorId("ausente").isPresent());
        assertThrows(UnsupportedOperationException.class, () -> repositorio.buscarPorMedicoId("m-1").clear());
    }

    @Test
    void rechazaSolicitudesNulasIdsDuplicadosEIdentificadoresInvalidos() {
        SolicitudPruebaDiagnostica solicitud = solicitud("s-1", "m-1");
        assertThrows(IllegalArgumentException.class, () -> repositorio.guardar(null));
        repositorio.guardar(solicitud);
        assertThrows(IllegalStateException.class, () -> repositorio.guardar(solicitud("s-1", "m-2")));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorId(" "));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorMedicoId(null));
    }

    private static SolicitudPruebaDiagnostica solicitud(String id, String medicoId) {
        return new SolicitudPruebaDiagnostica(
                id,
                medicoId,
                "paciente-1",
                "TAC",
                "Indicación",
                "Justificación",
                PrioridadPrueba.PREFERENTE,
                LocalDateTime.of(2026, 11, 22, 9, 0));
    }
}
