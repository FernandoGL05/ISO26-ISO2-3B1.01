package es.uclm.esi.iso2.B101.cliente.cu05;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClienteIncidenciasHttpTest {
    private static final String TOKEN = "token-de-prueba-sensible";

    private HttpServer servidor;
    private AtomicReference<String> metodo;
    private AtomicReference<String> ruta;
    private AtomicReference<String> autorizacion;
    private AtomicReference<String> cuerpoRecibido;
    private AtomicReference<Integer> estadoRespuesta;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        metodo = new AtomicReference<>();
        ruta = new AtomicReference<>();
        autorizacion = new AtomicReference<>();
        cuerpoRecibido = new AtomicReference<>();
        estadoRespuesta = new AtomicReference<>(200);
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/incidencias", intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            ruta.set(intercambio.getRequestURI().getRawPath());
            autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            cuerpoRecibido.set(new String(
                    intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int estado = estadoRespuesta.get();
            byte[] cuerpo = (estado >= 200 && estado < 300 ? "{\"id\":\"n1\"}" : "{\"exito\":false}")
                    .getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            intercambio.sendResponseHeaders(estado, cuerpo.length);
            intercambio.getResponseBody().write(cuerpo);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void detenerServidorTemporal() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void comunicaIncidenciaConPostTokenYJsonEscapado() throws Exception {
        ClienteIncidenciasHttp cliente = nuevoCliente();

        ClienteIncidenciasHttp.ResultadoHttp respuesta =
                cliente.comunicarIncidencia("Problema \"importante\"", "Primera línea\nSegunda \\ línea", "cita-1");

        assertEquals(200, respuesta.codigoHttp());
        assertEquals("POST", metodo.get());
        assertEquals("/api/incidencias", ruta.get());
        assertEquals("Bearer " + TOKEN, autorizacion.get());
        assertEquals(
                "{\"asunto\":\"Problema \\\"importante\\\"\",\"descripcion\":\"Primera línea\\nSegunda \\\\ línea\",\"citaId\":\"cita-1\"}",
                cuerpoRecibido.get());
    }

    @Test
    void consultaLaListaDeIncidencias() throws Exception {
        ClienteIncidenciasHttp.ResultadoHttp respuesta = nuevoCliente().consultarIncidencias();

        assertTrue(respuesta.esExito());
        assertEquals("GET", metodo.get());
        assertEquals("/api/incidencias", ruta.get());
        assertEquals("", cuerpoRecibido.get());
        assertEquals("Bearer " + TOKEN, autorizacion.get());
    }

    @Test
    void codificaElIdentificadorAlConsultarElDetalle() throws Exception {
        ClienteIncidenciasHttp.ResultadoHttp respuesta =
                nuevoCliente().consultarIncidencia("incidencia /#ñ");

        assertTrue(respuesta.esExito());
        assertEquals("GET", metodo.get());
        assertEquals("/api/incidencias/incidencia%20%2F%23%C3%B1", ruta.get());
    }

    @Test
    void conservaLosEstadosHttpDeError() throws Exception {
        ClienteIncidenciasHttp cliente = nuevoCliente();
        int[] estadosError = {400, 401, 403, 404, 405, 409, 500};

        for (int estado : estadosError) {
            estadoRespuesta.set(estado);
            ClienteIncidenciasHttp.ResultadoHttp respuesta = cliente.consultarIncidencias();

            assertEquals(estado, respuesta.codigoHttp());
            assertFalse(respuesta.esExito());
        }
    }

    @Test
    void errorDeConexionNoExponeElTokenEnElMensaje() {
        ClienteIncidenciasHttp cliente = nuevoCliente();
        servidor.stop(0);
        servidor = null;

        IOException error = assertThrows(IOException.class, cliente::consultarIncidencias);

        assertFalse(error.getMessage().contains(TOKEN));
    }

    private ClienteIncidenciasHttp nuevoCliente() {
        URI uri = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort());
        return new ClienteIncidenciasHttp(uri.toString(), TOKEN);
    }
}
