package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.InformePrueba;
import es.uclm.esi.iso2.B101.servidor.dominio.PruebaRealizada;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHistorialPruebasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioInformesPruebaMemoria;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioInformesPruebaTest {
    private ServicioInformesPrueba servicio;
    private RepositorioCitasMemoria repositorioCitas;
    private RepositorioHistorialPruebasMemoria repositorioPruebas;
    private RepositorioInformesPruebaMemoria repositorioInformes;

    @BeforeEach
    void prepararServicio() {
        repositorioCitas = new RepositorioCitasMemoria();
        Cita cita = new Cita(
                "cita-1",
                "paciente-1",
                "medico-1",
                "RM",
                LocalDateTime.of(2026, 11, 20, 9, 0));
        cita.marcarRealizada();
        repositorioCitas.guardar(cita);
        repositorioPruebas = new RepositorioHistorialPruebasMemoria();
        repositorioPruebas.registrar(new PruebaRealizada(
                "prueba-1", "paciente-1", "RM", LocalDateTime.of(2026, 11, 20, 10, 0), null, "cita-1"));
        repositorioInformes = new RepositorioInformesPruebaMemoria();
        servicio = new ServicioInformesPrueba(
                repositorioCitas,
                repositorioPruebas,
                repositorioInformes,
                Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void creaInformeVinculadoALaPruebaYLoExponeAlPacienteYMedicoAutor() {
        InformePrueba informe = servicio.crear("medico-1", "prueba-1", "Hallazgos de prueba", "Sin alteraciones");

        assertEquals("paciente-1", informe.getPacienteId());
        assertEquals("medico-1", informe.getMedicoId());
        assertEquals(LocalDateTime.of(2026, 11, 22, 10, 0), informe.getFechaCreacion());
        assertEquals(informe, servicio.consultar(informe.getId(), "paciente-1", null));
        assertEquals(informe, servicio.consultar(informe.getId(), null, "medico-1"));
        assertEquals(1, servicio.consultarPorPaciente("paciente-1").size());
        assertEquals(1, servicio.consultarPorMedico("medico-1").size());
    }

    @Test
    void ocultaInformeDePacientesYMedicosNoRelacionados() {
        InformePrueba informe = servicio.crear("medico-1", "prueba-1", "Hallazgos", "Conclusión");

        assertThrows(NoSuchElementException.class,
                () -> servicio.consultar(informe.getId(), "paciente-2", null));
        assertThrows(NoSuchElementException.class,
                () -> servicio.consultar(informe.getId(), null, "medico-2"));
    }

    @Test
    void validaPruebaYCamposObligatoriosAlCrear() {
        assertThrows(IllegalArgumentException.class, () -> servicio.crear("medico-1", " ", "Hallazgos", "Conclusión"));
        assertThrows(NoSuchElementException.class,
                () -> servicio.crear("medico-1", "inexistente", "Hallazgos", "Conclusión"));
        assertTrue(servicio.consultarPorPaciente("paciente-1").isEmpty());
    }

    @Test
    void rechazaAlMedicoNoAsignadoSinGuardarInforme() {
        assertThrows(InformePruebaNoPermitidoException.class,
                () -> servicio.crear("medico-2", "prueba-1", "Hallazgos", "Conclusión"));

        assertTrue(servicio.consultarPorPaciente("paciente-1").isEmpty());
        assertTrue(servicio.consultarPorMedico("medico-2").isEmpty());
    }

    @Test
    void rechazaPruebaSinCitaVinculadaSinGuardarInforme() {
        repositorioPruebas.registrar(new PruebaRealizada(
                "prueba-sin-cita",
                "paciente-1",
                "RM",
                LocalDateTime.of(2026, 11, 20, 11, 0),
                null));

        assertThrows(InformePruebaNoPermitidoException.class,
                () -> servicio.crear("medico-1", "prueba-sin-cita", "Hallazgos", "Conclusión"));

        assertTrue(servicio.consultarPorPaciente("paciente-1").isEmpty());
    }

    @Test
    void rechazaCitaNoRealizadaOIncoherenteSinGuardarInforme() {
        repositorioCitas.guardar(new Cita(
                "cita-pendiente",
                "paciente-1",
                "medico-1",
                "RM",
                LocalDateTime.of(2026, 11, 21, 9, 0)));
        Cita citaPacienteUno = new Cita(
                "cita-paciente-incoherente",
                "paciente-1",
                "medico-1",
                "RM",
                LocalDateTime.of(2026, 11, 20, 11, 0));
        citaPacienteUno.marcarRealizada();
        repositorioCitas.guardar(citaPacienteUno);
        repositorioPruebas.registrar(new PruebaRealizada(
                "prueba-pendiente",
                "paciente-1",
                "RM",
                LocalDateTime.of(2026, 11, 21, 10, 0),
                null,
                "cita-pendiente"));
        repositorioPruebas.registrar(new PruebaRealizada(
                "prueba-paciente-incoherente",
                "paciente-2",
                "RM",
                LocalDateTime.of(2026, 11, 20, 12, 0),
                null,
                "cita-paciente-incoherente"));

        assertThrows(InformePruebaNoPermitidoException.class,
                () -> servicio.crear("medico-1", "prueba-pendiente", "Hallazgos", "Conclusión"));
        assertThrows(InformePruebaNoPermitidoException.class,
                () -> servicio.crear("medico-1", "prueba-paciente-incoherente", "Hallazgos", "Conclusión"));

        assertTrue(servicio.consultarPorPaciente("paciente-1").isEmpty());
        assertTrue(servicio.consultarPorPaciente("paciente-2").isEmpty());
    }

    @Test
    void adjuntaUnaImagenPngYDevuelveCopiasDefensivas() {
        InformePrueba informe = servicio.crear("medico-1", "prueba-1", "Hallazgos", "Conclusión");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
        String base64 = Base64.getEncoder().encodeToString(png);

        servicio.adjuntarImagen("medico-1", informe.getId(), "image/png", base64);

        InformePrueba actualizado = servicio.consultar(informe.getId(), "paciente-1", null);
        assertTrue(actualizado.tieneImagen());
        assertEquals("image/png", actualizado.getTipoImagen());
        assertArrayEquals(png, actualizado.getImagen());
        byte[] copia = actualizado.getImagen();
        copia[0] = 0;
        assertArrayEquals(png, actualizado.getImagen());
        assertThrows(IllegalStateException.class,
                () -> servicio.adjuntarImagen("medico-1", informe.getId(), "image/png", base64));
    }

    @Test
    void rechazaImagenInvalidaOAdjuntaPorMedicoAjeno() {
        InformePrueba informe = servicio.crear("medico-1", "prueba-1", "Hallazgos", "Conclusión");
        String pngFake = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});

        assertThrows(IllegalArgumentException.class,
                () -> servicio.adjuntarImagen("medico-1", informe.getId(), "image/png", pngFake));
        assertThrows(NoSuchElementException.class,
                () -> servicio.adjuntarImagen(
                        "medico-2",
                        informe.getId(),
                        "image/png",
                        Base64.getEncoder().encodeToString(
                                new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a})));
        assertFalse(servicio.consultar(informe.getId(), "paciente-1", null).tieneImagen());
    }
}
