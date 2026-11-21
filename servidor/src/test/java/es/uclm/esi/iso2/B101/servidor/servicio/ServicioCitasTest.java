package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.EstadoCita;
import es.uclm.esi.iso2.B101.servidor.dominio.EstadoSolicitudCita;
import es.uclm.esi.iso2.B101.servidor.dominio.SolicitudCita;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoSolicitudCita;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSolicitudesCitaMemoria;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioCitasTest {
    private static final LocalDateTime FECHA_HORA = LocalDateTime.of(2026, 12, 1, 10, 30);
    private static final LocalDateTime NUEVA_FECHA_HORA = LocalDateTime.of(2026, 12, 15, 11, 0);

    private RepositorioCitasMemoria repositorio;
    private RepositorioSolicitudesCitaMemoria repositorioSolicitudes;
    private ServicioCitas servicio;

    @BeforeEach
    void crearServicioConRepositorioNuevo() {
        repositorio = new RepositorioCitasMemoria();
        repositorioSolicitudes = new RepositorioSolicitudesCitaMemoria();
        servicio = new ServicioCitas(repositorio, repositorioSolicitudes);
    }

    @Test
    void consultaUnicamenteLasCitasDelPaciente() {
        Cita primeraCita = guardarCita("cita-1", "paciente-1");
        Cita segundaCita = guardarCita("cita-2", "paciente-1");
        Cita citaDeOtroPaciente = guardarCita("cita-3", "paciente-2");

        List<Cita> citas = servicio.consultarCitas("paciente-1");

        assertEquals(2, citas.size());
        assertTrue(citas.contains(primeraCita));
        assertTrue(citas.contains(segundaCita));
        assertFalse(citas.contains(citaDeOtroPaciente));
    }

    @Test
    void devuelveListaVaciaSiElPacienteNoTieneCitas() {
        assertTrue(servicio.consultarCitas("paciente-sin-citas").isEmpty());
    }

    @Test
    void rechazaConsultarConIdentificadorNuloOVacio() {
        assertThrows(IllegalArgumentException.class, () -> servicio.consultarCitas(null));
        assertThrows(IllegalArgumentException.class, () -> servicio.consultarCitas(""));
    }

    @Test
    void confirmarCitaProgramadaCambiaSuEstadoAConfirmada() {
        Cita cita = guardarCita("cita-1", "paciente-1");

        servicio.confirmarCita("paciente-1", "cita-1");

        assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
    }

    @Test
    void rechazaConfirmarUnaCitaInexistente() {
        assertThrows(NoSuchElementException.class,
                () -> servicio.confirmarCita("paciente-1", "cita-inexistente"));
    }

    @Test
    void rechazaConfirmarUnaCitaDeOtroPaciente() {
        guardarCita("cita-1", "paciente-2");

        assertThrows(NoSuchElementException.class,
                () -> servicio.confirmarCita("paciente-1", "cita-1"));
    }

    @Test
    void mantieneElEstadoSiSeRechazaUnaCitaDeOtroPaciente() {
        Cita citaAjena = guardarCita("cita-1", "paciente-2");

        assertThrows(NoSuchElementException.class,
                () -> servicio.confirmarCita("paciente-1", "cita-1"));

        assertEquals(EstadoCita.PROGRAMADA, citaAjena.getEstado());
    }

    @Test
    void confirmarUnaCitaYaConfirmadaNoProduceErrores() {
        Cita cita = guardarCita("cita-1", "paciente-1");
        cita.confirmar();

        assertDoesNotThrow(() -> servicio.confirmarCita("paciente-1", "cita-1"));

        assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
    }

    @Test
    void rechazaConfirmarConIdentificadoresNulosOVacios() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.confirmarCita(null, "cita-1"));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.confirmarCita("", "cita-1"));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.confirmarCita("paciente-1", null));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.confirmarCita("paciente-1", ""));
    }

    @Test
    void rechazaConstruirElServicioConRepositorioNulo() {
        assertThrows(NullPointerException.class, () -> new ServicioCitas(null));
    }

    @Test
    void solicitarCambioFechaCreaYGuardaSolicitudSinModificarLaCita() {
        Cita cita = guardarCita("cita-1", "paciente-1");
        LocalDateTime fechaOriginal = cita.getFechaHora();
        EstadoCita estadoOriginal = cita.getEstado();

        SolicitudCita solicitud = servicio.solicitarCambioFecha(
                "paciente-1", "cita-1", NUEVA_FECHA_HORA);

        assertEquals(TipoSolicitudCita.CAMBIO_FECHA, solicitud.getTipo());
        assertEquals(EstadoSolicitudCita.PENDIENTE, solicitud.getEstado());
        assertEquals(NUEVA_FECHA_HORA, solicitud.getNuevaFechaHora());
        assertNotNull(solicitud.getId());
        assertFalse(solicitud.getId().isBlank());
        assertSame(solicitud, repositorioSolicitudes.buscarPorId(solicitud.getId()).orElseThrow());
        assertEquals(fechaOriginal, cita.getFechaHora());
        assertEquals(estadoOriginal, cita.getEstado());
    }

    @Test
    void solicitarCambioFechaRechazaUnaCitaInexistente() {
        assertThrows(NoSuchElementException.class,
                () -> servicio.solicitarCambioFecha("paciente-1", "cita-inexistente", NUEVA_FECHA_HORA));
    }

    @Test
    void solicitarCambioFechaRechazaCitaAjenaSinGuardarSolicitud() {
        guardarCita("cita-1", "paciente-2");

        assertThrows(NoSuchElementException.class,
                () -> servicio.solicitarCambioFecha("paciente-1", "cita-1", NUEVA_FECHA_HORA));

        assertTrue(repositorioSolicitudes.buscarPorCitaId("cita-1").isEmpty());
    }

    @Test
    void solicitarCambioFechaRechazaNuevaFechaNula() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.solicitarCambioFecha("paciente-1", "cita-1", null));
    }

    @Test
    void solicitarCambioFechaRechazaIdentificadoresNulosOVacios() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.solicitarCambioFecha(null, "cita-1", NUEVA_FECHA_HORA));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.solicitarCambioFecha("", "cita-1", NUEVA_FECHA_HORA));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.solicitarCambioFecha("paciente-1", null, NUEVA_FECHA_HORA));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.solicitarCambioFecha("paciente-1", "", NUEVA_FECHA_HORA));
    }

    @Test
    void solicitarCancelacionCreaYGuardaSolicitudSinModificarLaCita() {
        Cita cita = guardarCita("cita-1", "paciente-1");
        LocalDateTime fechaOriginal = cita.getFechaHora();
        EstadoCita estadoOriginal = cita.getEstado();

        SolicitudCita solicitud = servicio.solicitarCancelacion("paciente-1", "cita-1");

        assertEquals(TipoSolicitudCita.CANCELACION, solicitud.getTipo());
        assertEquals(EstadoSolicitudCita.PENDIENTE, solicitud.getEstado());
        assertNull(solicitud.getNuevaFechaHora());
        assertSame(solicitud, repositorioSolicitudes.buscarPorId(solicitud.getId()).orElseThrow());
        assertEquals(fechaOriginal, cita.getFechaHora());
        assertEquals(estadoOriginal, cita.getEstado());
    }

    @Test
    void solicitarCancelacionRechazaUnaCitaInexistente() {
        assertThrows(NoSuchElementException.class,
                () -> servicio.solicitarCancelacion("paciente-1", "cita-inexistente"));
    }

    @Test
    void solicitarCancelacionRechazaCitaAjenaSinGuardarSolicitud() {
        guardarCita("cita-1", "paciente-2");

        assertThrows(NoSuchElementException.class,
                () -> servicio.solicitarCancelacion("paciente-1", "cita-1"));

        assertTrue(repositorioSolicitudes.buscarPorCitaId("cita-1").isEmpty());
    }

    @Test
    void solicitarCancelacionRechazaIdentificadoresNulosOVacios() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.solicitarCancelacion(null, "cita-1"));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.solicitarCancelacion("", "cita-1"));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.solicitarCancelacion("paciente-1", null));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.solicitarCancelacion("paciente-1", ""));
    }

    @Test
    void consultarSolicitudesDevuelveSoloLasDelPaciente() {
        SolicitudCita primeraSolicitud = crearSolicitud("solicitud-1", "paciente-1", "cita-1");
        SolicitudCita segundaSolicitud = crearSolicitud("solicitud-2", "paciente-1", "cita-2");
        SolicitudCita solicitudDeOtroPaciente = crearSolicitud("solicitud-3", "paciente-2", "cita-3");
        repositorioSolicitudes.guardar(primeraSolicitud);
        repositorioSolicitudes.guardar(segundaSolicitud);
        repositorioSolicitudes.guardar(solicitudDeOtroPaciente);

        List<SolicitudCita> solicitudes = servicio.consultarSolicitudes("paciente-1");

        assertEquals(2, solicitudes.size());
        assertTrue(solicitudes.contains(primeraSolicitud));
        assertTrue(solicitudes.contains(segundaSolicitud));
        assertFalse(solicitudes.contains(solicitudDeOtroPaciente));
    }

    @Test
    void consultarSolicitudesDevuelveListaVaciaSiNoHaySolicitudes() {
        assertTrue(servicio.consultarSolicitudes("paciente-sin-solicitudes").isEmpty());
    }

    @Test
    void consultarSolicitudesRechazaIdentificadoresNulosOVacios() {
        assertThrows(IllegalArgumentException.class, () -> servicio.consultarSolicitudes(null));
        assertThrows(IllegalArgumentException.class, () -> servicio.consultarSolicitudes(""));
    }

    @Test
    void solicitudesConsecutivasTienenIdentificadoresDistintos() {
        guardarCita("cita-1", "paciente-1");
        guardarCita("cita-2", "paciente-1");

        SolicitudCita primeraSolicitud = servicio.solicitarCancelacion("paciente-1", "cita-1");
        SolicitudCita segundaSolicitud = servicio.solicitarCancelacion("paciente-1", "cita-2");

        assertNotEquals(primeraSolicitud.getId(), segundaSolicitud.getId());
    }

    @Test
    void constructorConDosRepositoriosRechazaCualquieraNulo() {
        assertThrows(NullPointerException.class,
                () -> new ServicioCitas(null, new RepositorioSolicitudesCitaMemoria()));
        assertThrows(NullPointerException.class,
                () -> new ServicioCitas(new RepositorioCitasMemoria(), null));
    }

    private Cita guardarCita(String id, String pacienteId) {
        Cita cita = new Cita(id, pacienteId, "radiografia", FECHA_HORA);
        repositorio.guardar(cita);
        return cita;
    }

    private SolicitudCita crearSolicitud(String id, String pacienteId, String citaId) {
        return new SolicitudCita(id, citaId, pacienteId, TipoSolicitudCita.CAMBIO_FECHA, NUEVA_FECHA_HORA);
    }
}
