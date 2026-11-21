package es.uclm.esi.iso2.B101.servidor.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.SolicitudCita;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoSolicitudCita;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepositorioSolicitudesCitaMemoriaTest {
    private static final LocalDateTime NUEVA_FECHA_HORA = LocalDateTime.of(2026, 12, 15, 11, 0);

    private RepositorioSolicitudesCitaMemoria repositorio;

    @BeforeEach
    void crearRepositorio() {
        repositorio = new RepositorioSolicitudesCitaMemoria();
    }

    @Test
    void guardaSolicitudYLaRecuperaPorIdentificador() {
        SolicitudCita solicitud = crearSolicitud("solicitud-1", "paciente-1", "cita-1");

        repositorio.guardar(solicitud);

        assertSame(solicitud, repositorio.buscarPorId("solicitud-1").orElseThrow());
    }

    @Test
    void devuelveOptionalVacioParaSolicitudInexistente() {
        assertTrue(repositorio.buscarPorId("solicitud-inexistente").isEmpty());
    }

    @Test
    void buscaUnicamenteLasSolicitudesDelPaciente() {
        SolicitudCita primeraSolicitud = crearSolicitud("solicitud-1", "paciente-1", "cita-1");
        SolicitudCita segundaSolicitud = crearSolicitud("solicitud-2", "paciente-1", "cita-2");
        SolicitudCita solicitudDeOtroPaciente = crearSolicitud("solicitud-3", "paciente-2", "cita-3");
        repositorio.guardar(primeraSolicitud);
        repositorio.guardar(segundaSolicitud);
        repositorio.guardar(solicitudDeOtroPaciente);

        List<SolicitudCita> solicitudes = repositorio.buscarPorPacienteId("paciente-1");

        assertEquals(2, solicitudes.size());
        assertTrue(solicitudes.contains(primeraSolicitud));
        assertTrue(solicitudes.contains(segundaSolicitud));
        assertFalse(solicitudes.contains(solicitudDeOtroPaciente));
    }

    @Test
    void devuelveListaVaciaSiElPacienteNoTieneSolicitudes() {
        assertTrue(repositorio.buscarPorPacienteId("paciente-sin-solicitudes").isEmpty());
    }

    @Test
    void buscaUnicamenteLasSolicitudesDeLaCita() {
        SolicitudCita primeraSolicitud = crearSolicitud("solicitud-1", "paciente-1", "cita-1");
        SolicitudCita segundaSolicitud = crearSolicitud("solicitud-2", "paciente-2", "cita-1");
        SolicitudCita solicitudDeOtraCita = crearSolicitud("solicitud-3", "paciente-1", "cita-2");
        repositorio.guardar(primeraSolicitud);
        repositorio.guardar(segundaSolicitud);
        repositorio.guardar(solicitudDeOtraCita);

        List<SolicitudCita> solicitudes = repositorio.buscarPorCitaId("cita-1");

        assertEquals(2, solicitudes.size());
        assertTrue(solicitudes.contains(primeraSolicitud));
        assertTrue(solicitudes.contains(segundaSolicitud));
        assertFalse(solicitudes.contains(solicitudDeOtraCita));
    }

    @Test
    void devuelveListaVaciaSiLaCitaNoTieneSolicitudes() {
        assertTrue(repositorio.buscarPorCitaId("cita-sin-solicitudes").isEmpty());
    }

    @Test
    void reemplazaLaSolicitudConElMismoIdentificador() {
        SolicitudCita solicitudOriginal = crearSolicitud("solicitud-1", "paciente-1", "cita-1");
        SolicitudCita solicitudReemplazo = crearSolicitud("solicitud-1", "paciente-2", "cita-2");
        repositorio.guardar(solicitudOriginal);

        repositorio.guardar(solicitudReemplazo);

        assertSame(solicitudReemplazo, repositorio.buscarPorId("solicitud-1").orElseThrow());
    }

    @Test
    void rechazaGuardarUnaSolicitudNula() {
        assertThrows(IllegalArgumentException.class, () -> repositorio.guardar(null));
    }

    @Test
    void rechazaBuscarPorIdNuloVacioOVacios() {
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorId(null));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorId(""));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorId("   "));
    }

    @Test
    void rechazaBuscarPorPacienteConIdNuloVacioOVacios() {
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorPacienteId(null));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorPacienteId(""));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorPacienteId("   "));
    }

    @Test
    void rechazaBuscarPorCitaConIdNuloVacioOVacios() {
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorCitaId(null));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorCitaId(""));
        assertThrows(IllegalArgumentException.class, () -> repositorio.buscarPorCitaId("   "));
    }

    @Test
    void modificarLasListasDevueltasNoAlteraElAlmacenamiento() {
        SolicitudCita primeraSolicitud = crearSolicitud("solicitud-1", "paciente-1", "cita-1");
        SolicitudCita segundaSolicitud = crearSolicitud("solicitud-2", "paciente-1", "cita-2");
        repositorio.guardar(primeraSolicitud);
        repositorio.guardar(segundaSolicitud);

        List<SolicitudCita> solicitudesDelPaciente = repositorio.buscarPorPacienteId("paciente-1");
        solicitudesDelPaciente.clear();
        List<SolicitudCita> solicitudesDeLaCita = repositorio.buscarPorCitaId("cita-1");
        solicitudesDeLaCita.clear();

        assertEquals(2, repositorio.buscarPorPacienteId("paciente-1").size());
        assertEquals(List.of(primeraSolicitud), repositorio.buscarPorCitaId("cita-1"));
    }

    private SolicitudCita crearSolicitud(String id, String pacienteId, String citaId) {
        return new SolicitudCita(
                id, citaId, pacienteId, TipoSolicitudCita.CAMBIO_FECHA, NUEVA_FECHA_HORA);
    }
}
