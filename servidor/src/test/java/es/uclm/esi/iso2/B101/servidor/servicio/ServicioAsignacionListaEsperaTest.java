package es.uclm.esi.iso2.B101.servidor.servicio;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.EntradaListaEspera;
import es.uclm.esi.iso2.B101.servidor.dominio.EstadoEntradaListaEspera;
import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadPrueba;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioListaEspera;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioListaEsperaMemoria;

class ServicioAsignacionListaEsperaTest {
    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);

    private static final LocalDateTime FECHA =
            LocalDateTime.of(2026, 12, 3, 10, 0);

    private RepositorioListaEsperaMemoria lista;
    private RepositorioCitasMemoria citas;
    private ServicioListaEspera gestion;
    private ServicioAsignacionListaEspera asignacion;

    @BeforeEach
    void preparar() {
        lista = new RepositorioListaEsperaMemoria();
        citas = new RepositorioCitasMemoria();
        gestion = new ServicioListaEspera(lista, RELOJ);
        asignacion = new ServicioAsignacionListaEspera(
                lista, citas, RELOJ);
    }

    @Test
    void asignaPrimeroLaEntradaDeMayorPrioridad() {
        EntradaListaEspera ordinaria = inscribir(
                "paciente-1", PrioridadPrueba.ORDINARIA);
        EntradaListaEspera urgente = inscribir(
                "paciente-2", PrioridadPrueba.URGENTE);

        var resultado = asignar();

        assertEquals(urgente.getId(), resultado.entrada().getId());
        assertEquals(
                EstadoEntradaListaEspera.CITADA,
                resultado.entrada().getEstado());
        assertEquals(
                resultado.cita().getId(), resultado.entrada().getCitaId());
        assertEquals("paciente-2", resultado.cita().getPacienteId());
        assertEquals("medico-1", resultado.cita().getMedicoId());
        assertEquals("TAC", resultado.cita().getTipoPrueba());
        assertEquals(FECHA, resultado.cita().getFechaHora());
        assertEquals(
                30, resultado.cita().getDuracionMinutos().orElseThrow());
        assertSame(
                resultado.cita(),
                citas.buscarPorId(resultado.cita().getId()).orElseThrow());
        assertEquals(List.of(ordinaria), gestion.consultarPendientes());
    }

    @Test
    void respetaLaAntiguedadCuandoLaPrioridadEsIgual() {
        EntradaListaEspera antigua = inscribir(
                "paciente-1", PrioridadPrueba.URGENTE);

        Clock posterior = Clock.offset(
                RELOJ, java.time.Duration.ofHours(1));

        new ServicioListaEspera(lista, posterior).inscribir(
                "paciente-2", "medico-1", "TAC", PrioridadPrueba.URGENTE);

        assertEquals(antigua.getId(), asignar().entrada().getId());
    }

    @Test
    void rechazaSolapamientoDelMedicoSinCambiarLaEntrada() {
        EntradaListaEspera entrada = inscribir(
                "paciente-1", PrioridadPrueba.URGENTE);

        citas.guardar(new Cita(
                "previa", "otro-paciente", "medico-1", "RM",
                FECHA.minusMinutes(15), 60));

        assertThrows(IllegalStateException.class, this::asignar);

        assertSame(entrada, gestion.consultarEntrada(entrada.getId()));
        assertEquals(1, citas.buscarPorMedicoId("medico-1").size());
        assertTrue(citas.buscarPorPacienteId("paciente-1").isEmpty());
    }

    @Test
    void rechazaSolapamientoDelPacienteConOtroMedico() {
        EntradaListaEspera entrada = inscribir(
                "paciente-1", PrioridadPrueba.URGENTE);

        citas.guardar(new Cita(
                "previa", "paciente-1", "medico-2", "RM",
                FECHA.minusMinutes(15), 60));

        assertThrows(IllegalStateException.class, this::asignar);

        assertSame(entrada, gestion.consultarEntrada(entrada.getId()));
        assertTrue(citas.buscarPorMedicoId("medico-1").isEmpty());
    }

    @Test
    void permiteCitasContiguasEIgnoraLasCanceladas() {
        inscribir("paciente-1", PrioridadPrueba.ORDINARIA);

        citas.guardar(new Cita(
                "anterior", "otro-paciente", "medico-1", "RM",
                FECHA.minusMinutes(60), 60));

        citas.guardar(new Cita(
                "posterior", "otro-paciente", "medico-1", "RM",
                FECHA.plusMinutes(30), 60));

        Cita cancelada = new Cita(
                "cancelada", "otro-paciente", "medico-1",
                "TAC", FECHA, 30);
        cancelada.cancelar();
        citas.guardar(cancelada);

        var resultado = asignar();

        assertEquals(
                EstadoEntradaListaEspera.CITADA,
                resultado.entrada().getEstado());
        assertEquals(4, citas.buscarPorMedicoId("medico-1").size());
    }

    @Test
    void rechazaReservaSiUnaCitaAnteriorTieneDuracionDesconocida() {
        EntradaListaEspera entrada = inscribir(
                "paciente-1", PrioridadPrueba.URGENTE);

        citas.guardar(new Cita(
                "sin-duracion", "otro-paciente", "medico-1",
                "TAC", FECHA.minusMinutes(15)));

        assertThrows(IllegalStateException.class, this::asignar);

        assertSame(entrada, gestion.consultarEntrada(entrada.getId()));
        assertTrue(citas.buscarPorPacienteId("paciente-1").isEmpty());
    }

    @Test
    void deshaceLaReservaSiFallaElGuardadoDeLaEntrada() {
        EntradaListaEspera entrada = inscribir(
                "paciente-1", PrioridadPrueba.URGENTE);

        RepositorioListaEspera repositorioConFallo =
                new RepositorioListaEspera() {
                    @Override
                    public void guardar(EntradaListaEspera valor) {
                        if (valor.getEstado()
                                == EstadoEntradaListaEspera.CITADA) {
                            throw new IllegalStateException(
                                    "Fallo de almacenamiento simulado");
                        }
                        lista.guardar(valor);
                    }

                    @Override
                    public Optional<EntradaListaEspera> buscarPorId(String id) {
                        return lista.buscarPorId(id);
                    }

                    @Override
                    public List<EntradaListaEspera> buscarPendientes() {
                        return lista.buscarPendientes();
                    }

                    @Override
                    public List<EntradaListaEspera> buscarPorPacienteId(
                            String pacienteId) {
                        return lista.buscarPorPacienteId(pacienteId);
                    }
                };

        var servicioConFallo = new ServicioAsignacionListaEspera(
                repositorioConFallo, citas, RELOJ);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> servicioConFallo.asignarSiguiente(
                        "medico-1", "TAC", FECHA, 30));

        assertEquals("Fallo de almacenamiento simulado", error.getMessage());
        assertTrue(citas.buscarPorMedicoId("medico-1").isEmpty());
        assertSame(entrada, gestion.consultarEntrada(entrada.getId()));
    }

    @Test
    void dosPeticionesSimultaneasSoloReservanUnaVez() throws Exception {
        inscribir("paciente-1", PrioridadPrueba.URGENTE);
        inscribir("paciente-2", PrioridadPrueba.ORDINARIA);

        var segundaInstancia = new ServicioAsignacionListaEspera(
                lista, citas, RELOJ);

        CountDownLatch inicio = new CountDownLatch(1);

        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primera = ejecutor.submit(() -> {
                inicio.await();
                return intentarAsignar(asignacion);
            });

            var segunda = ejecutor.submit(() -> {
                inicio.await();
                return intentarAsignar(segundaInstancia);
            });

            inicio.countDown();

            boolean resultado1 = primera.get(5, TimeUnit.SECONDS);
            boolean resultado2 = segunda.get(5, TimeUnit.SECONDS);

            assertNotEquals(resultado1, resultado2);
        }

        assertEquals(1, citas.buscarPorMedicoId("medico-1").size());
        assertEquals(1, gestion.consultarPendientes().size());

        long citadas = java.util.stream.Stream.concat(
                gestion.consultarPorPaciente("paciente-1").stream(),
                gestion.consultarPorPaciente("paciente-2").stream())
                .filter(entrada ->
                        entrada.getEstado() == EstadoEntradaListaEspera.CITADA)
                .count();

        assertEquals(1L, citadas);
    }

    @Test
    void rechazaListaVaciaYDatosInvalidosSinCrearCitas() {
        assertThrows(NoSuchElementException.class, this::asignar);

        assertThrows(IllegalArgumentException.class,
                () -> asignacion.asignarSiguiente(
                        " ", "TAC", FECHA, 30));
        assertThrows(IllegalArgumentException.class,
                () -> asignacion.asignarSiguiente(
                        "medico-1", " ", FECHA, 30));
        assertThrows(IllegalArgumentException.class,
                () -> asignacion.asignarSiguiente(
                        "medico-1", "TAC", null, 30));
        assertThrows(IllegalArgumentException.class,
                () -> asignacion.asignarSiguiente(
                        "medico-1", "TAC",
                        LocalDateTime.now(RELOJ).minusMinutes(1), 30));

        for (int duracion : new int[] {0, -1, 1441}) {
            assertThrows(IllegalArgumentException.class,
                    () -> asignacion.asignarSiguiente(
                            "medico-1", "TAC", FECHA, duracion));
        }

        assertTrue(citas.buscarPorMedicoId("medico-1").isEmpty());
    }

    private EntradaListaEspera inscribir(
            String pacienteId, PrioridadPrueba prioridad) {
        return gestion.inscribir(
                pacienteId, "medico-1", "TAC", prioridad);
    }

    private ServicioAsignacionListaEspera.ResultadoAsignacion asignar() {
        return asignacion.asignarSiguiente(
                "medico-1", "TAC", FECHA, 30);
    }

    private boolean intentarAsignar(
            ServicioAsignacionListaEspera servicio) {
        try {
            servicio.asignarSiguiente("medico-1", "TAC", FECHA, 30);
            return true;
        } catch (IllegalStateException conflicto) {
            return false;
        }
    }
}