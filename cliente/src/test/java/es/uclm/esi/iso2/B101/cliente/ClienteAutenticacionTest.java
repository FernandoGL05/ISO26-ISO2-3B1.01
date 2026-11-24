package es.uclm.esi.iso2.B101.cliente;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.cliente.comunicacion.ClienteAutenticacion;
import es.uclm.esi.iso2.B101.cliente.comunicacion.JsonClienteUtil;
import es.uclm.esi.iso2.B101.cliente.modelo.RespuestaLogin;
import es.uclm.esi.iso2.B101.cliente.modelo.SesionCliente;
import es.uclm.esi.iso2.B101.cliente.presentacion.ControladorInicioSesion;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClienteAutenticacionTest {

    private HttpServer servidorSimulado;
    private ControladorInicioSesion controlador;
    private final AtomicBoolean sesionActiva = new AtomicBoolean(false);

    @BeforeEach
    void setUp() throws IOException {
        servidorSimulado = HttpServer.create(new InetSocketAddress(0), 0);
        servidorSimulado.createContext("/api/auth/login", this::manejarLogin);
        servidorSimulado.createContext("/api/auth/sso", this::manejarSso);
        servidorSimulado.createContext("/api/auth/session", this::manejarSession);
        servidorSimulado.createContext("/api/auth/logout", this::manejarLogout);
        servidorSimulado.start();

        String baseUrl = "http://localhost:" + servidorSimulado.getAddress().getPort();
        controlador = new ControladorInicioSesion(new ClienteAutenticacion(baseUrl));
    }

    @AfterEach
    void tearDown() {
        servidorSimulado.stop(0);
    }

    @Test
    void validaCamposVaciosEnElClienteSinLlamarAlServidor() {
        RespuestaLogin r = controlador.iniciarSesion(" ", "");
        assertFalse(r.exito());
        assertEquals("CREDENCIALES_VACIAS", r.codigoError());
        assertTrue(controlador.obtenerSesionActual().isEmpty());
    }

    @Test
    void flujoLoginValidacionYCierreDesdeCliente() {
        RespuestaLogin res = controlador.iniciarSesion("paciente1", "paciente123");
        assertTrue(res.exito());
        SesionCliente sesion = controlador.obtenerSesionActual().orElseThrow();
        assertEquals("paciente1", sesion.username());
        assertEquals("PACIENTE", sesion.rol());

        assertTrue(controlador.verificarSesionActivaEnServidor());
        assertTrue(controlador.cerrarSesion());
        assertTrue(controlador.obtenerSesionActual().isEmpty());
    }

    @Test
    void inicioDeSesionConSSOYRechazoDeCredencialesInvalidas() {
        RespuestaLogin fallo = controlador.iniciarSesion("paciente1", "incorrecta");
        assertFalse(fallo.exito());
        assertEquals("CREDENCIALES_INVALIDAS", fallo.codigoError());

        RespuestaLogin ssoOk = controlador.iniciarSesionSSO("sso-sescam-medico1");
        assertTrue(ssoOk.exito());
        assertEquals("MEDICO", ssoOk.obtenerSesion().orElseThrow().rol());
    }

    private void manejarLogin(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> datos = JsonClienteUtil.parsearObjetoPlano(body);
        if ("paciente1".equals(datos.get("username")) && "paciente123".equals(datos.get("password"))) {
            sesionActiva.set(true);
            responder(exchange, 200,
                    "{\"exito\":true,\"token\":\"tok-123\",\"usuarioId\":\"USR-001\",\"username\":\"paciente1\","
                            + "\"nombreCompleto\":\"Ana García López\",\"rol\":\"PACIENTE\","
                            + "\"expiraEn\":\"2026-11-19T18:00:00Z\",\"origenAutenticacion\":\"LOCAL\","
                            + "\"mensaje\":\"Inicio de sesión correcto.\"}");
        } else {
            responder(exchange, 401,
                    "{\"exito\":false,\"codigoError\":\"CREDENCIALES_INVALIDAS\","
                            + "\"mensaje\":\"Usuario o contraseña incorrectos.\"}");
        }
    }

    private void manejarSso(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> datos = JsonClienteUtil.parsearObjetoPlano(body);
        if ("sso-sescam-medico1".equals(datos.get("ssoToken"))) {
            sesionActiva.set(true);
            responder(exchange, 200,
                    "{\"exito\":true,\"token\":\"tok-sso\",\"usuarioId\":\"USR-002\",\"username\":\"medico1\","
                            + "\"nombreCompleto\":\"Dr. Carlos Ruiz Martín\",\"rol\":\"MEDICO\","
                            + "\"expiraEn\":\"2026-11-19T18:00:00Z\",\"origenAutenticacion\":\"SSO_CORPORATIVO\","
                            + "\"mensaje\":\"Inicio de sesión correcto.\"}");
        } else {
            responder(exchange, 401,
                    "{\"exito\":false,\"codigoError\":\"SSO_INVALIDO\",\"mensaje\":\"Token SSO inválido.\"}");
        }
    }

    private void manejarSession(HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (sesionActiva.get() && "Bearer tok-123".equals(auth)) {
            responder(exchange, 200,
                    "{\"exito\":true,\"token\":\"tok-123\",\"usuarioId\":\"USR-001\",\"username\":\"paciente1\","
                            + "\"nombreCompleto\":\"Ana García López\",\"rol\":\"PACIENTE\","
                            + "\"expiraEn\":\"2026-11-19T18:00:00Z\",\"origenAutenticacion\":\"LOCAL\","
                            + "\"mensaje\":\"Sesión válida.\"}");
        } else {
            responder(exchange, 401, "{\"exito\":false,\"codigoError\":\"SESION_INVALIDA\"}");
        }
    }

    private void manejarLogout(HttpExchange exchange) throws IOException {
        sesionActiva.set(false);
        responder(exchange, 200, "{\"exito\":true,\"mensaje\":\"Sesión cerrada correctamente.\"}");
    }

    private void responder(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
