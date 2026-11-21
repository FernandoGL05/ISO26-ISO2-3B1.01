package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.EstadoCita;
import es.uclm.esi.iso2.B101.servidor.dominio.EstadoSolicitudCita;
import es.uclm.esi.iso2.B101.servidor.dominio.SolicitudCita;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoSolicitudCita;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSolicitudesCitaMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioCitas;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Cu02HttpHandlerTest {
    private static final String TOKEN_PACIENTE_1 = "Bearer test-paciente-1";
    private static final String TOKEN_PACIENTE_2 = "Bearer test-paciente-2";
    private static final String TOKEN_OTRO_ROL = "Bearer test-otro-rol";
    private static final LocalDateTime FECHA_CITA = LocalDateTime.of(2026, 12, 1, 10, 30);
    private static final LocalDateTime NUEVA_FECHA = LocalDateTime.of(2026, 12, 3, 12, 0);

    private final HttpClient clienteHttp = HttpClient.newHttpClient();
    private HttpServer servidor;
    private URI baseUri;
    private RepositorioCitasMemoria repositorioCitas;
    private RepositorioSolicitudesCitaMemoria repositorioSolicitudes;
    private ServicioCitas servicio;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        repositorioCitas = new RepositorioCitasMemoria();
        repositorioSolicitudes = new RepositorioSolicitudesCitaMemoria();
        servicio = new ServicioCitas(repositorioCitas, repositorioSolicitudes);
        AutorizadorCitas autorizador = this::autenticar;

        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        Cu02HttpHandler.registrarEn(servidor, servicio, autorizador);
        servidor.start();
        baseUri = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort());
    }

    @AfterEach
    void detenerServidorTemporal() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void consultaCitasPropiasYDevuelveJsonConCaracteresEscapados() throws Exception {
        guardarCita("cita-1", "paciente-1", "Radiografía \"X\" \\ tórax");
        guardarCita("cita-2", "paciente-2", "Ecografía");

        HttpResponse<String> respuesta = enviar("GET", "/api/citas", TOKEN_PACIENTE_1, null);

        assertEquals(200, respuesta.statusCode());
        assertContentTypeJson(respuesta);
        assertTrue(respuesta.body().startsWith("["));
        assertTrue(respuesta.body().endsWith("]"));
        assertTrue(respuesta.body().contains("\"id\":\"cita-1\""));
        assertTrue(respuesta.body().contains("\"tipoPrueba\":\"Radiografía \\\"X\\\" \\\\ tórax\""));
        assertFalse(respuesta.body().contains("cita-2"));
    }

    @Test
    void ignoraPacienteIdProporcionadoEnLaConsulta() throws Exception {
        guardarCita("cita-1", "paciente-1", "Radiografía");
        guardarCita("cita-2", "paciente-2", "Ecografía");

        HttpResponse<String> respuesta = enviar(
                "GET", "/api/citas?pacienteId=paciente-2", TOKEN_PACIENTE_1, null);

        assertEquals(200, respuesta.statusCode());
        assertTrue(respuesta.body().contains("cita-1"));
        assertFalse(respuesta.body().contains("cita-2"));
    }

    @Test
    void consultaSolicitudesPropiasSinIncluirLasDeOtroPaciente() throws Exception {
        repositorioSolicitudes.guardar(crearSolicitud("solicitud-1", "paciente-1", "cita-1"));
        repositorioSolicitudes.guardar(crearSolicitud("solicitud-2", "paciente-2", "cita-2"));

        HttpResponse<String> respuesta = enviar("GET", "/api/solicitudes-cita", TOKEN_PACIENTE_1, null);

        assertEquals(200, respuesta.statusCode());
        assertContentTypeJson(respuesta);
        assertTrue(respuesta.body().contains("solicitud-1"));
        assertFalse(respuesta.body().contains("solicitud-2"));
    }

    @Test
    void devuelveArraysJsonVaciosCuandoNoHayCitasNiSolicitudes() throws Exception {
        HttpResponse<String> respuestaCitas = enviar("GET", "/api/citas", TOKEN_PACIENTE_1, null);
        HttpResponse<String> respuestaSolicitudes = enviar(
                "GET", "/api/solicitudes-cita", TOKEN_PACIENTE_1, null);

        assertEquals(200, respuestaCitas.statusCode());
        assertEquals("[]", respuestaCitas.body());
        assertEquals(200, respuestaSolicitudes.statusCode());
        assertEquals("[]", respuestaSolicitudes.body());
    }

    @Test
    void confirmaCitaPropiaYDevuelveJsonDeExito() throws Exception {
        Cita cita = guardarCita("cita-1", "paciente-1", "Radiografía");

        HttpResponse<String> respuesta = enviar(
                "POST", "/api/citas/cita-1/confirmacion", TOKEN_PACIENTE_1, null);

        assertEquals(200, respuesta.statusCode());
        assertContentTypeJson(respuesta);
        assertTrue(respuesta.body().contains("\"exito\":true"));
        assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
    }

    @Test
    void solicitaCambioFechaGuardaSolicitudPendienteSinModificarLaCita() throws Exception {
        Cita cita = guardarCita("cita-1", "paciente-1", "Radiografía");
        LocalDateTime fechaOriginal = cita.getFechaHora();
        EstadoCita estadoOriginal = cita.getEstado();
        String cuerpo = "{\"nuevaFechaHora\":\"2026-12-03T12:00:00\"}";

        HttpResponse<String> respuesta = enviar(
                "POST", "/api/citas/cita-1/solicitudes/cambio", TOKEN_PACIENTE_1, cuerpo);

        assertEquals(201, respuesta.statusCode());
        assertContentTypeJson(respuesta);
        assertTrue(respuesta.body().contains("\"tipo\":\"CAMBIO_FECHA\""));
        assertTrue(respuesta.body().contains("\"nuevaFechaHora\":\"2026-12-03T12:00\""));
        assertTrue(respuesta.body().contains("\"estado\":\"PENDIENTE\""));

        SolicitudCita solicitud = unicaSolicitudDeCita("cita-1");
        assertEquals(TipoSolicitudCita.CAMBIO_FECHA, solicitud.getTipo());
        assertEquals(EstadoSolicitudCita.PENDIENTE, solicitud.getEstado());
        assertEquals(NUEVA_FECHA, solicitud.getNuevaFechaHora());
        assertEquals(fechaOriginal, cita.getFechaHora());
        assertEquals(estadoOriginal, cita.getEstado());
    }

    @Test
    void solicitaCancelacionPendienteSinModificarLaCita() throws Exception {
        Cita cita = guardarCita("cita-1", "paciente-1", "Radiografía");
        LocalDateTime fechaOriginal = cita.getFechaHora();
        EstadoCita estadoOriginal = cita.getEstado();

        HttpResponse<String> respuesta = enviar(
                "POST", "/api/citas/cita-1/solicitudes/cancelacion", TOKEN_PACIENTE_1, null);

        assertEquals(201, respuesta.statusCode());
        assertContentTypeJson(respuesta);
        assertTrue(respuesta.body().contains("\"tipo\":\"CANCELACION\""));
        assertTrue(respuesta.body().contains("\"nuevaFechaHora\":null"));
        assertTrue(respuesta.body().contains("\"estado\":\"PENDIENTE\""));

        SolicitudCita solicitud = unicaSolicitudDeCita("cita-1");
        assertEquals(EstadoSolicitudCita.PENDIENTE, solicitud.getEstado());
        assertEquals(EstadoCita.PROGRAMADA, cita.getEstado());
        assertEquals(fechaOriginal, cita.getFechaHora());
        assertEquals(estadoOriginal, cita.getEstado());
    }

    @Test
    void rechazaPeticionesSinTokenCon401() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/api/citas", null, null);

        assertErrorJson(respuesta, 401, "NO_AUTENTICADO");
    }

    @Test
    void rechazaTokensInvalidosCon401() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/api/citas", "Bearer token-invalido", null);

        assertErrorJson(respuesta, 401, "NO_AUTENTICADO");
    }

    @Test
    void rechazaUsuariosSinRolPacienteCon403() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/api/citas", TOKEN_OTRO_ROL, null);

        assertErrorJson(respuesta, 403, "PROHIBIDO");
    }

    @Test
    void impideConsultarYModificarCitasDeOtroPaciente() throws Exception {
        Cita citaAjena = guardarCita("cita-ajena", "paciente-2", "Radiografía");
        HttpResponse<String> respuestaConsulta = enviar("GET", "/api/citas", TOKEN_PACIENTE_1, null);

        HttpResponse<String> respuestaConfirmacion = enviar(
                "POST", "/api/citas/cita-ajena/confirmacion", TOKEN_PACIENTE_1, null);
        HttpResponse<String> respuestaCambio = enviar(
                "POST", "/api/citas/cita-ajena/solicitudes/cambio", TOKEN_PACIENTE_1,
                "{\"nuevaFechaHora\":\"2026-12-03T12:00:00\"}");
        HttpResponse<String> respuestaCancelacion = enviar(
                "POST", "/api/citas/cita-ajena/solicitudes/cancelacion", TOKEN_PACIENTE_1, null);

        assertEquals(200, respuestaConsulta.statusCode());
        assertFalse(respuestaConsulta.body().contains("cita-ajena"));
        assertErrorJson(respuestaConfirmacion, 404, "NO_ENCONTRADO");
        assertErrorJson(respuestaCambio, 404, "NO_ENCONTRADO");
        assertErrorJson(respuestaCancelacion, 404, "NO_ENCONTRADO");
        assertTrue(repositorioSolicitudes.buscarPorCitaId("cita-ajena").isEmpty());
        assertEquals(EstadoCita.PROGRAMADA, citaAjena.getEstado());
    }

    @Test
    void rechazaCitasInexistentesCon404() throws Exception {
        HttpResponse<String> respuesta = enviar(
                "POST", "/api/citas/no-existe/confirmacion", TOKEN_PACIENTE_1, null);

        assertErrorJson(respuesta, 404, "NO_ENCONTRADO");
    }

    @Test
    void rechazaFechasYJsonMalFormadosCon400() throws Exception {
        HttpResponse<String> fechaInvalida = enviar(
                "POST", "/api/citas/cita-1/solicitudes/cambio", TOKEN_PACIENTE_1,
                "{\"nuevaFechaHora\":\"no-es-una-fecha\"}");
        HttpResponse<String> jsonInvalido = enviar(
                "POST", "/api/citas/cita-1/solicitudes/cambio", TOKEN_PACIENTE_1,
                "{\"nuevaFechaHora\":12}");

        assertErrorJson(fechaInvalida, 400, "SOLICITUD_INVALIDA");
        assertErrorJson(jsonInvalido, 400, "SOLICITUD_INVALIDA");
        assertTrue(repositorioSolicitudes.buscarPorCitaId("cita-1").isEmpty());
    }

    @Test
    void rechazaMetodosIncorrectosCon405() throws Exception {
        HttpResponse<String> metodoConsultaIncorrecto = enviar(
                "POST", "/api/citas", TOKEN_PACIENTE_1, null);
        HttpResponse<String> metodoConfirmacionIncorrecto = enviar(
                "GET", "/api/citas/cita-1/confirmacion", TOKEN_PACIENTE_1, null);

        assertErrorJson(metodoConsultaIncorrecto, 405, "METODO_NO_PERMITIDO");
        assertEquals("GET", metodoConsultaIncorrecto.headers().firstValue("Allow").orElseThrow());
        assertErrorJson(metodoConfirmacionIncorrecto, 405, "METODO_NO_PERMITIDO");
        assertEquals("POST", metodoConfirmacionIncorrecto.headers().firstValue("Allow").orElseThrow());
    }

    @Test
    void rutasQueSoloCoincidenPorPrefijoDevuelven404() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/api/citas-incorrecto", TOKEN_PACIENTE_1, null);

        assertErrorJson(respuesta, 404, "NO_ENCONTRADO");
    }

    private AutorizadorCitas.IdentidadAutorizada autenticarTokenDePrueba(String cabecera) {
        return switch (cabecera) {
            case TOKEN_PACIENTE_1 -> new AutorizadorCitas.IdentidadAutorizada(true, "paciente-1");
            case TOKEN_PACIENTE_2 -> new AutorizadorCitas.IdentidadAutorizada(true, "paciente-2");
            case TOKEN_OTRO_ROL -> new AutorizadorCitas.IdentidadAutorizada(false, null);
            default -> null;
        };
    }

    private Optional<AutorizadorCitas.IdentidadAutorizada> autenticar(String cabecera) {
        return Optional.ofNullable(autenticarTokenDePrueba(cabecera));
    }

    private Cita guardarCita(String id, String pacienteId, String tipoPrueba) {
        Cita cita = new Cita(id, pacienteId, tipoPrueba, FECHA_CITA);
        repositorioCitas.guardar(cita);
        return cita;
    }

    private SolicitudCita crearSolicitud(String id, String pacienteId, String citaId) {
        return new SolicitudCita(
                id, citaId, pacienteId, TipoSolicitudCita.CAMBIO_FECHA, NUEVA_FECHA);
    }

    private SolicitudCita unicaSolicitudDeCita(String citaId) {
        List<SolicitudCita> solicitudes = repositorioSolicitudes.buscarPorCitaId(citaId);
        assertEquals(1, solicitudes.size());
        return solicitudes.get(0);
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String autorizacion, String cuerpo)
            throws IOException, InterruptedException {
        HttpRequest.Builder solicitud = HttpRequest.newBuilder(baseUri.resolve(ruta))
                .header("Accept", "application/json");
        if (autorizacion != null) {
            solicitud.header("Authorization", autorizacion);
        }

        HttpRequest.BodyPublisher contenido = cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8);
        if (cuerpo != null) {
            solicitud.header("Content-Type", "application/json; charset=utf-8");
        }
        return clienteHttp.send(solicitud.method(metodo, contenido).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertContentTypeJson(HttpResponse<String> respuesta) {
        assertTrue(respuesta.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
    }

    private void assertErrorJson(HttpResponse<String> respuesta, int estado, String codigoError) {
        assertEquals(estado, respuesta.statusCode());
        assertContentTypeJson(respuesta);
        assertTrue(respuesta.body().startsWith("{"));
        assertTrue(respuesta.body().contains("\"exito\":false"));
        assertTrue(respuesta.body().contains("\"codigoError\":\"" + codigoError + "\""));
        assertTrue(respuesta.body().contains("\"mensaje\":"));
    }
}
