package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.NecesidadCitaSeguimiento;
import es.uclm.esi.iso2.B101.servidor.dominio.PrescripcionSeguimiento;
import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadSeguimiento;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioNecesidadesCitaSeguimientoMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioPrescripcionesSeguimientoMemoria;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioPrescripcionesSeguimientoTest {
    private RepositorioPrescripcionesSeguimientoMemoria repositorioPrescripciones;
    private RepositorioNecesidadesCitaSeguimientoMemoria repositorioNecesidades;
    private ServicioPrescripcionesSeguimiento servicio;

    @BeforeEach
    void prepararServicio() {
        repositorioPrescripciones = new RepositorioPrescripcionesSeguimientoMemoria();
        repositorioNecesidades = new RepositorioNecesidadesCitaSeguimientoMemoria();
        AtomicInteger siguienteId = new AtomicInteger();
        servicio = new ServicioPrescripcionesSeguimiento(
                repositorioPrescripciones,
                repositorioNecesidades,
                Clock.fixed(Instant.parse("2024-01-01T10:00:00Z"), ZoneOffset.UTC),
                (medico, paciente) -> !"sin-permiso".equals(paciente),
                12,
                24,
                () -> "id-" + siguienteId.incrementAndGet());
    }

    @Test
    void registraPrescripcionYGeneraAutomaticamenteNecesidadesParaDoceMeses() {
        PrescripcionSeguimiento prescripcion = crear("medico-1", "paciente-1", LocalDate.of(2024, 1, 31), 1);
        List<NecesidadCitaSeguimiento> necesidades =
                servicio.consultarNecesidadesCita("medico-1", prescripcion.getId());

        assertEquals(LocalDate.of(2024, 1, 1), prescripcion.getFechaCreacion());
        assertEquals(12, servicio.getHorizonteInicialMeses());
        assertEquals(12, necesidades.size());
        assertEquals(LocalDate.of(2024, 1, 31), necesidades.get(0).getFechaPrevista());
        assertEquals(LocalDate.of(2024, 2, 29), necesidades.get(1).getFechaPrevista());
        assertEquals(LocalDate.of(2024, 3, 31), necesidades.get(2).getFechaPrevista());
        assertEquals(LocalDate.of(2024, 12, 31), necesidades.get(11).getFechaPrevista());
        assertTrue(necesidades.stream().allMatch(n -> n.getCitaId() == null));
    }

    @Test
    void calculaDesdeLaFechaOriginalYRespetaFinDeMesYAnoNoBisiesto() {
        repositorioPrescripciones = new RepositorioPrescripcionesSeguimientoMemoria();
        repositorioNecesidades = new RepositorioNecesidadesCitaSeguimientoMemoria();
        AtomicInteger siguienteId = new AtomicInteger();
        servicio = new ServicioPrescripcionesSeguimiento(
                repositorioPrescripciones,
                repositorioNecesidades,
                Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneOffset.UTC),
                (medico, paciente) -> true,
                2,
                12,
                () -> "fecha-" + siguienteId.incrementAndGet());
        PrescripcionSeguimiento prescripcion = crear(
                "medico-1", "paciente-1", LocalDate.of(2025, 1, 31), 1);
        List<NecesidadCitaSeguimiento> automaticas =
                servicio.consultarNecesidadesCita("medico-1", prescripcion.getId());
        int nuevas = servicio.generarNecesidadesHasta(
                "medico-1", prescripcion.getId(), LocalDate.of(2025, 5, 1));
        List<NecesidadCitaSeguimiento> ampliadas =
                servicio.consultarNecesidadesCita("medico-1", prescripcion.getId());

        assertEquals(2, nuevas);
        assertEquals(LocalDate.of(2025, 2, 28), automaticas.get(1).getFechaPrevista());
        assertEquals(LocalDate.of(2025, 3, 31), ampliadas.get(2).getFechaPrevista());
        assertEquals(LocalDate.of(2025, 4, 30), ampliadas.get(3).getFechaPrevista());
        assertEquals(4, ampliadas.size());
    }

    @Test
    void repetirGeneracionEsIdempotenteYElPlanificadorPuedeAmpliarSinPaciente() {
        PrescripcionSeguimiento prescripcion = crear(
                "medico-1", "paciente-1", LocalDate.of(2024, 2, 29), 3);
        LocalDate limite = LocalDate.of(2025, 1, 1);
        int existentes = repositorioNecesidades.buscarPorPrescripcionId(prescripcion.getId()).size();
        assertEquals(
                List.of(
                        LocalDate.of(2024, 2, 29),
                        LocalDate.of(2024, 5, 29),
                        LocalDate.of(2024, 8, 29),
                        LocalDate.of(2024, 11, 29)),
                servicio.consultarNecesidadesCita("medico-1", prescripcion.getId()).stream()
                        .map(NecesidadCitaSeguimiento::getFechaPrevista)
                        .toList());

        assertEquals(0, servicio.generarNecesidadesHasta(limite));
        assertEquals(0, servicio.generarNecesidadesHasta(limite));
        assertEquals(existentes, repositorioNecesidades.buscarPorPrescripcionId(prescripcion.getId()).size());
        assertEquals(1, servicio.generarNecesidadesHasta(LocalDate.of(2025, 3, 1)));
        assertEquals(0, servicio.generarNecesidadesHasta(LocalDate.of(2025, 3, 1)));
    }

    @Test
    void evitaAccesoEntreMedicosYCompruebaLaPoliticaAntesDeGuardar() {
        PrescripcionSeguimiento propia = crear(
                "medico-1", "paciente-1", LocalDate.of(2024, 2, 1), 6);
        assertTrue(servicio.consultarPrescripciones("medico-2").isEmpty());
        assertThrows(
                java.util.NoSuchElementException.class,
                () -> servicio.consultarDetalle("medico-2", propia.getId()));
        assertThrows(
                java.util.NoSuchElementException.class,
                () -> servicio.consultarNecesidadesCita("medico-2", propia.getId()));
        assertThrows(
                PrescripcionSeguimientoNoPermitidaException.class,
                () -> crear("medico-1", "sin-permiso", LocalDate.of(2024, 2, 1), 3));
        assertEquals(1, servicio.consultarPrescripciones("medico-1").size());
    }

    @Test
    void validaPeriodicidadFechasHorizonteYDatosObligatorios() {
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.registrarPrescripcion(
                        "medico-1", "p-1", "TAC", "Control", 0,
                        LocalDate.of(2024, 2, 1), PrioridadSeguimiento.URGENTE));
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.registrarPrescripcion(
                        "medico-1", "p-1", "TAC", "Control", 1,
                        LocalDate.of(2023, 12, 31), PrioridadSeguimiento.URGENTE));
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.registrarPrescripcion(
                        "medico-1", "p-1", "TAC", "Control", 1,
                        null, PrioridadSeguimiento.URGENTE));
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.generarNecesidadesHasta(LocalDate.of(2035, 1, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServicioPrescripcionesSeguimiento(
                        repositorioPrescripciones,
                        repositorioNecesidades,
                        Clock.systemUTC(),
                        (m, p) -> true,
                        12,
                        ServicioPrescripcionesSeguimiento.HORIZONTE_MAXIMO_MESES + 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.registrarPrescripcion(
                        " ", "p-1", "TAC", "Control", 1,
                        LocalDate.of(2024, 2, 1), PrioridadSeguimiento.URGENTE));
    }

    @Test
    void aplicaUnLimiteConfigurableAlNumeroDeNecesidadesGeneradas() {
        RepositorioPrescripcionesSeguimientoMemoria prescripciones =
                new RepositorioPrescripcionesSeguimientoMemoria();
        RepositorioNecesidadesCitaSeguimientoMemoria necesidades =
                new RepositorioNecesidadesCitaSeguimientoMemoria();
        ServicioPrescripcionesSeguimiento acotado = new ServicioPrescripcionesSeguimiento(
                prescripciones,
                necesidades,
                Clock.fixed(Instant.parse("2024-01-01T10:00:00Z"), ZoneOffset.UTC),
                (medico, paciente) -> true,
                12,
                ServicioPrescripcionesSeguimiento.HORIZONTE_MAXIMO_MESES,
                () -> java.util.UUID.randomUUID().toString());
        PrescripcionSeguimiento prescripcion = acotado.registrarPrescripcion(
                "m", "p", "TAC", "Control", 1, LocalDate.of(2024, 1, 1), PrioridadSeguimiento.ORDINARIA);

        assertEquals(108, acotado.generarNecesidadesHasta(LocalDate.of(2034, 1, 1)));
        assertEquals(121, acotado.consultarNecesidadesCita("m", prescripcion.getId()).size());
        assertThrows(
                IllegalArgumentException.class,
                () -> acotado.generarNecesidadesHasta(LocalDate.of(2034, 1, 2)));
    }

    private PrescripcionSeguimiento crear(
            String medicoId, String pacienteId, LocalDate primeraRevision, int periodicidad) {
        return servicio.registrarPrescripcion(
                medicoId,
                pacienteId,
                "Resonancia",
                "Seguimiento de lesión",
                periodicidad,
                primeraRevision,
                PrioridadSeguimiento.PREFERENTE);
    }
}
