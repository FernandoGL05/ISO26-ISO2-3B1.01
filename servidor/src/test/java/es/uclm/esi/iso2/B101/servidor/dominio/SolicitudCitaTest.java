package es.uclm.esi.iso2.B101.servidor.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SolicitudCitaTest {
    private static final LocalDateTime NUEVA_FECHA_HORA = LocalDateTime.of(2026, 12, 15, 11, 0);

    @Test
    void creaSolicitudValidaDeCambioDeFecha() {
        SolicitudCita solicitud = crearSolicitud(
                "solicitud-1", "cita-1", "paciente-1", TipoSolicitudCita.CAMBIO_FECHA, NUEVA_FECHA_HORA);

        assertEquals(TipoSolicitudCita.CAMBIO_FECHA, solicitud.getTipo());
        assertEquals(NUEVA_FECHA_HORA, solicitud.getNuevaFechaHora());
    }

    @Test
    void creaSolicitudValidaDeCancelacion() {
        SolicitudCita solicitud = crearSolicitud(
                "solicitud-1", "cita-1", "paciente-1", TipoSolicitudCita.CANCELACION, null);

        assertEquals(TipoSolicitudCita.CANCELACION, solicitud.getTipo());
        assertNull(solicitud.getNuevaFechaHora());
    }

    @Test
    void todaSolicitudNuevaComienzaPendiente() {
        SolicitudCita solicitudCambio = crearSolicitud(
                "solicitud-1", "cita-1", "paciente-1", TipoSolicitudCita.CAMBIO_FECHA, NUEVA_FECHA_HORA);
        SolicitudCita solicitudCancelacion = crearSolicitud(
                "solicitud-2", "cita-2", "paciente-2", TipoSolicitudCita.CANCELACION, null);

        assertEquals(EstadoSolicitudCita.PENDIENTE, solicitudCambio.getEstado());
        assertEquals(EstadoSolicitudCita.PENDIENTE, solicitudCancelacion.getEstado());
    }

    @Test
    void gettersDevuelvenTodosLosDatosIntroducidos() {
        SolicitudCita solicitud = crearSolicitud(
                "solicitud-1", "cita-1", "paciente-1", TipoSolicitudCita.CAMBIO_FECHA, NUEVA_FECHA_HORA);

        assertEquals("solicitud-1", solicitud.getId());
        assertEquals("cita-1", solicitud.getCitaId());
        assertEquals("paciente-1", solicitud.getPacienteId());
        assertEquals(TipoSolicitudCita.CAMBIO_FECHA, solicitud.getTipo());
        assertEquals(NUEVA_FECHA_HORA, solicitud.getNuevaFechaHora());
        assertEquals(EstadoSolicitudCita.PENDIENTE, solicitud.getEstado());
    }

    @Test
    void rechazaIdentificadorDeSolicitudNuloVacioOVacios() {
        assertThrows(IllegalArgumentException.class,
                () -> crearSolicitud(null, "cita-1", "paciente-1", TipoSolicitudCita.CANCELACION, null));
        assertThrows(IllegalArgumentException.class,
                () -> crearSolicitud("", "cita-1", "paciente-1", TipoSolicitudCita.CANCELACION, null));
        assertThrows(IllegalArgumentException.class,
                () -> crearSolicitud("   ", "cita-1", "paciente-1", TipoSolicitudCita.CANCELACION, null));
    }

    @Test
    void rechazaIdentificadorDeCitaNuloVacioOVacios() {
        assertThrows(IllegalArgumentException.class,
                () -> crearSolicitud("solicitud-1", null, "paciente-1", TipoSolicitudCita.CANCELACION, null));
        assertThrows(IllegalArgumentException.class,
                () -> crearSolicitud("solicitud-1", "", "paciente-1", TipoSolicitudCita.CANCELACION, null));
        assertThrows(IllegalArgumentException.class,
                () -> crearSolicitud("solicitud-1", "   ", "paciente-1", TipoSolicitudCita.CANCELACION, null));
    }

    @Test
    void rechazaIdentificadorDePacienteNuloVacioOVacios() {
        assertThrows(IllegalArgumentException.class,
                () -> crearSolicitud("solicitud-1", "cita-1", null, TipoSolicitudCita.CANCELACION, null));
        assertThrows(IllegalArgumentException.class,
                () -> crearSolicitud("solicitud-1", "cita-1", "", TipoSolicitudCita.CANCELACION, null));
        assertThrows(IllegalArgumentException.class,
                () -> crearSolicitud("solicitud-1", "cita-1", "   ", TipoSolicitudCita.CANCELACION, null));
    }

    @Test
    void rechazaTipoDeSolicitudNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> crearSolicitud("solicitud-1", "cita-1", "paciente-1", null, null));
    }

    @Test
    void rechazaCambioDeFechaSinNuevaFechaHora() {
        assertThrows(IllegalArgumentException.class,
                () -> crearSolicitud("solicitud-1", "cita-1", "paciente-1", TipoSolicitudCita.CAMBIO_FECHA, null));
    }

    @Test
    void rechazaCancelacionConNuevaFechaHora() {
        assertThrows(IllegalArgumentException.class,
                () -> crearSolicitud(
                        "solicitud-1", "cita-1", "paciente-1", TipoSolicitudCita.CANCELACION, NUEVA_FECHA_HORA));
    }

    private SolicitudCita crearSolicitud(String id, String citaId, String pacienteId, TipoSolicitudCita tipo,
            LocalDateTime nuevaFechaHora) {
        return new SolicitudCita(id, citaId, pacienteId, tipo, nuevaFechaHora);
    }
}
