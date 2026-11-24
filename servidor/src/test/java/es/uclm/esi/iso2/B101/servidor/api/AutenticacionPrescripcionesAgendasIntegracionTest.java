package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.Rol;
import es.uclm.esi.iso2.B101.servidor.integracion.ProveedorSSOSimulado;
import es.uclm.esi.iso2.B101.servidor.persistencia.AsociacionIdentidadesClinicas;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioAuditoriaAccesoMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioEncuestasSatisfaccionMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHistorialPruebasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioIncidenciasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioNecesidadesCitaSeguimientoMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioNotificacionesMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioPrescripcionesDiagnosticasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioPrescripcionesSeguimientoMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioRespuestasEncuestasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSeguimientosMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSesionesMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSolicitudesCitaMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioUsuariosMemoria;
import es.uclm.esi.iso2.B101.servidor.seguridad.AutenticadorSesion;
import es.uclm.esi.iso2.B101.servidor.seguridad.GeneradorTokens;
import es.uclm.esi.iso2.B101.servidor.seguridad.GestorContrasenas;
import es.uclm.esi.iso2.B101.servidor.servicio.PoliticaPrescripcionDiagnosticaPorVinculo;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAgendas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAutenticacion;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioCitas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioEncuestasSatisfaccion;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioHistorialPruebas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioIncidencias;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioNotificaciones;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioPrescripcionesDiagnosticas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioPrescripcionesSeguimiento;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutenticacionPrescripcionesAgendasIntegracionTest {
    private static final String CU09 = "/api/prescripciones-seguimiento";
    private static final String CU10 =
            "/api/agendas/disponibilidad?tipoPrueba=TAC&desde=2026-12-01T09%3A00"
                    + "&hasta=2026-12-01T10%3A00&duracionMinutos=30";
    private static final String LOGIN = "/api/auth/login";
    private static final String LOGOUT = "/api/auth/logout";
    private static final Instant AHORA = Instant.parse("2026-11-23T12:00:00Z");

    private final HttpClient clienteHttp = HttpClient.newHttpClient();
    private MutableClock relojAutenticacion;
    private ServidorHTTP servidor;
    private String baseUrl;

    @BeforeEach
    void iniciarServidorCompartido() throws IOException {
        GestorContrasenas gestorContrasenas = new GestorContrasenas();
        RepositorioUsuariosMemoria repositorioUsuarios =
                RepositorioUsuariosMemoria.conUsuariosPorDefecto(gestorContrasenas);
        repositorioUsuarios.registrarUsuario(
                "USR-005", "paciente2", "Paciente dos", "paciente123", Rol.PACIENTE, true, gestorContrasenas);
        repositorioUsuarios.registrarUsuario(
                "USR-006", "medico2", "Médico dos", "medico123", Rol.MEDICO, true, gestorContrasenas);

        relojAutenticacion = new MutableClock(AHORA, ZoneOffset.UTC);
        ServicioAutenticacion servicioAutenticacion = new ServicioAutenticacion(
                repositorioUsuarios,
                new RepositorioSesionesMemoria(),
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
                Map.of("USR-002", "medico-1", "USR-006", "medico-2"));
        AutenticadorSesion autenticadorSesion = new AutenticadorSesion(servicioAutenticacion);

        RepositorioCitasMemoria repositorioCitas = new RepositorioCitasMemoria();
        repositorioCitas.guardar(new Cita(
                "cita-medico-1",
                "paciente-1",
                "medico-1",
                "TAC",
                LocalDateTime.of(2026, 12, 1, 9, 30)));
        repositorioCitas.guardar(new Cita(
                "cita-medico-2",
                "paciente-2",
                "medico-2",
                "TAC",
                LocalDateTime.of(2026, 12, 1, 9, 0)));

        ServicioCitas servicioCitas =
                new ServicioCitas(repositorioCitas, new RepositorioSolicitudesCitaMemoria());
        ServicioPrescripcionesDiagnosticas servicioPrescripciones = new ServicioPrescripcionesDiagnosticas(
                new RepositorioPrescripcionesDiagnosticasMemoria(),
                Clock.fixed(AHORA, ZoneOffset.UTC),
                new PoliticaPrescripcionDiagnosticaPorVinculo(Map.of(
                        "medico-1", Set.of("paciente-1"),
                        "medico-2", Set.of("paciente-2"))));
        AutorizadorPrescripcionesPorSesion autorizadorPrescripciones =
                new AutorizadorPrescripcionesPorSesion(autenticadorSesion, asociaciones);

        ServicioNotificaciones servicioNotificaciones =
                new ServicioNotificaciones(new RepositorioNotificacionesMemoria());
        ServicioHistorialPruebas servicioHistorial =
                new ServicioHistorialPruebas(new RepositorioHistorialPruebasMemoria());
        ServicioIncidencias servicioIncidencias =
                new ServicioIncidencias(new RepositorioIncidenciasMemoria());
        ServicioEncuestasSatisfaccion servicioEncuestas = new ServicioEncuestasSatisfaccion(
                new RepositorioEncuestasSatisfaccionMemoria(),
                new RepositorioRespuestasEncuestasMemoria());
        ServicioSeguimientos servicioSeguimientos =
                new ServicioSeguimientos(new RepositorioSeguimientosMemoria());

        Map<String, Set<String>> pacientesPorMedico = Map.of(
                "medico-1", Set.of("paciente-1"),
                "medico-2", Set.of("paciente-2"));
        ServicioPrescripcionesSeguimiento servicioPrescripcionesSeguimiento =
                new ServicioPrescripcionesSeguimiento(
                        new RepositorioPrescripcionesSeguimientoMemoria(),
                        new RepositorioNecesidadesCitaSeguimientoMemoria(),
                        Clock.fixed(AHORA, ZoneOffset.UTC),
                        (medicoId, pacienteId) ->
                                pacientesPorMedico.getOrDefault(medicoId, Set.of()).contains(pacienteId));
        ServicioAgendas servicioAgendas = new ServicioAgendas(repositorioCitas);

        servidor = new ServidorHTTP(
                0,
                servicioAutenticacion,
                servicioCitas,
                new AutorizadorCitasPorSesion(autenticadorSesion, asociaciones),
                servicioPrescripciones,
                autorizadorPrescripciones,
                servicioNotificaciones,
                new AutorizadorNotificacionesPorSesion(autenticadorSesion, asociaciones),
                servicioHistorial,
                new AutorizadorHistorialPruebasPorSesion(autenticadorSesion, asociaciones),
                servicioIncidencias,
                new AutorizadorIncidenciasPorSesion(autenticadorSesion, asociaciones),
                servicioEncuestas,
                new AutorizadorEncuestasPorSesion(autenticadorSesion, asociaciones),
                servicioSeguimientos,
                new AutorizadorSeguimientosPorSesion(autenticadorSesion, asociaciones),
                servicioPrescripcionesSeguimiento,
                new AutorizadorPrescripcionesSeguimientoPorSesion(autenticadorSesion, asociaciones),
                servicioAgendas);
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
    void medicoAutenticadoPrescribeSoloParaPacientesAutorizadosEnCu09() throws Exception {
        String medicoUno = iniciarSesion("medico1", "medico123");
        HttpResponse<String> creada =
                enviar("POST", CU09, medicoUno, prescripcion("paciente-1"), true);

        assertEquals(201, creada.statusCode());
        assertTrue(creada.body().contains("\"medicoId\":\"medico-1\""));
        assertTrue(creada.body().contains("\"pacienteId\":\"paciente-1\""));

        String prescripcionId = JsonUtil.parsearObjetoPlano(creada.body()).get("id");
        assertTrue(enviar("GET", CU09, medicoUno, null, false).body().contains(prescripcionId));
        assertEquals(200, enviar("GET", CU09 + "/" + prescripcionId + "/necesidades-cita", medicoUno, null, false)
                .statusCode());
        assertEquals(403, enviar("POST", CU09, medicoUno, prescripcion("paciente-2"), true).statusCode());

        String medicoDos = iniciarSesion("medico2", "medico123");
        assertEquals(404, enviar("GET", CU09 + "/" + prescripcionId, medicoDos, null, false).statusCode());
        assertEquals(400, enviar(
                        "POST",
                        CU09,
                        medicoUno,
                        prescripcion("paciente-1").replace(
                                "\"prioridad\":\"ORDINARIA\"",
                                "\"prioridad\":\"ORDINARIA\",\"medicoId\":\"medico-2\""),
                        true)
                .statusCode());
    }

    @Test
    void medicoAutenticadoConsultaSoloLaDisponibilidadDeSuAgendaEnCu10() throws Exception {
        String medicoUno = iniciarSesion("medico1", "medico123");
        String medicoDos = iniciarSesion("medico2", "medico123");

        HttpResponse<String> agendaUno = enviar("GET", CU10, medicoUno, null, false);
        HttpResponse<String> agendaDos = enviar("GET", CU10, medicoDos, null, false);
        assertEquals(200, agendaUno.statusCode());
        assertTrue(agendaUno.body().contains("\"desde\":\"2026-12-01T09:00\""));
        assertFalse(agendaUno.body().contains("\"desde\":\"2026-12-01T09:30\""));
        assertEquals(200, agendaDos.statusCode());
        assertFalse(agendaDos.body().contains("\"desde\":\"2026-12-01T09:00\""));
        assertTrue(agendaDos.body().contains("\"desde\":\"2026-12-01T09:30\""));

        assertEquals(400, enviar("GET", CU10 + "&medicoId=medico-2", medicoUno, null, false).statusCode());
    }

    @Test
    void rechazaTokensInexistentesExpiradosYCerradosEnCu09YCu10() throws Exception {
        String expirado = iniciarSesion("medico1", "medico123");
        relojAutenticacion.avanzar(Duration.ofHours(9));

        for (String ruta : rutasProtegidas()) {
            assertEquals(401, enviar("GET", ruta, "token-inexistente", null, false).statusCode());
            assertEquals(401, enviar("GET", ruta, expirado, null, false).statusCode());
        }

        String cerrado = iniciarSesion("medico1", "medico123");
        assertEquals(200, enviar("POST", LOGOUT, cerrado, null, false).statusCode());
        for (String ruta : rutasProtegidas()) {
            assertEquals(401, enviar("GET", ruta, cerrado, null, false).statusCode());
        }
    }

    @Test
    void rechazaCon403AUsuariosSinRolMedicoEnCu09YCu10() throws Exception {
        String paciente = iniciarSesion("paciente1", "paciente123");

        for (String ruta : rutasProtegidas()) {
            assertEquals(403, enviar("GET", ruta, paciente, null, false).statusCode());
        }
    }

    @Test
    void loginYCasosDeUsoAnterioresSiguenAtendidosPorElServidorCompartido() throws Exception {
        String paciente = iniciarSesion("paciente1", "paciente123");
        String medico = iniciarSesion("medico1", "medico123");

        assertEquals(200, enviar("GET", "/api/auth/session", paciente, null, false).statusCode());
        for (String ruta : new String[] {
            "/api/citas?pacienteId=paciente-1",
            "/api/notificaciones",
            "/api/historial-pruebas",
            "/api/incidencias",
            "/api/encuestas",
            "/api/seguimientos"
        }) {
            assertEquals(200, enviar("GET", ruta, paciente, null, false).statusCode(), ruta);
        }
        assertEquals(200, enviar("GET", "/api/prescripciones-pruebas", medico, null, false).statusCode());
    }

    private String[] rutasProtegidas() {
        return new String[] {CU09, CU10};
    }

    private String prescripcion(String pacienteId) {
        return "{\"pacienteId\":\"" + pacienteId
                + "\",\"tipoPrueba\":\"TAC\",\"motivoClinico\":\"Seguimiento\","
                + "\"periodicidadMeses\":12,\"fechaPrimeraRevision\":\"2026-12-01\","
                + "\"prioridad\":\"ORDINARIA\"}";
    }

    private String iniciarSesion(String username, String password) throws Exception {
        String cuerpo = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        HttpResponse<String> respuesta = enviar("POST", LOGIN, null, cuerpo, true);
        assertEquals(200, respuesta.statusCode());
        return JsonUtil.parsearObjetoPlano(respuesta.body()).get("token");
    }

    private HttpResponse<String> enviar(
            String metodo, String ruta, String token, String cuerpo, boolean json) throws Exception {
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(URI.create(baseUrl + ruta))
                .header("Accept", "application/json");
        if (token != null) {
            solicitud.header("Authorization", "Bearer " + token);
        }
        if (json && cuerpo != null) {
            solicitud.header("Content-Type", "application/json; charset=utf-8");
        }
        HttpRequest.BodyPublisher contenido = cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8);
        return clienteHttp.send(
                solicitud.method(metodo, contenido).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instante;
        private final ZoneId zona;

        private MutableClock(Instant instante, ZoneId zona) {
            this(new AtomicReference<>(instante), zona);
        }

        private MutableClock(AtomicReference<Instant> instante, ZoneId zona) {
            this.instante = instante;
            this.zona = zona;
        }

        @Override
        public ZoneId getZone() {
            return zona;
        }

        @Override
        public Clock withZone(ZoneId nuevaZona) {
            return new MutableClock(instante, nuevaZona);
        }

        @Override
        public Instant instant() {
            return instante.get();
        }

        private void avanzar(Duration duracion) {
            instante.updateAndGet(actual -> actual.plus(duracion));
        }
    }
}
