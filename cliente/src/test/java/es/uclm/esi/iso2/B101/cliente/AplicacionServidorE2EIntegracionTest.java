package es.uclm.esi.iso2.B101.cliente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.cliente.comunicacion.ClienteAutenticacion;
import es.uclm.esi.iso2.B101.cliente.comunicacion.JsonClienteUtil;
import es.uclm.esi.iso2.B101.cliente.cu02.ClienteCitasHttp;
import es.uclm.esi.iso2.B101.cliente.cu03.ClienteNotificacionesHttp;
import es.uclm.esi.iso2.B101.cliente.cu04.ClienteHistorialPruebasHttp;
import es.uclm.esi.iso2.B101.cliente.cu05.ClienteIncidenciasHttp;
import es.uclm.esi.iso2.B101.cliente.cu06.ClienteEncuestasHttp;
import es.uclm.esi.iso2.B101.cliente.cu07.ClienteSeguimientosHttp;
import es.uclm.esi.iso2.B101.cliente.cu08.ClientePrescripcionesHttp;
import es.uclm.esi.iso2.B101.cliente.cu09.ClienteSeguimientoPeriodicoHttp;
import es.uclm.esi.iso2.B101.cliente.cu10.ClienteAgendasHttp;
import es.uclm.esi.iso2.B101.cliente.cu11.ClienteInformesPruebaHttp;
import es.uclm.esi.iso2.B101.cliente.cu12.ClienteRealizacionPruebasHttp;
import es.uclm.esi.iso2.B101.servidor.api.AplicacionServidor;
import es.uclm.esi.iso2.B101.servidor.api.ServidorHTTP;
import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAutenticacion;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AplicacionServidorE2EIntegracionTest {
    private static final Instant INICIO = Instant.parse("2026-11-24T10:00:00Z");
    private static final String CITA_REALIZADA = "e2e-cita-realizada";
    private static final String CITA_INCIDENCIA = "e2e-cita-incidencia";

    private final HttpClient clienteHttp = HttpClient.newHttpClient();
    private final RelojMutable reloj = new RelojMutable(INICIO, ZoneOffset.UTC);
    private ServidorHTTP servidor;
    private String baseUrl;
    private ClienteAutenticacion autenticacion;

    @BeforeEach
    void iniciarServidorCompletoConDatosControlados() throws IOException {
        RepositorioCitasMemoria citas = new RepositorioCitasMemoria();
        citas.guardar(new Cita(
                CITA_REALIZADA,
                "paciente-1",
                "medico-1",
                "TAC",
                LocalDateTime.of(2026, 11, 24, 9, 0)));
        citas.guardar(new Cita(
                CITA_INCIDENCIA,
                "paciente-1",
                "medico-1",
                "RM",
                LocalDateTime.of(2026, 11, 24, 11, 0)));
        servidor = AplicacionServidor.crearServidor(0, reloj, citas);
        servidor.iniciar();
        baseUrl = "http://127.0.0.1:" + servidor.getPuerto();
        autenticacion = new ClienteAutenticacion(baseUrl);
    }

    @AfterEach
    void detenerServidor() {
        if (servidor != null) {
            servidor.detener();
        }
    }

    @Test
    void pacienteAutenticadoAccedeConElMismoTokenACu02HastaCu07() throws Exception {
        String token = iniciarSesion("paciente1", "paciente123");

        assertEquals(200, new ClienteCitasHttp(baseUrl, token).consultarCitas().codigoHttp());
        assertEquals(200, new ClienteNotificacionesHttp(baseUrl, token).consultarNotificaciones().codigoHttp());
        assertEquals(200, new ClienteHistorialPruebasHttp(baseUrl, token).consultarHistorial().codigoHttp());

        ClienteIncidenciasHttp incidencias = new ClienteIncidenciasHttp(baseUrl, token);
        assertEquals(201, incidencias.comunicarIncidencia("Consulta E2E", "Incidencia de prueba", null).codigoHttp());
        assertTrue(incidencias.consultarIncidencias().cuerpoJson().contains("\"asunto\":\"Consulta E2E\""));

        ClienteEncuestasHttp encuestas = new ClienteEncuestasHttp(baseUrl, token);
        assertEquals(200, encuestas.consultarEncuestas().codigoHttp());
        assertEquals(200, encuestas.consultarMisRespuestas().codigoHttp());
        assertEquals(200, new ClienteSeguimientosHttp(baseUrl, token).consultarSeguimientos().codigoHttp());
    }

    @Test
    void medicoCompletaCu08ACu12YLosRepositoriosCompartidosPropaganLosResultados() throws Exception {
        String tokenMedico = iniciarSesion("medico1", "medico123");
        String tokenPaciente = iniciarSesion("paciente1", "paciente123");

        ClientePrescripcionesHttp prescripciones = new ClientePrescripcionesHttp(baseUrl, tokenMedico);
        assertEquals(201, prescripciones.prescribir(new ClientePrescripcionesHttp.DatosPrescripcion(
                "paciente-1", "TAC", "Control", "Seguimiento de prueba E2E", "ORDINARIA")).codigoHttp());

        ClienteSeguimientoPeriodicoHttp seguimiento = new ClienteSeguimientoPeriodicoHttp(baseUrl, tokenMedico);
        assertEquals(201, seguimiento.registrarPrescripcion(
                new ClienteSeguimientoPeriodicoHttp.DatosPrescripcion(
                        "paciente-1",
                        "TAC",
                        "Seguimiento E2E",
                        12,
                        LocalDate.of(2027, 11, 24),
                        "ORDINARIA")).codigoHttp());

        ClienteAgendasHttp agendas = new ClienteAgendasHttp(baseUrl, tokenMedico);
        assertEquals(200, agendas.consultarDisponibilidad(
                "TAC",
                LocalDateTime.of(2026, 11, 24, 12, 0),
                LocalDateTime.of(2026, 11, 24, 16, 0),
                30).codigoHttp());

        ClienteRealizacionPruebasHttp realizacion = new ClienteRealizacionPruebasHttp(baseUrl, tokenMedico);
        var pruebaRealizada = realizacion.registrarRealizacion(CITA_REALIZADA);
        assertEquals(201, pruebaRealizada.codigoHttp());
        Map<String, String> datosPrueba = JsonClienteUtil.parsearObjetoPlano(pruebaRealizada.cuerpoJson());
        String pruebaId = datosPrueba.get("id");
        assertTrue(pruebaId != null && !pruebaId.isBlank());

        ClienteHistorialPruebasHttp historial = new ClienteHistorialPruebasHttp(baseUrl, tokenPaciente);
        var historialPaciente = historial.consultarHistorial();
        assertEquals(200, historialPaciente.codigoHttp());
        assertTrue(historialPaciente.cuerpoJson().contains("\"citaId\":\"" + CITA_REALIZADA + "\""));
        assertTrue(historialPaciente.cuerpoJson().contains("\"id\":\"" + pruebaId + "\""));

        ClienteInformesPruebaHttp informesMedico = new ClienteInformesPruebaHttp(baseUrl, tokenMedico);
        assertEquals(403, new ClienteInformesPruebaHttp(baseUrl, tokenPaciente)
                .crearInforme(pruebaId, "No autorizado", "No autorizado").codigoHttp());
        var informeCreado = informesMedico.crearInforme(pruebaId, "Hallazgos E2E", "Conclusión E2E");
        assertEquals(201, informeCreado.codigoHttp());
        var informesPaciente =
                new ClienteInformesPruebaHttp(baseUrl, tokenPaciente).consultarInformesPaciente();
        assertEquals(200, informesPaciente.codigoHttp());
        assertTrue(informesPaciente.cuerpo().contains("\"pruebaId\":\"" + pruebaId + "\""));
        assertTrue(informesPaciente.cuerpo().contains("\"hallazgos\":\"Hallazgos E2E\""));

        var incidenciaRealizacion =
                realizacion.registrarIncidenciaRealizacion(CITA_INCIDENCIA, "Incidencia E2E en la prueba");
        assertEquals(201, incidenciaRealizacion.codigoHttp());
        var incidenciasPaciente = new ClienteIncidenciasHttp(baseUrl, tokenPaciente).consultarIncidencias();
        assertEquals(200, incidenciasPaciente.codigoHttp());
        assertTrue(incidenciasPaciente.cuerpoJson().contains("\"descripcion\":\"Incidencia E2E en la prueba\""));
        assertTrue(incidenciasPaciente.cuerpoJson().contains("\"citaId\":\"" + CITA_INCIDENCIA + "\""));

        assertEquals(403, new ClienteRealizacionPruebasHttp(baseUrl, tokenPaciente)
                .registrarRealizacion(CITA_INCIDENCIA).codigoHttp());
        assertEquals(403, new ClienteCitasHttp(baseUrl, tokenMedico).consultarCitas().codigoHttp());
    }

    @Test
    void todosLosEndpointsDeCasosDeUsoRequierenUnaSesionValidaYLogoutLaRevoca() throws Exception {
        HttpResponse<String> ssoNoRegistrado = clienteHttp.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/sso"))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"ssoToken\":\"sso-no-registrado\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(401, ssoNoRegistrado.statusCode());

        String tokenActivo = iniciarSesion("paciente1", "paciente123");
        assertTrue(autenticacion.validarSesion(tokenActivo).isPresent());
        assertSesionRechazadaEnEndpointsProtegidos("token-inexistente");

        String tokenCerrado = iniciarSesion("paciente1", "paciente123");
        assertTrue(autenticacion.cerrarSesion(tokenCerrado));
        assertSesionRechazadaEnEndpointsProtegidos(tokenCerrado);
        assertFalse(autenticacion.validarSesion(tokenCerrado).isPresent());

        String tokenCaducado = iniciarSesion("paciente1", "paciente123");
        reloj.avanzar(ServicioAutenticacion.DURACION_SESION_POR_DEFECTO.plusSeconds(1));
        assertSesionRechazadaEnEndpointsProtegidos(tokenCaducado);
        assertFalse(autenticacion.validarSesion(tokenCaducado).isPresent());
    }

    private String iniciarSesion(String usuario, String contrasena) {
        var respuesta = autenticacion.iniciarSesion(usuario, contrasena);
        assertTrue(respuesta.exito(), respuesta.mensaje());
        return respuesta.obtenerSesion().orElseThrow().token();
    }

    private void assertSesionRechazadaEnEndpointsProtegidos(String token) throws Exception {
        for (String ruta : RUTAS_PROTEGIDAS) {
            HttpRequest.Builder solicitud = HttpRequest.newBuilder(URI.create(baseUrl + ruta))
                    .header("Accept", "application/json");
            if (token != null) {
                solicitud.header("Authorization", "Bearer " + token);
            }
            HttpRequest.BodyPublisher cuerpo = ruta.equals("/api/realizaciones-prueba")
                    ? HttpRequest.BodyPublishers.ofString(
                            "{\"citaId\":\"" + CITA_REALIZADA + "\",\"resultado\":\"REALIZADA\"}",
                            StandardCharsets.UTF_8)
                    : HttpRequest.BodyPublishers.noBody();
            if (ruta.equals("/api/realizaciones-prueba")) {
                solicitud.header("Content-Type", "application/json; charset=utf-8");
            }
            HttpResponse<String> respuesta = clienteHttp.send(
                    solicitud.method(
                                    ruta.equals("/api/realizaciones-prueba") ? "POST" : "GET",
                                    cuerpo)
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(401, respuesta.statusCode(), ruta);
        }
    }

    private static final List<String> RUTAS_PROTEGIDAS = List.of(
            "/api/citas",
            "/api/solicitudes-cita",
            "/api/notificaciones",
            "/api/historial-pruebas",
            "/api/incidencias",
            "/api/encuestas",
            "/api/seguimientos",
            "/api/prescripciones-pruebas",
            "/api/prescripciones-seguimiento",
            "/api/agendas/disponibilidad?tipoPrueba=TAC&desde=2026-11-24T12%3A00&hasta=2026-11-24T16%3A00&duracionMinutos=30",
            "/api/informes-prueba",
            "/api/realizaciones-prueba");

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

        private void avanzar(java.time.Duration duracion) {
            instante = instante.plus(duracion);
        }
    }
}
