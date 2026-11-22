package es.uclm.esi.iso2.B101.servidor.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CitaTest {
    private static final LocalDateTime FECHA_HORA = LocalDateTime.of(2026, 12, 1, 10, 30);

    @Test
    void creaCitaValidaEnEstadoProgramada() {
        Cita cita = crearCita();

        assertEquals(EstadoCita.PROGRAMADA, cita.getEstado());
    }

    @Test
    void devuelveLosDatosIntroducidosEnLosGetters() {
        Cita cita = crearCita();

        assertEquals("cita-1", cita.getId());
        assertEquals("paciente-1", cita.getPacienteId());
        assertNull(cita.getMedicoId());
        assertEquals("radiografia", cita.getTipoPrueba());
        assertEquals(FECHA_HORA, cita.getFechaHora());
    }

    @Test
    void permiteAsociarLaCitaAUnMedico() {
        Cita cita = new Cita("cita-1", "paciente-1", "medico-1", "RM", FECHA_HORA);

        assertEquals("medico-1", cita.getMedicoId());
    }

    @Test
    void confirmarCambiaElEstadoAConfirmada() {
        Cita cita = crearCita();

        cita.confirmar();

        assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
    }

    @Test
    void marcarRealizadaActualizaElEstadoYBloqueaTransicionesIncompatibles() {
        Cita cita = crearCita();

        cita.marcarRealizada();

        assertEquals(EstadoCita.REALIZADA, cita.getEstado());
        assertThrows(IllegalStateException.class, cita::marcarRealizada);
        assertThrows(IllegalStateException.class, cita::confirmar);
        assertThrows(IllegalStateException.class, cita::cancelar);
    }

    @Test
    void noPermiteMarcarComoRealizadaUnaCitaCancelada() {
        Cita cita = crearCita();
        cita.cancelar();

        assertThrows(IllegalStateException.class, cita::marcarRealizada);
        assertEquals(EstadoCita.CANCELADA, cita.getEstado());
    }

    @Test
    void rechazaIdentificadorNuloOVacio() {
        assertThrows(IllegalArgumentException.class,
                () -> new Cita(null, "paciente-1", "radiografia", FECHA_HORA));
        assertThrows(IllegalArgumentException.class,
                () -> new Cita("", "paciente-1", "radiografia", FECHA_HORA));
    }

    @Test
    void rechazaPacienteIdNuloOVacio() {
        assertThrows(IllegalArgumentException.class,
                () -> new Cita("cita-1", null, "radiografia", FECHA_HORA));
        assertThrows(IllegalArgumentException.class,
                () -> new Cita("cita-1", "", "radiografia", FECHA_HORA));
    }

    @Test
    void rechazaMedicoIdVacio() {
        assertThrows(IllegalArgumentException.class,
                () -> new Cita("cita-1", "paciente-1", "", "RM", FECHA_HORA));
    }

    @Test
    void rechazaTipoPruebaNuloOVacio() {
        assertThrows(IllegalArgumentException.class,
                () -> new Cita("cita-1", "paciente-1", null, FECHA_HORA));
        assertThrows(IllegalArgumentException.class,
                () -> new Cita("cita-1", "paciente-1", "", FECHA_HORA));
    }

    @Test
    void rechazaFechaHoraNula() {
        assertThrows(IllegalArgumentException.class,
                () -> new Cita("cita-1", "paciente-1", "radiografia", null));
    }

    private Cita crearCita() {
        return new Cita("cita-1", "paciente-1", "radiografia", FECHA_HORA);
    }
}
