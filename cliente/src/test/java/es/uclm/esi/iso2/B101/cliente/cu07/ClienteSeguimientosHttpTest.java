package es.uclm.esi.iso2.B101.cliente.cu07;

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

class ClienteSeguimientosHttpTest {
    private static final String TOKEN = "token-de-prueba-sensible";

    private HttpServer servidor;
    private AtomicReference<String> metodo;
    private AtomicReference<String> ruta;
    private AtomicReference<String> autorizacion;
    private AtomicReference<Integer> estadoRespuesta;

    @BeforeEach
    void iniciarServidorTemporal() throws IOException {
        metodo = new AtomicReference<>();
        ruta = new AtomicReference<>();
        autorizacion = new AtomicReference<>();
        estadoRespuesta = new AtomicReference<>(200);
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/seguimientos", intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            ruta.set(intercambio.getRequestURI().getRawPath());
            autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            intercambio.getRequestBody().readAllBytes();
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
    void consultaListadoConTokenDeSesion() throws Exception {
        ClienteSeguimientosHttp.ResultadoHttp respuesta = nuevoCliente().consultarSeguimientos();

        assertTrue(respuesta.esExito());
        assertEquals("GET", metodo.get());
        assertEquals("/api/seguimientos", ruta.get());
        assertEquals("Bearer " + TOKEN, autorizacion.get());
    }

    @Test
    void codificaElIdentificadorParaConsultarDetalle() throws Exception {
        ClienteSeguimientosHttp.ResultadoHttp respuesta =
                nuevoCliente().consultarDetalle("seguimiento /#ñ");

        assertEquals(200, respuesta.codigoHttp());
        assertEquals("GET", metodo.get());
        assertEquals("/api/seguimientos/seguimiento%20%2F%23%C3%B1", ruta.get());
    }

    @Test
    void conservaErroresHttpYNoExponeTokenSiFallaLaConexion() throws Exception {
        ClienteSeguimientosHttp cliente = nuevoCliente();
        estadoRespuesta.set(404);
        ClienteSeguimientosHttp.ResultadoHttp errorHttp = cliente.consultarDetalle("ausente");

        assertEquals(404, errorHttp.codigoHttp());
        assertFalse(errorHttp.esExito());
        assertEquals("{\"exito\":false}", errorHttp.cuerpoJson());

        servidor.stop(0);
        servidor = null;
        IOException errorConexion = assertThrows(IOException.class, cliente::consultarSeguimientos);
        assertFalse(errorConexion.getMessage().contains(TOKEN));
    }

    private ClienteSeguimientosHttp nuevoCliente() {
        URI uri = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort());
        return new ClienteSeguimientosHttp(uri.toString(), TOKEN);
    }
}
