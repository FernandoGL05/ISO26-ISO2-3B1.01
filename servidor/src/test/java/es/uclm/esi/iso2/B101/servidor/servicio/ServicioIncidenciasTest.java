package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.EstadoIncidencia;
import es.uclm.esi.iso2.B101.servidor.dominio.Incidencia;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioIncidenciasMemoria;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioIncidenciasTest {
    private static final Instant INSTANTE_FIJO = Instant.parse("2026-11-21T11:00:00Z");

    private RepositorioIncidenciasMemoria repositorio;
    private ServicioIncidencias servicio;

    @BeforeEach
    void prepararServicio() {
        repositorio = new RepositorioIncidenciasMemoria();
        servicio = new ServicioIncidencias(repositorio, Clock.fixed(INSTANTE_FIJO, ZoneOffset.UTC));
    }

    @Test
    void comunicaIncidenciaConIdentificadorFechaYEstadoInicial() {
        Incidencia incidencia =
                servicio.comunicarIncidencia("paciente-1", "Problema con mi cita", "No veo confirmación.", null);

        assertFalse(incidencia.getId().isBlank());
        assertEquals(LocalDateTime.of(2026, 11, 21, 11, 0), incidencia.getFechaHoraCreacion());
        assertEquals(EstadoIncidencia.RECIBIDA, incidencia.getEstado());
        assertEquals(incidencia, repositorio.buscarPorId(incidencia.getId()).orElseThrow());
    }

    @Test
    void generaIdentificadoresDistintosParaIncidenciasDiferentes() {
        Incidencia primera =
                servicio.comunicarIncidencia("paciente-1", "Asunto uno", "Descripción uno", null);
        Incidencia segunda =
                servicio.comunicarIncidencia("paciente-1", "Asunto dos", "Descripción dos", null);

        assertNotEquals(primera.getId(), segunda.getId());
    }

    @Test
    void consultaLaListaPropiaYElDetallePropio() {
        Incidencia propia =
                servicio.comunicarIncidencia("paciente-1", "Asunto", "Descripción", "cita-1");
        servicio.comunicarIncidencia("paciente-2", "Ajena", "No debe aparecer.", null);

        assertEquals(List.of(propia), servicio.consultarIncidencias("paciente-1"));
        assertEquals(propia, servicio.consultarDetalle("paciente-1", propia.getId()));
        assertTrue(servicio.consultarIncidencias("paciente-sin-incidencias").isEmpty());
    }

    @Test
    void impideConsultarElDetalleDeUnaIncidenciaAjenaOInexistente() {
        Incidencia ajena =
                servicio.comunicarIncidencia("paciente-2", "Ajena", "Descripción", null);

        assertThrows(
                NoSuchElementException.class,
                () -> servicio.consultarDetalle("paciente-1", ajena.getId()));
        assertThrows(
                NoSuchElementException.class,
                () -> servicio.consultarDetalle("paciente-1", "inexistente"));
    }

    @Test
    void repositorioRechazaIdentificadoresDuplicadosSinSobrescribir() {
        Incidencia original = new Incidencia(
                "id-duplicado",
                "paciente-1",
                "Original",
                "Descripción original",
                null,
                LocalDateTime.of(2026, 11, 21, 11, 0));
        Incidencia duplicada = new Incidencia(
                "id-duplicado",
                "paciente-1",
                "Duplicada",
                "Descripción distinta",
                null,
                LocalDateTime.of(2026, 11, 21, 11, 1));
        repositorio.guardar(original);

        assertThrows(IllegalStateException.class, () -> repositorio.guardar(duplicada));
        assertEquals(original, repositorio.buscarPorId("id-duplicado").orElseThrow());
    }
}
