package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.Notificacion;
import es.uclm.esi.iso2.B101.servidor.dominio.Rol;
import es.uclm.esi.iso2.B101.servidor.dominio.EncuestaSatisfaccion;
import es.uclm.esi.iso2.B101.servidor.dominio.SeguimientoPaciente;
import es.uclm.esi.iso2.B101.servidor.integracion.ProveedorSSOSimulado;
import es.uclm.esi.iso2.B101.servidor.persistencia.AsociacionIdentidadesClinicas;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioAuditoriaAccesoMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioEncuestasSatisfaccionMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHistorialPruebasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioIncidenciasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioNotificacionesMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioPrescripcionesDiagnosticasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioRespuestasEncuestasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSesionesMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSeguimientosMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSolicitudesCitaMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioUsuariosMemoria;
import es.uclm.esi.iso2.B101.servidor.seguridad.AutenticadorSesion;
import es.uclm.esi.iso2.B101.servidor.seguridad.GeneradorTokens;
import es.uclm.esi.iso2.B101.servidor.seguridad.GestorContrasenas;
import es.uclm.esi.iso2.B101.servidor.servicio.PoliticaPrescripcionDiagnosticaPorVinculo;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAutenticacion;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioCitas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioEncuestasSatisfaccion;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioHistorialPruebas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioIncidencias;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioNotificaciones;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioPrescripcionesDiagnosticas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioSeguimientos;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutenticacionCasosUsoPacienteIntegracionTest {
    private static final String NOTIFICACIONES = "/api/notificaciones";
    private static final String HISTORIAL = "/api/historial-pruebas";
    private static final String INCIDENCIAS = "/api/incidencias";
    private static final String ENCUESTAS = "/api/encuestas";
    private static final String SEGUIMIENTOS = "/api/seguimientos";
    private static final String LOGIN = "/api/auth/login";
    private static final Instant AHORA = Instant.parse("2026-11-23T12:00:00Z");

    private final HttpClient clienteHttp = HttpClient.newHttpClient();
    private RepositorioSesionesMemoria repositorioSesiones;
    private RelojMutable relojAutenticacion;
    private ServicioNotificaciones servicioNotificaciones;
    private ServicioHistorialPruebas servicioHistorial;
    private ServicioIncidencias servicioIncidencias;
    private ServicioEncuestasSatisfaccion servicioEncuestas;
    private ServicioSeguimientos servicioSeguimientos;
    private ServidorHTTP servidor;
    private String baseUrl;
    private Notificacion notificacionPacienteDos;
    private String incidenciaPacienteDos;
    private String seguimientoPacienteDos;

    @BeforeEach
    void iniciarServidorCompartido() throws IOException {
        GestorContrasenas gestorContrasenas = new GestorContrasenas();
        RepositorioUsuariosMemoria repositorioUsuarios =
                RepositorioUsuariosMemoria.conUsuariosPorDefecto(gestorContrasenas);
        repositorioUsuarios.registrarUsuario(
                "USR-005", "paciente2", "Paciente dos", "paciente123", Rol.PACIENTE, true, gestorContrasenas);
        repositorioSesiones = new RepositorioSesionesMemoria();
        relojAutenticacion = new RelojMutable(AHORA, ZoneOffset.UTC);
        ServicioAutenticacion servicioAutenticacion = new ServicioAutenticacion(
                repositorioUsuarios,
                repositorioSesiones,
                new RepositorioAuditoriaAccesoMemoria(),
                gestorContrasenas,
                new GeneradorTokens(),
                new ProveedorSSOSimulado(),
                relojAutenticacion,
                ServicioAutenticacion.MAX_INTENTOS_FALLIDOS_POR_DEFECTO,
                ServicioAutenticacion.DURACION_BLOQUEO_POR_DEFECTO,
                ServicioAutenticacion.DURACION_SESION_POR_DEFECTO);

        AsociacionIdentidadesClinicas asociaciones = new AsociacionIdentidadesClinicas(
                Map.of("USR-001", "paciente-1", "USR-005", "paciente-2"),
                Map.of("USR-002", "medico-1"));
        AutenticadorSesion autenticadorSesion = new AutenticadorSesion(servicioAutenticacion);

        RepositorioNotificacionesMemoria repositorioNotificaciones = new RepositorioNotificacionesMemoria();
        servicioNotificaciones = new ServicioNotificaciones(
                repositorioNotificaciones,
                Clock.fixed(AHORA, ZoneOffset.UTC),
                Duration.ofHours(24));
        servicioNotificaciones.registrarInformeDisponible("paciente-1", "informe-1");
        notificacionPacienteDos = servicioNotificaciones.registrarInformeDisponible("paciente-2", "informe-2");

        RepositorioHistorialPruebasMemoria repositorioHistorial = new RepositorioHistorialPruebasMemoria();
        servicioHistorial = new ServicioHistorialPruebas(repositorioHistorial);
        servicioHistorial.registrarPruebaRealizada(
                "prueba-paciente-1", "paciente-1", "TAC", LocalDateTime.of(2026, 11, 20, 10, 0), "solicitud-1");
        servicioHistorial.registrarPruebaRealizada(
                "prueba-paciente-2", "paciente-2", "RM", LocalDateTime.of(2026, 11, 21, 10, 0), "solicitud-2");

        servicioIncidencias = new ServicioIncidencias(
                new RepositorioIncidenciasMemoria(), Clock.fixed(AHORA, ZoneOffset.UTC));
        servicioIncidencias.comunicarIncidencia("paciente-1", "Propia", "Incidencia propia", null);
        incidenciaPacienteDos =
                servicioIncidencias.comunicarIncidencia("paciente-2", "Ajena", "Incidencia ajena", null).getId();

        RepositorioEncuestasSatisfaccionMemoria repositorioEncuestas =
                new RepositorioEncuestasSatisfaccionMemoria();
        repositorioEncuestas.guardar(new EncuestaSatisfaccion("encuesta-1", "Satisfacción", "Valore la atención", true));
        servicioEncuestas = new ServicioEncuestasSatisfaccion(
                repositorioEncuestas,
                new RepositorioRespuestasEncuestasMemoria(),
                Clock.fixed(AHORA, ZoneOffset.UTC));

        servicioSeguimientos = new ServicioSeguimientos(new RepositorioSeguimientosMemoria());
        servicioSeguimientos.registrarSeguimiento(new SeguimientoPaciente(
                "seguimiento-paciente-1",
                "paciente-1",
                "TAC",
                "Revisión anual",
                12,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 10, 1),
                "Acudir en ayunas"));
        seguimientoPacienteDos = "seguimiento-paciente-2";
        servicioSeguimientos.registrarSeguimiento(new SeguimientoPaciente(
                seguimientoPacienteDos,
                "paciente-2",
                "RM",
                "Control",
                6,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 3, 1),
                null));

        ServicioCitas servicioCitas =
                new ServicioCitas(new RepositorioCitasMemoria(), new RepositorioSolicitudesCitaMemoria());
        ServicioPrescripcionesDiagnosticas servicioPrescripciones = new ServicioPrescripcionesDiagnosticas(
                new RepositorioPrescripcionesDiagnosticasMemoria(),
                Clock.fixed(AHORA, ZoneOffset.UTC),
                new PoliticaPrescripcionDiagnosticaPorVinculo(Map.of("medico-1", Set.of("paciente-1"))));

        servidor = new ServidorHTTP(
                0,
                servicioAutenticacion,
                servicioCitas,
                new AutorizadorCitasPorSesion(autenticadorSesion, asociaciones),
                servicioPrescripciones,
                new AutorizadorPrescripcionesPorSesion(autenticadorSesion, asociaciones),
                servicioNotificaciones,
                new AutorizadorNotificacionesPorSesion(autenticadorSesion, asociaciones),
                servicioHistorial,
                new AutorizadorHistorialPruebasPorSesion(autenticadorSesion, asociaciones),
                servicioIncidencias,
                new AutorizadorIncidenciasPorSesion(autenticadorSesion, asociaciones),
                servicioEncuestas,
                new AutorizadorEncuestasPorSesion(autenticadorSesion, asociaciones),
                servicioSeguimientos,
                new AutorizadorSeguimientosPorSesion(autenticadorSesion, asociaciones));
        servidor.iniciar();
        baseUrl = "http://127.0.0.1:" + servidor.getPuerto();
    }

    @AfterEach
    void detenerServidor() {
        if (servidor != null) {
            servidor.detener();
        }
    }

    @Test
    void loginPacientePermiteAccederSoloASusCincoCasosDeUso() throws Exception {
        String token = iniciarSesion("paciente1", "paciente123");

        HttpResponse<String> notificaciones = enviar("GET", NOTIFICACIONES, token, null);
        HttpResponse<String> historial = enviar("GET", HISTORIAL, token, null);
        HttpResponse<String> incidencias = enviar("GET", INCIDENCIAS, token, null);
        HttpResponse<String> encuestas = enviar("GET", ENCUESTAS, token, null);
        HttpResponse<String> seguimientos = enviar("GET", SEGUIMIENTOS, token, null);

        assertEquals(200, notificaciones.statusCode());
        assertTrue(notificaciones.body().contains("\"referenciaId\":\"informe-1\""));
        assertFalse(notificaciones.body().contains("\"referenciaId\":\"informe-2\""));
        assertEquals(200, historial.statusCode());
        assertTrue(historial.body().contains("\"id\":\"prueba-paciente-1\""));
        assertFalse(historial.body().contains("\"id\":\"prueba-paciente-2\""));
        assertEquals(200, incidencias.statusCode());
        assertTrue(incidencias.body().contains("\"asunto\":\"Propia\""));
        assertFalse(incidencias.body().contains("\"asunto\":\"Ajena\""));
        assertEquals(200, encuestas.statusCode());
        assertTrue(encuestas.body().contains("\"id\":\"encuesta-1\""));
        assertEquals(200, seguimientos.statusCode());
        assertTrue(seguimientos.body().contains("\"id\":\"seguimiento-paciente-1\""));
        assertFalse(seguimientos.body().contains("\"id\":\"seguimiento-paciente-2\""));
    }

    @Test
    void respondeEncuestaYConsultaSusRespuestasConLaIdentidadDeLaSesion() throws Exception {
        String pacienteUno = iniciarSesion("paciente1", "paciente123");
        String pacienteDos = iniciarSesion("paciente2", "paciente123");
        String respuestaPacienteUno =
                "{\"puntuacion\":5,\"comentario\":\"Atención excelente\"}";
        String respuestaPacienteDos = "{\"puntuacion\":2}";

        assertEquals(
                201,
                enviar("POST", ENCUESTAS + "/encuesta-1/respuestas", pacienteUno, respuestaPacienteUno).statusCode());
        assertEquals(
                201,
                enviar("POST", ENCUESTAS + "/encuesta-1/respuestas", pacienteDos, respuestaPacienteDos).statusCode());

        HttpResponse<String> respuestasUno = enviar("GET", ENCUESTAS + "/mis-respuestas", pacienteUno, null);
        HttpResponse<String> respuestasDos = enviar("GET", ENCUESTAS + "/mis-respuestas", pacienteDos, null);
        assertTrue(respuestasUno.body().contains("\"puntuacion\":5"));
        assertFalse(respuestasUno.body().contains("\"puntuacion\":2"));
        assertTrue(respuestasDos.body().contains("\"puntuacion\":2"));
    }

    @Test
    void incidenciaCreadaUsaLaIdentidadClinicaDeLaSesion() throws Exception {
        String token = iniciarSesion("paciente1", "paciente123");
        String cuerpo = "{\"asunto\":\"Consulta\",\"descripcion\":\"No se muestra pacienteId del cliente\"}";

        HttpResponse<String> respuesta = enviar("POST", INCIDENCIAS, token, cuerpo);

        assertEquals(201, respuesta.statusCode());
        assertTrue(respuesta.body().contains("\"pacienteId\":\"paciente-1\""));
        assertEquals(2, servicioIncidencias.consultarIncidencias("paciente-1").size());
        assertTrue(servicioIncidencias.consultarIncidencias("paciente-2").stream()
                .noneMatch(incidencia -> "Consulta".equals(incidencia.getAsunto())));
    }

    @Test
    void rechazaTokensInexistentesExpiradosYCerradosEnLosCincoCasosDeUso() throws Exception {
        String tokenExpirado = iniciarSesion("paciente1", "paciente123");
        relojAutenticacion.avanzar(Duration.ofHours(9));

        for (String ruta : rutasPaciente()) {
            assertEquals(401, enviar("GET", ruta, "token-inexistente", null).statusCode());
            assertEquals(401, enviar("GET", ruta, tokenExpirado, null).statusCode());
        }

        String token = iniciarSesion("paciente1", "paciente123");
        assertEquals(200, enviar("POST", "/api/auth/logout", token, null).statusCode());
        for (String ruta : rutasPaciente()) {
            assertEquals(401, enviar("GET", ruta, token, null).statusCode());
        }
    }

    @Test
    void rechazaCon403UnUsuarioAutenticadoSinRolPaciente() throws Exception {
        String token = iniciarSesion("medico1", "medico123");

        for (String ruta : rutasPaciente()) {
            assertEquals(403, enviar("GET", ruta, token, null).statusCode());
        }
    }

    @Test
    void impideConsultarElementosDeOtroPaciente() throws Exception {
        String token = iniciarSesion("paciente1", "paciente123");

        HttpResponse<String> notificacionAjena = enviar(
                "POST", NOTIFICACIONES + "/" + notificacionPacienteDos.getId() + "/leida", token, null);
        HttpResponse<String> incidenciaAjena = enviar("GET", INCIDENCIAS + "/" + incidenciaPacienteDos, token, null);
        HttpResponse<String> seguimientoAjeno =
                enviar("GET", SEGUIMIENTOS + "/" + seguimientoPacienteDos, token, null);
        HttpResponse<String> historialSeleccionado = enviar(
                "GET", HISTORIAL + "?pacienteId=paciente-2", token, null);
        HttpResponse<String> seguimientoSeleccionado =
                enviar("GET", SEGUIMIENTOS + "?pacienteId=paciente-2", token, null);

        assertEquals(404, notificacionAjena.statusCode());
        assertFalse(notificacionPacienteDos.estaLeida());
        assertEquals(404, incidenciaAjena.statusCode());
        assertEquals(404, seguimientoAjeno.statusCode());
        assertEquals(400, historialSeleccionado.statusCode());
        assertEquals(400, seguimientoSeleccionado.statusCode());
        assertFalse(historialSeleccionado.body().contains("paciente-2"));
    }

    private String[] rutasPaciente() {
        return new String[] {NOTIFICACIONES, HISTORIAL, INCIDENCIAS, ENCUESTAS, SEGUIMIENTOS};
    }

    private String iniciarSesion(String username, String password) throws Exception {
        String cuerpo = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        HttpResponse<String> respuesta = enviar("POST", LOGIN, null, cuerpo);
        assertEquals(200, respuesta.statusCode());
        return JsonUtil.parsearObjetoPlano(respuesta.body()).get("token");
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String token, String cuerpo) throws Exception {
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(URI.create(baseUrl + ruta))
                .header("Accept", "application/json");
        if (token != null) {
            solicitud.header("Authorization", "Bearer " + token);
        }
        if (cuerpo != null) {
            solicitud.header("Content-Type", "application/json; charset=utf-8");
        }
        HttpRequest.BodyPublisher contenido = cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8);
        return clienteHttp.send(
                solicitud.method(metodo, contenido).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static final class RelojMutable extends Clock {
        private Instant instante;
        private final ZoneId zona;

        private RelojMutable(Instant instante, ZoneId zona) {
            this.instante = instante;
            this.zona = zona;
        }

        @Override
        public ZoneId getZone() {
            return zona;
        }

        @Override
        public Clock withZone(ZoneId zonaNueva) {
            return new RelojMutable(instante, zonaNueva);
        }

        @Override
        public Instant instant() {
            return instante;
        }

        private void avanzar(Duration duracion) {
            instante = instante.plus(duracion);
        }
    }
}
