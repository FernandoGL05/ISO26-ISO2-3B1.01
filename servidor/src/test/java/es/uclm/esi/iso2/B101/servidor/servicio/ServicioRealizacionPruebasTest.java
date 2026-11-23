package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.EstadoCita;
import es.uclm.esi.iso2.B101.servidor.dominio.Incidencia;
import es.uclm.esi.iso2.B101.servidor.dominio.PruebaRealizada;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHistorialPruebasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioIncidenciasMemoria;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioRealizacionPruebasTest {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 22, 10, 0);

    private RepositorioCitasMemoria citas;
    private RepositorioHistorialPruebasMemoria historial;
    private RepositorioIncidenciasMemoria incidencias;
    private ServicioRealizacionPruebas servicio;

    @BeforeEach
    void prepararServicio() {
        citas = new RepositorioCitasMemoria();
        historial = new RepositorioHistorialPruebasMemoria();
        incidencias = new RepositorioIncidenciasMemoria();
        citas.guardar(new Cita(
                "cita-1", "paciente-1", "medico-1", "RM", LocalDateTime.of(2026, 11, 22, 9, 0)));
        servicio = new ServicioRealizacionPruebas(
                citas,
                historial,
                incidencias,
                Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void registraRealizacionActualizaLaCitaYElHistorial() {
        PruebaRealizada prueba = servicio.registrarRealizacion("medico-1", "cita-1");

        assertEquals("cita-1", prueba.getCitaId());
        assertEquals("paciente-1", prueba.getPacienteId());
        assertEquals("RM", prueba.getTipoPrueba());
        assertEquals(AHORA, prueba.getFechaHoraRealizacion());
        assertEquals(EstadoCita.REALIZADA, citas.buscarPorId("cita-1").orElseThrow().getEstado());
        assertEquals(prueba, historial.buscarPorCitaId("cita-1").orElseThrow());
        assertTrue(incidencias.buscarPorPacienteId("paciente-1").isEmpty());
    }

    @Test
    void registraIncidenciaAsociadaALaCitaSinDeclararlaRealizada() {
        Incidencia incidencia = servicio.registrarIncidencia(
                "medico-1", "cita-1", "El equipo falló durante la adquisición");

        assertEquals("cita-1", incidencia.getCitaId());
        assertEquals("paciente-1", incidencia.getPacienteId());
        assertEquals("Incidencia durante realización de prueba", incidencia.getAsunto());
        assertEquals(incidencia, incidencias.buscarPorId(incidencia.getId()).orElseThrow());
        assertEquals(EstadoCita.PROGRAMADA, citas.buscarPorId("cita-1").orElseThrow().getEstado());
        assertTrue(historial.buscarPorCitaId("cita-1").isEmpty());
    }

    @Test
    void noPermiteRegistrarResultadosDeCitasAjenasInexistentesOCanceladas() {
        assertThrows(NoSuchElementException.class,
                () -> servicio.registrarRealizacion("medico-2", "cita-1"));
        assertThrows(NoSuchElementException.class,
                () -> servicio.registrarRealizacion("medico-1", "desconocida"));
        Cita cancelada = new Cita(
                "cita-cancelada", "paciente-1", "medico-1", "TAC", AHORA);
        cancelada.cancelar();
        citas.guardar(cancelada);
        assertThrows(IllegalStateException.class,
                () -> servicio.registrarRealizacion("medico-1", "cita-cancelada"));
        assertThrows(IllegalStateException.class,
                () -> servicio.registrarIncidencia("medico-1", "cita-cancelada", "No se pudo completar"));
    }

    @Test
    void evitaRegistrarDosVecesLaRealizacionDeLaMismaCita() {
        servicio.registrarRealizacion("medico-1", "cita-1");

        assertThrows(IllegalStateException.class, () -> servicio.registrarRealizacion("medico-1", "cita-1"));
        assertEquals(1, historial.buscarPorPacienteId("paciente-1").size());
    }
}
