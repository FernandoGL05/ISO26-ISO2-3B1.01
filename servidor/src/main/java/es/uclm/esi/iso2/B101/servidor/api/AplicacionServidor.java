package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.integracion.ProveedorSSOSimulado;
import es.uclm.esi.iso2.B101.servidor.persistencia.AsociacionIdentidadesClinicas;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioAuditoriaAccesoMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioEncuestasSatisfaccionMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHistorialPruebasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioIncidenciasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioNotificacionesMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioPrescripcionesDiagnosticasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioRespuestasEncuestasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSolicitudesCitaMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSesionesMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSeguimientosMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioUsuariosMemoria;
import es.uclm.esi.iso2.B101.servidor.seguridad.AutenticadorSesion;
import es.uclm.esi.iso2.B101.servidor.seguridad.GeneradorTokens;
import es.uclm.esi.iso2.B101.servidor.seguridad.GestorContrasenas;
import es.uclm.esi.iso2.B101.servidor.servicio.PoliticaPrescripcionDiagnosticaPorVinculo;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioCitas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioEncuestasSatisfaccion;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioHistorialPruebas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioIncidencias;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioNotificaciones;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioPrescripcionesDiagnosticas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioSeguimientos;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAutenticacion;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
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
        GestorContrasenas gestorContrasenas = new GestorContrasenas();
        RepositorioSesionesMemoria repositorioSesiones = new RepositorioSesionesMemoria();
        ServicioAutenticacion servicioAutenticacion = new ServicioAutenticacion(
                RepositorioUsuariosMemoria.conUsuariosPorDefecto(gestorContrasenas),
                repositorioSesiones,
                new RepositorioAuditoriaAccesoMemoria(),
                gestorContrasenas,
                new GeneradorTokens(),
                new ProveedorSSOSimulado()
        );

        AsociacionIdentidadesClinicas asociaciones = new AsociacionIdentidadesClinicas(
                Map.of("USR-001", "paciente-1"),
                Map.of("USR-002", "medico-1"));
        AutenticadorSesion autenticadorSesion = new AutenticadorSesion(servicioAutenticacion);
        ServicioCitas servicioCitas = new ServicioCitas(
                new RepositorioCitasMemoria(), new RepositorioSolicitudesCitaMemoria());
        ServicioPrescripcionesDiagnosticas servicioPrescripciones = new ServicioPrescripcionesDiagnosticas(
                new RepositorioPrescripcionesDiagnosticasMemoria(),
                Clock.systemDefaultZone(),
                new PoliticaPrescripcionDiagnosticaPorVinculo(
                        Map.of("medico-1", Set.of("paciente-1"))));
        ServicioNotificaciones servicioNotificaciones =
                new ServicioNotificaciones(new RepositorioNotificacionesMemoria());
        ServicioHistorialPruebas servicioHistorialPruebas =
                new ServicioHistorialPruebas(new RepositorioHistorialPruebasMemoria());
        ServicioIncidencias servicioIncidencias =
                new ServicioIncidencias(new RepositorioIncidenciasMemoria());
        ServicioEncuestasSatisfaccion servicioEncuestas = new ServicioEncuestasSatisfaccion(
                new RepositorioEncuestasSatisfaccionMemoria(),
                new RepositorioRespuestasEncuestasMemoria());
        ServicioSeguimientos servicioSeguimientos =
                new ServicioSeguimientos(new RepositorioSeguimientosMemoria());

        return new ServidorHTTP(
                puerto,
                servicioAutenticacion,
                servicioCitas,
                new AutorizadorCitasPorSesion(autenticadorSesion, asociaciones),
                servicioPrescripciones,
                new AutorizadorPrescripcionesPorSesion(autenticadorSesion, asociaciones),
                servicioNotificaciones,
                new AutorizadorNotificacionesPorSesion(autenticadorSesion, asociaciones),
                servicioHistorialPruebas,
                new AutorizadorHistorialPruebasPorSesion(autenticadorSesion, asociaciones),
                servicioIncidencias,
                new AutorizadorIncidenciasPorSesion(autenticadorSesion, asociaciones),
                servicioEncuestas,
                new AutorizadorEncuestasPorSesion(autenticadorSesion, asociaciones),
                servicioSeguimientos,
                new AutorizadorSeguimientosPorSesion(autenticadorSesion, asociaciones));
    }
}
