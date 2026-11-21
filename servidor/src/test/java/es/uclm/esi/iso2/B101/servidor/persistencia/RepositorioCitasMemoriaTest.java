package es.uclm.esi.iso2.B101.servidor.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepositorioCitasMemoriaTest {
    private static final LocalDateTime FECHA_HORA = LocalDateTime.of(2026, 12, 1, 10, 30);

    private RepositorioCitasMemoria repositorio;

    @BeforeEach
    void crearRepositorio() {
        repositorio = new RepositorioCitasMemoria();
    }

    @Test
    void guardaCitaYLaRecuperaPorIdentificador() {
        Cita cita = crearCita("cita-1", "paciente-1");

        repositorio.guardar(cita);

        assertSame(cita, repositorio.buscarPorId("cita-1").orElseThrow());
    }

    @Test
    void devuelveOptionalVacioParaCitaInexistente() {
        assertTrue(repositorio.buscarPorId("cita-inexistente").isEmpty());
    }

    @Test
    void buscaTodasLasCitasDelPaciente() {
        Cita primeraCita = crearCita("cita-1", "paciente-1");
        Cita segundaCita = crearCita("cita-2", "paciente-1");
        repositorio.guardar(primeraCita);
        repositorio.guardar(segundaCita);

        List<Cita> citas = repositorio.buscarPorPacienteId("paciente-1");

        assertEquals(2, citas.size());
        assertTrue(citas.containsAll(List.of(primeraCita, segundaCita)));
    }

    @Test
    void excluyeCitasDeOtrosPacientes() {
        Cita citaDelPaciente = crearCita("cita-1", "paciente-1");
        repositorio.guardar(citaDelPaciente);
        repositorio.guardar(crearCita("cita-2", "paciente-2"));

        assertEquals(List.of(citaDelPaciente), repositorio.buscarPorPacienteId("paciente-1"));
    }

    @Test
    void devuelveListaVaciaSiElPacienteNoTieneCitas() {
        List<Cita> citas = repositorio.buscarPorPacienteId("paciente-sin-citas");

        assertNotNull(citas);
        assertTrue(citas.isEmpty());
    }

    @Test
    void reemplazaLaCitaConElMismoIdentificador() {
        Cita citaOriginal = crearCita("cita-1", "paciente-1");
        Cita citaReemplazo = crearCita("cita-1", "paciente-2");
        repositorio.guardar(citaOriginal);

        repositorio.guardar(citaReemplazo);

        assertSame(citaReemplazo, repositorio.buscarPorId("cita-1").orElseThrow());
    }

    @Test
    void rechazaGuardarUnaCitaNula() {
        assertThrows(IllegalArgumentException.class, () -> repositorio.guardar(null));
    }

    @Test
    void rechazaIdentificadoresNulosOVaciosEnLasBusquedas() {
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorId(null));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorId(""));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorPacienteId(null));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorPacienteId(""));
    }

    @Test
    void modificarLaListaDevueltaNoAlteraElRepositorio() {
        Cita cita = crearCita("cita-1", "paciente-1");
        repositorio.guardar(cita);
        List<Cita> citasDevueltas = repositorio.buscarPorPacienteId("paciente-1");

        citasDevueltas.clear();

        assertEquals(List.of(cita), repositorio.buscarPorPacienteId("paciente-1"));
    }

    private Cita crearCita(String id, String pacienteId) {
        return new Cita(id, pacienteId, "radiografia", FECHA_HORA);
    }
}
