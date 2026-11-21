package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.Notificacion;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoNotificacion;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioNotificacionesMemoria;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioNotificacionesTest {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 3, 15, 10, 0);

    private RepositorioNotificacionesMemoria repositorio;
    private ServicioNotificaciones servicio;

    @BeforeEach
    void prepararServicio() {
        repositorio = new RepositorioNotificacionesMemoria();
        servicio = new ServicioNotificaciones(
                repositorio,
                Clock.fixed(AHORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                Duration.ofHours(24));
    }

    @Test
    void registraPreparacionEInformeDisponible() {
        Notificacion preparacion =
                servicio.registrarPreparacionPrueba("paciente-1", "prueba-1", "Acude en ayunas.");
        Notificacion informe = servicio.registrarInformeDisponible("paciente-1", "informe-1");

        assertEquals(TipoNotificacion.PREPARACION_PRUEBA, preparacion.getTipo());
        assertEquals("Acude en ayunas.", preparacion.getMensaje());
        assertEquals(TipoNotificacion.INFORME_DISPONIBLE, informe.getTipo());
        assertEquals("informe-1", informe.getReferenciaId());
    }

    @Test
    void consultaSoloLasNotificacionesDelPaciente() {
        Notificacion propia = servicio.registrarInformeDisponible("paciente-1", "informe-1");
        servicio.registrarInformeDisponible("paciente-2", "informe-2");

        assertEquals(List.of(propia), servicio.consultarNotificaciones("paciente-1"));
        assertTrue(servicio.consultarNotificaciones("paciente-sin-avisos").isEmpty());
    }

    @Test
    void marcaComoLeidaUnaNotificacionPropia() {
        Notificacion notificacion = servicio.registrarInformeDisponible("paciente-1", "informe-1");

        Notificacion resultado = servicio.marcarComoLeida("paciente-1", notificacion.getId());

        assertTrue(resultado.estaLeida());
        assertTrue(servicio.consultarNotificaciones("paciente-1").get(0).estaLeida());
    }

    @Test
    void impideMarcarUnaNotificacionAjenaComoLeida() {
        Notificacion ajena = servicio.registrarInformeDisponible("paciente-2", "informe-2");

        assertThrows(
                NoSuchElementException.class,
                () -> servicio.marcarComoLeida("paciente-1", ajena.getId()));
        assertFalse(ajena.estaLeida());
    }

    @Test
    void generaRecordatorioAlLlegarAlMomentoDeAvisoYNoLoDuplica() {
        ServicioNotificaciones.CitaProgramada cita = new ServicioNotificaciones.CitaProgramada(
                "cita-1", "paciente-1", AHORA.plusHours(24));

        List<Notificacion> primeraGeneracion = servicio.generarRecordatoriosCitasProximas(List.of(cita));
        List<Notificacion> segundaGeneracion = servicio.generarRecordatoriosCitasProximas(List.of(cita));

        assertEquals(1, primeraGeneracion.size());
        assertEquals(TipoNotificacion.RECORDATORIO_CITA, primeraGeneracion.get(0).getTipo());
        assertEquals(AHORA, primeraGeneracion.get(0).getMomentoAviso());
        assertTrue(segundaGeneracion.isEmpty());
        assertEquals(1, servicio.consultarNotificaciones("paciente-1").size());
    }

    @Test
    void noGeneraRecordatorioAntesDelMomentoDeAviso() {
        ServicioNotificaciones.CitaProgramada cita = new ServicioNotificaciones.CitaProgramada(
                "cita-1", "paciente-1", AHORA.plusHours(25));

        assertTrue(servicio.generarRecordatoriosCitasProximas(List.of(cita)).isEmpty());
    }

    @Test
    void permiteConfigurarLaAnticipacionDelRecordatorio() {
        ServicioNotificaciones servicioConDosHoras = new ServicioNotificaciones(
                repositorio,
                Clock.fixed(AHORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                Duration.ofHours(2));
        ServicioNotificaciones.CitaProgramada cita = new ServicioNotificaciones.CitaProgramada(
                "cita-2", "paciente-1", AHORA.plusHours(2));

        assertEquals(1, servicioConDosHoras.generarRecordatoriosCitasProximas(List.of(cita)).size());
    }

    @Test
    void rechazaIdentificadoresInvalidosAlConsultarORegistrar() {
        assertThrows(IllegalArgumentException.class, () -> servicio.consultarNotificaciones(" "));
        assertThrows(
                IllegalArgumentException.class,
                () -> servicio.registrarPreparacionPrueba("paciente-1", "prueba-1", ""));
    }
}
