package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.PruebaRealizada;
import es.uclm.esi.iso2.B101.servidor.dominio.Rol;
import es.uclm.esi.iso2.B101.servidor.integracion.ProveedorSSOSimulado;
import es.uclm.esi.iso2.B101.servidor.persistencia.AsociacionIdentidadesClinicas;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioAuditoriaAccesoMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioEncuestasSatisfaccionMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHistorialPruebasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioIncidenciasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioInformesPruebaMemoria;
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
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioInformesPrueba;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioNotificaciones;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioPrescripcionesDiagnosticas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioPrescripcionesSeguimiento;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioRealizacionPruebas;
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
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutenticacionInformesRealizacionIntegracionTest {
    private static final String INFORMES = "/api/informes-prueba";
    private static final String REALIZACIONES = "/api/realizaciones-prueba";
    private static final String LOGIN = "/api/auth/login";
    private static final String LOGOUT = "/api/auth/logout";
    private static final String AGENDA =
            "/api/agendas/disponibilidad?tipoPrueba=TAC&desde=2026-12-01T09%3A00"
                    + "&hasta=2026-12-01T10%3A00&duracionMinutos=30";
    private static final Instant AHORA = Instant.parse("2026-11-23T12:00:00Z");
    private static final String RESULTADO_REALIZADA =
            "{\"citaId\":\"cita-1\",\"resultado\":\"REALIZADA\"}";

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
                "USR-006", "medico2", "Medico dos", "medico123", Rol.MEDICO, true, gestorContrasenas);

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
                "cita-1",
                "paciente-1",
                "medico-1",
                "TAC",
                LocalDateTime.of(2026, 12, 1, 9, 0)));
        repositorioCitas.guardar(new Cita(
                "cita-2",
                "paciente-2",
                "medico-2",
                "TAC",
                LocalDateTime.of(2026, 12, 1, 9, 0)));

        RepositorioHistorialPruebasMemoria repositorioHistorial = new RepositorioHistorialPruebasMemoria();
        repositorioHistorial.registrar(new PruebaRealizada(
                "prueba-1",
                "paciente-1",
                "TAC",
                LocalDateTime.of(2026, 11, 20, 10, 0),
                null));
        repositorioHistorial.registrar(new PruebaRealizada(
                "prueba-2",
                "paciente-2",
                "RM",
                LocalDateTime.of(2026, 11, 21, 10, 0),
                null));
        RepositorioIncidenciasMemoria repositorioIncidencias = new RepositorioIncidenciasMemoria();

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
        ServicioHistorialPruebas servicioHistorial = new ServicioHistorialPruebas(repositorioHistorial);
        ServicioIncidencias servicioIncidencias = new ServicioIncidencias(repositorioIncidencias);
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
        ServicioInformesPrueba servicioInformes = new ServicioInformesPrueba(
                repositorioHistorial,
                new RepositorioInformesPruebaMemoria(),
                Clock.fixed(AHORA, ZoneOffset.UTC));
        ServicioRealizacionPruebas servicioRealizacion = new ServicioRealizacionPruebas(
                repositorioCitas,
                repositorioHistorial,
                repositorioIncidencias,
                Clock.fixed(AHORA, ZoneOffset.UTC));

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
                servicioAgendas,
                servicioInformes,
                new AutorizadorInformesPruebaPorSesion(autenticadorSesion, asociaciones),
                servicioRealizacion);
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
    void medicoCreaInformeYSoloPacientePropietarioOMedicoAutorPuedeConsultarOModificarlo() throws Exception {
        String medicoUno = iniciarSesion("medico1", "medico123");
        String medicoDos = iniciarSesion("medico2", "medico123");
        String pacienteUno = iniciarSesion("paciente1", "paciente123");
        String pacienteDos = iniciarSesion("paciente2", "paciente123");

        HttpResponse<String> creado = enviar(
                "POST",
                INFORMES,
                medicoUno,
                "{\"pruebaId\":\"prueba-1\",\"hallazgos\":\"Hallazgos\",\"conclusion\":\"Conclusion\"}");

        assertEquals(201, creado.statusCode());
        assertTrue(creado.body().contains("\"medicoId\":\"medico-1\""));
        assertTrue(creado.body().contains("\"pacienteId\":\"paciente-1\""));
        String informeId = JsonUtil.parsearObjetoPlano(creado.body()).get("id");
        assertEquals(200, enviar("GET", INFORMES + "/" + informeId, pacienteUno, null).statusCode());
        assertEquals(200, enviar("GET", INFORMES + "/" + informeId, medicoUno, null).statusCode());
        assertEquals(404, enviar("GET", INFORMES + "/" + informeId, pacienteDos, null).statusCode());
        assertEquals(404, enviar("GET", INFORMES + "/" + informeId, medicoDos, null).statusCode());
        assertTrue(enviar("GET", INFORMES, pacienteUno, null).body().contains(informeId));
        assertTrue(enviar("GET", INFORMES + "/medico", medicoUno, null).body().contains(informeId));

        String png = Base64.getEncoder().encodeToString(
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a});
        String imagenJson = "{\"tipoImagen\":\"image/png\",\"imagenBase64\":\"" + png + "\"}";
        assertEquals(404, enviar("POST", INFORMES + "/" + informeId + "/imagen", medicoDos, imagenJson)
                .statusCode());
        assertEquals(200, enviar("POST", INFORMES + "/" + informeId + "/imagen", medicoUno, imagenJson)
                .statusCode());
        assertEquals(200, enviar("GET", INFORMES + "/" + informeId + "/imagen", pacienteUno, null).statusCode());
        assertEquals(404, enviar("GET", INFORMES + "/" + informeId + "/imagen", pacienteDos, null).statusCode());
        assertEquals(403, enviar(
                        "POST",
                        INFORMES,
                        pacienteUno,
                        "{\"pruebaId\":\"prueba-1\",\"hallazgos\":\"H\",\"conclusion\":\"C\"}")
                .statusCode());
        assertEquals(400, enviar(
                        "POST",
                        INFORMES,
                        medicoUno,
                        "{\"pruebaId\":\"prueba-1\",\"hallazgos\":\"H\",\"conclusion\":\"C\","
                                + "\"medicoId\":\"medico-2\"}")
                .statusCode());
    }

    @Test
    void medicoRealizaSoloLasCitasQueTieneAsignadas() throws Exception {
        String medicoUno = iniciarSesion("medico1", "medico123");

        assertEquals(404, enviar(
                        "POST",
                        REALIZACIONES,
                        medicoUno,
                        "{\"citaId\":\"cita-2\",\"resultado\":\"REALIZADA\"}")
                .statusCode());
        assertEquals(201, enviar("POST", REALIZACIONES, medicoUno, RESULTADO_REALIZADA).statusCode());
        assertEquals(400, enviar(
                        "POST",
                        REALIZACIONES,
                        medicoUno,
                        "{\"citaId\":\"cita-1\",\"resultado\":\"REALIZADA\",\"medicoId\":\"medico-2\"}")
                .statusCode());
    }

    @Test
    void rechazaTokensInexistentesExpiradosYCerradosEnCu11YCu12() throws Exception {
        String expirado = iniciarSesion("medico1", "medico123");
        relojAutenticacion.avanzar(Duration.ofHours(9));

        for (String ruta : rutasProtegidas()) {
            assertEquals(401, enviar("GET", ruta, "token-inexistente", null).statusCode());
            assertEquals(401, enviar("GET", ruta, expirado, null).statusCode());
        }
        assertEquals(401, enviar("POST", REALIZACIONES, "token-inexistente", RESULTADO_REALIZADA).statusCode());
        assertEquals(401, enviar("POST", REALIZACIONES, expirado, RESULTADO_REALIZADA).statusCode());

        String cerrado = iniciarSesion("medico1", "medico123");
        assertEquals(200, enviar("POST", LOGOUT, cerrado, null).statusCode());
        for (String ruta : rutasProtegidas()) {
            assertEquals(401, enviar("GET", ruta, cerrado, null).statusCode());
        }
        assertEquals(401, enviar("POST", REALIZACIONES, cerrado, RESULTADO_REALIZADA).statusCode());
    }

    @Test
    void rechazaCon403OperacionesQueExigenRolMedico() throws Exception {
        String paciente = iniciarSesion("paciente1", "paciente123");

        assertEquals(403, enviar("POST", INFORMES, paciente,
                "{\"pruebaId\":\"prueba-1\",\"hallazgos\":\"H\",\"conclusion\":\"C\"}").statusCode());
        assertEquals(403, enviar("GET", INFORMES + "/medico", paciente, null).statusCode());
        assertEquals(403, enviar("POST", REALIZACIONES, paciente, RESULTADO_REALIZADA).statusCode());
    }

    @Test
    void mantieneAccesiblesLasIntegracionesPreviasEnElMismoServidor() throws Exception {
        String paciente = iniciarSesion("paciente1", "paciente123");
        String medico = iniciarSesion("medico1", "medico123");

        assertEquals(200, enviar("GET", "/api/auth/session", paciente, null).statusCode());
        for (String ruta : new String[] {
            "/api/citas?pacienteId=paciente-1",
            "/api/notificaciones",
            "/api/historial-pruebas",
            "/api/incidencias",
            "/api/encuestas",
            "/api/seguimientos",
            "/api/informes-prueba"
        }) {
            assertEquals(200, enviar("GET", ruta, paciente, null).statusCode(), ruta);
        }
        assertEquals(200, enviar("GET", "/api/prescripciones-pruebas", medico, null).statusCode());
        assertEquals(200, enviar("GET", "/api/prescripciones-seguimiento", medico, null).statusCode());
        assertEquals(200, enviar("GET", AGENDA, medico, null).statusCode());
    }

    private String[] rutasProtegidas() {
        return new String[] {INFORMES};
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
                HttpResponse.BodyHandlers.ofString(StandardCharsets.ISO_8859_1));
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
