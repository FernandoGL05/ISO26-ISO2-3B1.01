package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.integracion.ProveedorSSOSimulado;
import es.uclm.esi.iso2.B101.servidor.persistencia.AsociacionIdentidadesClinicas;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioAuditoriaAccesoMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioEncuestasSatisfaccionMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHistorialPruebasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioIncidenciasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioInformesPruebaMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioNecesidadesCitaSeguimientoMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioNotificacionesMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioPrescripcionesDiagnosticasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioPrescripcionesSeguimientoMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioRespuestasEncuestasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSolicitudesCitaMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSesionesMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSeguimientosMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioUsuariosMemoria;
import es.uclm.esi.iso2.B101.servidor.seguridad.AutenticadorSesion;
import es.uclm.esi.iso2.B101.servidor.seguridad.GeneradorTokens;
import es.uclm.esi.iso2.B101.servidor.seguridad.GestorContrasenas;
import es.uclm.esi.iso2.B101.servidor.servicio.PoliticaPrescripcionDiagnosticaPorVinculo;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAgendas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioCitas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioEncuestasSatisfaccion;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioHistorialPruebas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioIncidencias;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioInformesPrueba;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioNotificaciones;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioPrescripcionesDiagnosticas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioPrescripcionesSeguimiento;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioSeguimientos;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioRealizacionPruebas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAutenticacion;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Punto de entrada del servidor de Radiodiagnóstico del SESCAM.
 */
public class AplicacionServidor {

    public static final int PUERTO_POR_DEFECTO = 8080;

    public static void main(String[] args) throws IOException {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : PUERTO_POR_DEFECTO;
        ServidorHTTP servidor = crearServidor(puerto);
        servidor.iniciar();
        System.out.println("Servidor de Radiodiagnóstico SESCAM escuchando en http://localhost:" + servidor.getPuerto());
    }

    public static ServidorHTTP crearServidor(int puerto) throws IOException {
        return crearServidor(puerto, Clock.systemUTC(), new RepositorioCitasMemoria());
    }

    public static ServidorHTTP crearServidor(
            int puerto, Clock relojAutenticacion, RepositorioCitasMemoria repositorioCitas) throws IOException {
        Objects.requireNonNull(relojAutenticacion, "relojAutenticacion no puede ser nulo");
        Objects.requireNonNull(repositorioCitas, "repositorioCitas no puede ser nulo");
        GestorContrasenas gestorContrasenas = new GestorContrasenas();
        RepositorioSesionesMemoria repositorioSesiones = new RepositorioSesionesMemoria();
        ServicioAutenticacion servicioAutenticacion = new ServicioAutenticacion(
                RepositorioUsuariosMemoria.conUsuariosPorDefecto(gestorContrasenas),
                repositorioSesiones,
                new RepositorioAuditoriaAccesoMemoria(),
                gestorContrasenas,
                new GeneradorTokens(),
                new ProveedorSSOSimulado(),
                relojAutenticacion,
                ServicioAutenticacion.MAX_INTENTOS_FALLIDOS_POR_DEFECTO,
                ServicioAutenticacion.DURACION_BLOQUEO_POR_DEFECTO,
                ServicioAutenticacion.DURACION_SESION_POR_DEFECTO
        );

        AsociacionIdentidadesClinicas asociaciones = new AsociacionIdentidadesClinicas(
                Map.of("USR-001", "paciente-1"),
                Map.of("USR-002", "medico-1"));
        AutenticadorSesion autenticadorSesion = new AutenticadorSesion(servicioAutenticacion);
        ServicioCitas servicioCitas =
                new ServicioCitas(repositorioCitas, new RepositorioSolicitudesCitaMemoria());
        ServicioPrescripcionesDiagnosticas servicioPrescripciones = new ServicioPrescripcionesDiagnosticas(
                new RepositorioPrescripcionesDiagnosticasMemoria(),
                Clock.systemDefaultZone(),
                new PoliticaPrescripcionDiagnosticaPorVinculo(
                        Map.of("medico-1", Set.of("paciente-1"))));
        ServicioNotificaciones servicioNotificaciones =
                new ServicioNotificaciones(new RepositorioNotificacionesMemoria());
        RepositorioHistorialPruebasMemoria repositorioHistorialPruebas =
                new RepositorioHistorialPruebasMemoria();
        ServicioHistorialPruebas servicioHistorialPruebas =
                new ServicioHistorialPruebas(repositorioHistorialPruebas);
        RepositorioIncidenciasMemoria repositorioIncidencias = new RepositorioIncidenciasMemoria();
        ServicioIncidencias servicioIncidencias = new ServicioIncidencias(repositorioIncidencias);
        ServicioEncuestasSatisfaccion servicioEncuestas = new ServicioEncuestasSatisfaccion(
                new RepositorioEncuestasSatisfaccionMemoria(),
                new RepositorioRespuestasEncuestasMemoria());
        ServicioSeguimientos servicioSeguimientos =
                new ServicioSeguimientos(new RepositorioSeguimientosMemoria());
        Map<String, Set<String>> pacientesPorMedico = Map.of("medico-1", Set.of("paciente-1"));
        ServicioPrescripcionesSeguimiento servicioPrescripcionesSeguimiento =
                new ServicioPrescripcionesSeguimiento(
                        new RepositorioPrescripcionesSeguimientoMemoria(),
                        new RepositorioNecesidadesCitaSeguimientoMemoria(),
                        Clock.systemDefaultZone(),
                        (medicoId, pacienteId) ->
                                pacientesPorMedico.getOrDefault(medicoId, Set.of()).contains(pacienteId));
        ServicioAgendas servicioAgendas = new ServicioAgendas(repositorioCitas);
        ServicioInformesPrueba servicioInformesPrueba = new ServicioInformesPrueba(
                repositorioCitas,
                repositorioHistorialPruebas,
                new RepositorioInformesPruebaMemoria());
        ServicioRealizacionPruebas servicioRealizacionPruebas = new ServicioRealizacionPruebas(
                repositorioCitas,
                repositorioHistorialPruebas,
                repositorioIncidencias);
        AutorizadorPrescripcionesPorSesion autorizadorPrescripciones =
                new AutorizadorPrescripcionesPorSesion(autenticadorSesion, asociaciones);

        return new ServidorHTTP(
                puerto,
                servicioAutenticacion,
                servicioCitas,
                new AutorizadorCitasPorSesion(autenticadorSesion, asociaciones),
                servicioPrescripciones,
                autorizadorPrescripciones,
                servicioNotificaciones,
                new AutorizadorNotificacionesPorSesion(autenticadorSesion, asociaciones),
                servicioHistorialPruebas,
                new AutorizadorHistorialPruebasPorSesion(autenticadorSesion, asociaciones),
                servicioIncidencias,
                new AutorizadorIncidenciasPorSesion(autenticadorSesion, asociaciones),
                servicioEncuestas,
                new AutorizadorEncuestasPorSesion(autenticadorSesion, asociaciones),
                servicioSeguimientos,
                new AutorizadorSeguimientosPorSesion(autenticadorSesion, asociaciones),
                servicioPrescripcionesSeguimiento,
                new AutorizadorPrescripcionesSeguimientoPorSesion(autenticadorSesion, asociaciones),
                servicioAgendas,
                servicioInformesPrueba,
                new AutorizadorInformesPruebaPorSesion(autenticadorSesion, asociaciones),
                servicioRealizacionPruebas);
    }
}
