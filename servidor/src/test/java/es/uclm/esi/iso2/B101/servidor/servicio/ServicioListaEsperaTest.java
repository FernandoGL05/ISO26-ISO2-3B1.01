package es.uclm.esi.iso2.B101.servidor.servicio;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import es.uclm.esi.iso2.B101.servidor.dominio.EntradaListaEspera;
import es.uclm.esi.iso2.B101.servidor.dominio.EstadoEntradaListaEspera;
import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadPrueba;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioListaEsperaMemoria;

class ServicioListaEsperaTest {
    private RepositorioListaEsperaMemoria repositorio;
    private ServicioListaEspera servicio;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioListaEsperaMemoria();
        servicio = servicioEn("2026-11-25T10:00:00Z");
    }

    @Test
    void inscribeUnaEntradaPendienteConFechaDelServidor() {
        EntradaListaEspera entrada = inscribir(
                "paciente-1", PrioridadPrueba.ORDINARIA);

        assertFalse(entrada.getId().isBlank());
        assertEquals("paciente-1", entrada.getPacienteId());
        assertEquals("medico-1", entrada.getMedicoId());
        assertEquals("TAC", entrada.getTipoPrueba());
        assertEquals(PrioridadPrueba.ORDINARIA, entrada.getPrioridad());
        assertEquals(
                LocalDateTime.of(2026, 11, 25, 10, 0),
                entrada.getFechaEntrada());
        assertEquals(
                EstadoEntradaListaEspera.PENDIENTE, entrada.getEstado());
        assertNull(entrada.getCitaId());
        assertSame(entrada, servicio.consultarEntrada(entrada.getId()));
    }

    @Test
    void rechazaDuplicadosPendientesSinCambiarLaLista() {
        EntradaListaEspera original = inscribir(
                "paciente-1", PrioridadPrueba.ORDINARIA);

        assertThrows(IllegalStateException.class,
                () -> inscribir("paciente-1", PrioridadPrueba.URGENTE));

        assertEquals(List.of(original), servicio.consultarPendientes());
    }

    @Test
    void ordenaPorPrioridadYDespuesPorAntiguedad() {
        EntradaListaEspera ordinaria = inscribir(
                "paciente-1", PrioridadPrueba.ORDINARIA);

        EntradaListaEspera urgenteAntigua = inscribir(
                "paciente-2", PrioridadPrueba.URGENTE);

        EntradaListaEspera urgenteNueva =
                servicioEn("2026-11-25T11:00:00Z").inscribir(
                        "paciente-3", "medico-1", "TAC",
                        PrioridadPrueba.URGENTE);

        EntradaListaEspera preferente = inscribir(
                "paciente-4", PrioridadPrueba.PREFERENTE);

        assertEquals(
                List.of(urgenteAntigua, urgenteNueva, preferente, ordinaria),
                servicio.consultarPendientes());
    }

    @Test
    void filtraPorMedicoYTipoDePrueba() {
        EntradaListaEspera esperada = inscribir(
                "paciente-1", PrioridadPrueba.ORDINARIA);

        servicio.inscribir(
                "paciente-2", "medico-2", "TAC",
                PrioridadPrueba.URGENTE);
        servicio.inscribir(
                "paciente-3", "medico-1", "RM",
                PrioridadPrueba.URGENTE);

        assertEquals(
                List.of(esperada),
                servicio.consultarPendientes("medico-1", "TAC"));
    }

    @Test
    void cambiarPrioridadConservaLaAntiguedadYReordena() {
        EntradaListaEspera primera = inscribir(
                "paciente-1", PrioridadPrueba.ORDINARIA);
        EntradaListaEspera segunda = inscribir(
                "paciente-2", PrioridadPrueba.PREFERENTE);

        EntradaListaEspera actualizada = servicio.cambiarPrioridad(
                primera.getId(), PrioridadPrueba.URGENTE);

        assertEquals(primera.getFechaEntrada(), actualizada.getFechaEntrada());
        assertEquals(primera.getId(), actualizada.getId());
        assertEquals(PrioridadPrueba.ORDINARIA, primera.getPrioridad());
        assertEquals(
                List.of(actualizada, segunda),
                servicio.consultarPendientes());
    }

    @Test
    void cancelarRetiraDePendientesPeroConservaElHistorial() {
        EntradaListaEspera entrada = inscribir(
                "paciente-1", PrioridadPrueba.ORDINARIA);

        EntradaListaEspera cancelada = servicio.cancelar(entrada.getId());

        assertEquals(
                EstadoEntradaListaEspera.CANCELADA, cancelada.getEstado());
        assertTrue(servicio.consultarPendientes().isEmpty());
        assertEquals(
                List.of(cancelada),
                servicio.consultarPorPaciente("paciente-1"));

        assertThrows(IllegalStateException.class,
                () -> servicio.cancelar(entrada.getId()));
        assertThrows(IllegalStateException.class,
                () -> servicio.cambiarPrioridad(
                        entrada.getId(), PrioridadPrueba.URGENTE));

        assertSame(cancelada, servicio.consultarEntrada(entrada.getId()));
    }

    @Test
    void permiteNuevaEntradaTrasCancelarLaAnterior() {
        EntradaListaEspera anterior = inscribir(
                "paciente-1", PrioridadPrueba.ORDINARIA);
        servicio.cancelar(anterior.getId());

        EntradaListaEspera nueva = inscribir(
                "paciente-1", PrioridadPrueba.PREFERENTE);

        assertNotEquals(anterior.getId(), nueva.getId());
        assertEquals(List.of(nueva), servicio.consultarPendientes());
        assertEquals(2, servicio.consultarPorPaciente("paciente-1").size());
    }

    @Test
    void rechazaDatosInvalidosSinGuardarEntradas() {
        assertThrows(IllegalArgumentException.class,
                () -> inscribir(" ", PrioridadPrueba.ORDINARIA));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.inscribir(
                        "paciente-1", "", "TAC", PrioridadPrueba.URGENTE));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.inscribir(
                        "paciente-1", "medico-1", "", PrioridadPrueba.URGENTE));
        assertThrows(IllegalArgumentException.class,
                () -> inscribir("paciente-1", null));

        assertTrue(servicio.consultarPendientes().isEmpty());
    }

    @Test
    void rechazaOperacionesSobreEntradasInexistentes() {
        assertThrows(NoSuchElementException.class,
                () -> servicio.consultarEntrada("inexistente"));
        assertThrows(NoSuchElementException.class,
                () -> servicio.cancelar("inexistente"));
        assertThrows(NoSuchElementException.class,
                () -> servicio.cambiarPrioridad(
                        "inexistente", PrioridadPrueba.URGENTE));
    }

    private EntradaListaEspera inscribir(
            String pacienteId, PrioridadPrueba prioridad) {
        return servicio.inscribir(
                pacienteId, "medico-1", "TAC", prioridad);
    }

    private ServicioListaEspera servicioEn(String instante) {
        return new ServicioListaEspera(
                repositorio,
                Clock.fixed(Instant.parse(instante), ZoneOffset.UTC));
    }
}