package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.EncuestaSatisfaccion;
import es.uclm.esi.iso2.B101.servidor.dominio.Incidencia;
import es.uclm.esi.iso2.B101.servidor.dominio.Notificacion;
import es.uclm.esi.iso2.B101.servidor.dominio.PruebaRealizada;
import es.uclm.esi.iso2.B101.servidor.dominio.SeguimientoPaciente;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoNotificacion;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioEncuestasSatisfaccionMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHistorialPruebasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioIncidenciasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioNotificacionesMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSeguimientosMemoria;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

final class DatosDemostracion {
    private static final String PACIENTE_ID = "paciente-1";
    private static final String MEDICO_ID = "medico-1";

    private DatosDemostracion() {
    }

    static void cargar(
            Clock reloj,
            RepositorioCitasMemoria citas,
            RepositorioNotificacionesMemoria notificaciones,
            RepositorioHistorialPruebasMemoria historial,
            RepositorioIncidenciasMemoria incidencias,
            RepositorioEncuestasSatisfaccionMemoria encuestas,
            RepositorioSeguimientosMemoria seguimientos) {
        Objects.requireNonNull(reloj, "reloj no puede ser nulo");
        Objects.requireNonNull(citas, "citas no puede ser nulo");
        Objects.requireNonNull(notificaciones, "notificaciones no puede ser nulo");
        Objects.requireNonNull(historial, "historial no puede ser nulo");
        Objects.requireNonNull(incidencias, "incidencias no puede ser nulo");
        Objects.requireNonNull(encuestas, "encuestas no puede ser nulo");
        Objects.requireNonNull(seguimientos, "seguimientos no puede ser nulo");

        LocalDate hoy = LocalDate.now(reloj);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        LocalDateTime fechaPruebaRealizada = hoy.minusDays(2).atTime(10, 0);

        Cita citaRealizada = new Cita(
                "DEMO-CITA-REALIZADA",
                PACIENTE_ID,
                MEDICO_ID,
                "TAC",
                fechaPruebaRealizada);
        citaRealizada.marcarRealizada();
        citas.guardar(citaRealizada);
        historial.registrar(new PruebaRealizada(
                "DEMO-PRUEBA-REALIZADA",
                PACIENTE_ID,
                citaRealizada.getTipoPrueba(),
                fechaPruebaRealizada.plusMinutes(30),
                null,
                citaRealizada.getId()));

        Cita citaParaRealizar = new Cita(
                "DEMO-CITA-CU12",
                PACIENTE_ID,
                MEDICO_ID,
                "RM",
                hoy.plusDays(2).atTime(9, 0));
        Cita citaParaIncidencia = new Cita(
                "DEMO-CITA-INCIDENCIA",
                PACIENTE_ID,
                MEDICO_ID,
                "TAC",
                hoy.plusDays(3).atTime(11, 0));
        citas.guardar(citaParaRealizar);
        citas.guardar(citaParaIncidencia);

        notificaciones.guardar(new Notificacion(
                "DEMO-NOTIFICACION-PREPARACION",
                PACIENTE_ID,
                TipoNotificacion.PREPARACION_PRUEBA,
                "Recuerde las indicaciones de preparación para su prueba.",
                citaParaRealizar.getId(),
                ahora,
                null));
        incidencias.guardar(new Incidencia(
                "DEMO-INCIDENCIA-CONSULTA",
                PACIENTE_ID,
                "Consulta de preparación",
                "La paciente solicita confirmar las indicaciones previas a su prueba.",
                null,
                ahora.minusHours(1)));
        encuestas.guardar(new EncuestaSatisfaccion(
                "DEMO-ENCUESTA-SATISFACCION",
                "Satisfacción con la atención",
                "Valore la atención recibida en radiodiagnóstico.",
                true));
        seguimientos.registrar(new SeguimientoPaciente(
                "DEMO-SEGUIMIENTO-TAC",
                PACIENTE_ID,
                "TAC",
                "Control radiológico periódico",
                6,
                fechaPruebaRealizada.toLocalDate(),
                hoy.plusMonths(6),
                "Acudir con la documentación clínica."));
    }
}
