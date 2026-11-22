package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.EstadoSolicitudPrueba;
import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadPrueba;
import es.uclm.esi.iso2.B101.servidor.dominio.SolicitudPruebaDiagnostica;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioPrescripcionesDiagnosticasMemoria;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioPrescripcionesDiagnosticasTest {
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 22, 9, 30);

    private RepositorioPrescripcionesDiagnosticasMemoria repositorio;
    private ServicioPrescripcionesDiagnosticas servicio;

    @BeforeEach
    void prepararServicio() {
        repositorio = new RepositorioPrescripcionesDiagnosticasMemoria();
        AtomicInteger siguienteId = new AtomicInteger();
        servicio = new ServicioPrescripcionesDiagnosticas(
                repositorio,
                Clock.fixed(Instant.parse("2026-11-22T09:30:00Z"), ZoneOffset.UTC),
                (medico, paciente) -> !paciente.equals("sin-permiso"),
                () -> "solicitud-" + siguienteId.incrementAndGet());
    }

    @Test
    void creaConFechaControladaEstadoPendienteYCadaPrioridad() {
        for (PrioridadPrueba prioridad : PrioridadPrueba.values()) {
            SolicitudPruebaDiagnostica solicitud =
                    servicio.prescribir("medico-1", "paciente-1", "TAC", "Dolor", "Descartar lesión", prioridad);

            assertEquals(prioridad, solicitud.getPrioridad());
            assertEquals(FECHA, solicitud.getFecha());
            assertEquals(EstadoSolicitudPrueba.PENDIENTE, solicitud.getEstado());
            assertTrue(repositorio.buscarPorId(solicitud.getId()).isPresent());
        }
    }

    @Test
    void aplicaPoliticaAntesDeGuardarYNoExponeSolicitudesDeOtroMedico() {
        servicio.prescribir("medico-1", "paciente-1", "TAC", "Dolor", "Indicación", PrioridadPrueba.URGENTE);
        assertThrows(
                PrescripcionNoPermitidaException.class,
                () -> servicio.prescribir(
                        "medico-1", "sin-permiso", "TAC", "Dolor", "Indicación", PrioridadPrueba.URGENTE));
        assertEquals(1, servicio.consultarSolicitudes("medico-1").size());
        assertTrue(servicio.consultarSolicitudes("medico-2").isEmpty());
        assertThrows(NoSuchElementException.class, () -> servicio.consultarDetalle("medico-2", "solicitud-1"));
        assertThrows(NoSuchElementException.class, () -> servicio.consultarDetalle("medico-1", "inexistente"));
    }

    @Test
    void listaDeFormaDeterministaYValidaIdentificadores() {
        SolicitudPruebaDiagnostica primera =
                servicio.prescribir("medico-1", "p-1", "TAC", "A", "B", PrioridadPrueba.ORDINARIA);
        SolicitudPruebaDiagnostica segunda =
                servicio.prescribir("medico-1", "p-2", "RM", "C", "D", PrioridadPrueba.PREFERENTE);

        assertEquals(List.of(primera, segunda), servicio.consultarSolicitudes("medico-1"));
        assertThrows(IllegalArgumentException.class, () -> servicio.consultarSolicitudes(" "));
        assertThrows(IllegalArgumentException.class, () -> servicio.consultarDetalle(null, "id"));
    }
}
