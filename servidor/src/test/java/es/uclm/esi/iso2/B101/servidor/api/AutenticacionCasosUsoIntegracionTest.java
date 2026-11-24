package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.Rol;
import es.uclm.esi.iso2.B101.servidor.dominio.Sesion;
import es.uclm.esi.iso2.B101.servidor.integracion.ProveedorSSOSimulado;
import es.uclm.esi.iso2.B101.servidor.persistencia.AsociacionIdentidadesClinicas;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioAuditoriaAccesoMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioCitasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioPrescripcionesDiagnosticasMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSesionesMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSolicitudesCitaMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioUsuariosMemoria;
import es.uclm.esi.iso2.B101.servidor.seguridad.AutenticadorSesion;
import es.uclm.esi.iso2.B101.servidor.seguridad.GeneradorTokens;
import es.uclm.esi.iso2.B101.servidor.seguridad.GestorContrasenas;
import es.uclm.esi.iso2.B101.servidor.servicio.PoliticaPrescripcionDiagnosticaPorVinculo;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAutenticacion;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioCitas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioPrescripcionesDiagnosticas;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutenticacionCasosUsoIntegracionTest {
    private static final String RUTA_CITAS = "/api/citas";
    private static final String RUTA_PRESCRIPCIONES = "/api/prescripciones-pruebas";
    private static final String RUTA_LOGIN = "/api/auth/login";
    private static final LocalDateTime FECHA_CITA = LocalDateTime.of(2026, 12, 1, 10, 30);

    private final HttpClient clienteHttp = HttpClient.newHttpClient();
    private RepositorioSesionesMemoria repositorioSesiones;
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
        repositorioSesiones = new RepositorioSesionesMemoria();
        ServicioAutenticacion servicioAutenticacion = new ServicioAutenticacion(
                repositorioUsuarios,
                repositorioSesiones,
                new RepositorioAuditoriaAccesoMemoria(),
                gestorContrasenas,
                new GeneradorTokens(),
                new ProveedorSSOSimulado());

        AsociacionIdentidadesClinicas asociaciones = new AsociacionIdentidadesClinicas(
                Map.of("USR-001", "paciente-1", "USR-005", "paciente-2"),
                Map.of("USR-002", "medico-1", "USR-006", "medico-2"));
        AutenticadorSesion autenticadorSesion = new AutenticadorSesion(servicioAutenticacion);

        RepositorioCitasMemoria repositorioCitas = new RepositorioCitasMemoria();
        repositorioCitas.guardar(new Cita("cita-1", "paciente-1", "Radiografía", FECHA_CITA));
        repositorioCitas.guardar(new Cita("cita-2", "paciente-2", "Ecografía", FECHA_CITA));
        ServicioCitas servicioCitas =
                new ServicioCitas(repositorioCitas, new RepositorioSolicitudesCitaMemoria());

        ServicioPrescripcionesDiagnosticas servicioPrescripciones = new ServicioPrescripcionesDiagnosticas(
                new RepositorioPrescripcionesDiagnosticasMemoria(),
                Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC),
                new PoliticaPrescripcionDiagnosticaPorVinculo(Map.of(
                        "medico-1", Set.of("paciente-1"),
                        "medico-2", Set.of("paciente-2"))));

        servidor = new ServidorHTTP(
                0,
                servicioAutenticacion,
                servicioCitas,
                new AutorizadorCitasPorSesion(autenticadorSesion, asociaciones),
                servicioPrescripciones,
                new AutorizadorPrescripcionesPorSesion(autenticadorSesion, asociaciones));
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
    void pacienteAutenticadoSoloConsultaSusCitasAunqueIndiqueOtroPaciente() throws Exception {
        String token = iniciarSesion("paciente1", "paciente123");

        HttpResponse<String> respuesta =
                enviar("GET", RUTA_CITAS + "?pacienteId=paciente-2", token, null);

        assertEquals(200, respuesta.statusCode());
        assertTrue(respuesta.body().contains("\"id\":\"cita-1\""));
        assertFalse(respuesta.body().contains("\"id\":\"cita-2\""));
    }

    @Test
    void medicoAutenticadoPuedePrescribirConSuIdentidadAsociada() throws Exception {
        String token = iniciarSesion("medico1", "medico123");
        String cuerpo = "{\"pacienteId\":\"paciente-1\",\"tipoPrueba\":\"TAC\","
                + "\"indicacionClinica\":\"Dolor\",\"justificacion\":\"Descartar lesión\","
                + "\"prioridad\":\"URGENTE\"}";

        HttpResponse<String> creada =
                enviar("POST", RUTA_PRESCRIPCIONES, token, cuerpo);

        assertEquals(201, creada.statusCode());
        assertTrue(creada.body().contains("\"medicoId\":\"medico-1\""));
        assertTrue(creada.body().contains("\"pacienteId\":\"paciente-1\""));
        assertEquals(200, enviar("GET", RUTA_PRESCRIPCIONES, token, null).statusCode());
    }

    @Test
    void rechazaTokensInexistentesYExpiradosCon401() throws Exception {
        repositorioSesiones.guardar(new Sesion(
                "token-expirado",
                "USR-001",
                "paciente1",
                "Paciente uno",
                Rol.PACIENTE,
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(3600),
                Sesion.ORIGEN_LOCAL));

        assertEquals(401, enviar("GET", RUTA_CITAS, "token-inexistente", null).statusCode());
        assertEquals(401, enviar("GET", RUTA_CITAS, "token-expirado", null).statusCode());
        assertEquals(401, enviar("GET", RUTA_PRESCRIPCIONES, "token-inexistente", null).statusCode());
        assertEquals(401, enviar("GET", RUTA_PRESCRIPCIONES, "token-expirado", null).statusCode());
    }

    @Test
    void rechazaCon403UnRolAutenticadoPeroIncompatible() throws Exception {
        String paciente = iniciarSesion("paciente1", "paciente123");
        String medico = iniciarSesion("medico1", "medico123");

        assertEquals(403, enviar("GET", RUTA_PRESCRIPCIONES, paciente, null).statusCode());
        assertEquals(403, enviar("GET", RUTA_CITAS, medico, null).statusCode());
    }

    @Test
    void tokenDejaDeAutorizarAlCerrarSesion() throws Exception {
        String token = iniciarSesion("paciente1", "paciente123");

        assertEquals(200, enviar("POST", "/api/auth/logout", token, null).statusCode());
        assertEquals(401, enviar("GET", RUTA_CITAS, token, null).statusCode());
        assertEquals(401, enviar("GET", RUTA_PRESCRIPCIONES, token, null).statusCode());
    }

    @Test
    void medicoNoPuedeConsultarPrescripcionesDeOtroMedico() throws Exception {
        String medico1 = iniciarSesion("medico1", "medico123");
        String medico2 = iniciarSesion("medico2", "medico123");
        String cuerpo = "{\"pacienteId\":\"paciente-1\",\"tipoPrueba\":\"TAC\","
                + "\"indicacionClinica\":\"Dolor\",\"justificacion\":\"Descartar lesión\","
                + "\"prioridad\":\"URGENTE\"}";
        HttpResponse<String> creada = enviar("POST", RUTA_PRESCRIPCIONES, medico1, cuerpo);
        String id = JsonUtil.parsearObjetoPlano(creada.body()).get("id");

        assertEquals(201, creada.statusCode());
        assertEquals("[]", enviar("GET", RUTA_PRESCRIPCIONES, medico2, null).body());
        assertEquals(404, enviar("GET", RUTA_PRESCRIPCIONES + "/" + id, medico2, null).statusCode());
    }

    private String iniciarSesion(String username, String password) throws Exception {
        String cuerpo = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        HttpResponse<String> respuesta = enviar("POST", RUTA_LOGIN, null, cuerpo);
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
}
