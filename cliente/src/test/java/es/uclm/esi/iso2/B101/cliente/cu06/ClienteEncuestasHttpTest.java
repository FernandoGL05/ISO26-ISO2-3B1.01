package es.uclm.esi.iso2.B101.cliente.cu06;

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

class ClienteEncuestasHttpTest {
    private static final String TOKEN = "token-de-prueba-sensible";

    private HttpServer servidor;
    private AtomicReference<String> metodo;
    private AtomicReference<String> ruta;
    private AtomicReference<String> autorizacion;
    private AtomicReference<String> tipoContenido;
    private AtomicReference<String> cuerpoRecibido;
    private AtomicReference<Integer> estadoRespuesta;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        metodo = new AtomicReference<>();
        ruta = new AtomicReference<>();
        autorizacion = new AtomicReference<>();
        tipoContenido = new AtomicReference<>();
        cuerpoRecibido = new AtomicReference<>();
        estadoRespuesta = new AtomicReference<>(200);
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/encuestas", intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            ruta.set(intercambio.getRequestURI().getRawPath());
            autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            tipoContenido.set(intercambio.getRequestHeaders().getFirst("Content-Type"));
            cuerpoRecibido.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int estado = estadoRespuesta.get();
            byte[] cuerpo = (estado >= 200 && estado < 300 ? "[]" : "{\"exito\":false}")
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
    void consultaEncuestasDisponiblesConToken() throws Exception {
        ClienteEncuestasHttp.ResultadoHttp respuesta = nuevoCliente().consultarEncuestas();

        assertTrue(respuesta.esExito());
        assertEquals("GET", metodo.get());
        assertEquals("/api/encuestas", ruta.get());
        assertEquals("Bearer " + TOKEN, autorizacion.get());
    }

    @Test
    void respondeEncuestaConJsonEscapadoYSegmentoCodificado() throws Exception {
        ClienteEncuestasHttp.ResultadoHttp respuesta =
                nuevoCliente().responderEncuesta("encuesta /#ñ", 5, "Bien \"gracias\"\nsegunda");

        assertEquals(200, respuesta.codigoHttp());
        assertEquals("POST", metodo.get());
        assertEquals("/api/encuestas/encuesta%20%2F%23%C3%B1/respuestas", ruta.get());
        assertEquals(
                "{\"puntuacion\":5,\"comentario\":\"Bien \\\"gracias\\\"\\nsegunda\"}",
                cuerpoRecibido.get());
        assertEquals("application/json; charset=utf-8", tipoContenido.get());
    }

    @Test
    void consultaRespuestasPropiasYConservaEstadosDeError() throws Exception {
        ClienteEncuestasHttp cliente = nuevoCliente();
        ClienteEncuestasHttp.ResultadoHttp respuesta = cliente.consultarMisRespuestas();

        assertTrue(respuesta.esExito());
        assertEquals("GET", metodo.get());
        assertEquals("/api/encuestas/mis-respuestas", ruta.get());
        assertEquals("", cuerpoRecibido.get());

        estadoRespuesta.set(401);
        ClienteEncuestasHttp.ResultadoHttp error = cliente.consultarMisRespuestas();
        assertEquals(401, error.codigoHttp());
        assertFalse(error.esExito());
        assertEquals("{\"exito\":false}", error.cuerpoJson());
    }

    @Test
    void errorDeConexionNoExponeToken() {
        ClienteEncuestasHttp cliente = nuevoCliente();
        servidor.stop(0);
        servidor = null;

        IOException error = assertThrows(IOException.class, cliente::consultarEncuestas);

        assertFalse(error.getMessage().contains(TOKEN));
    }

    private ClienteEncuestasHttp nuevoCliente() {
        URI uri = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort());
        return new ClienteEncuestasHttp(uri.toString(), TOKEN);
    }
}
