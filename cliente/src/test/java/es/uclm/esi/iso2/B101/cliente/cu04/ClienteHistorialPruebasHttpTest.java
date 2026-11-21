package es.uclm.esi.iso2.B101.cliente.cu04;

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

class ClienteHistorialPruebasHttpTest {
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
        servidor.createContext("/api/historial-pruebas", intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            ruta.set(intercambio.getRequestURI().getRawPath());
            autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            int estado = estadoRespuesta.get();
            String cuerpo = estado == 200 ? "[]" : "{\"exito\":false,\"codigoError\":\"ERROR\"}";
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            intercambio.sendResponseHeaders(estado, bytes.length);
            intercambio.getResponseBody().write(bytes);
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
    void consultaElHistorialMedianteGetYEnviaTokenBearer() throws Exception {
        ClienteHistorialPruebasHttp cliente = nuevoCliente();

        ClienteHistorialPruebasHttp.ResultadoHttp respuesta = cliente.consultarHistorial();

        assertEquals(200, respuesta.codigoHttp());
        assertEquals("[]", respuesta.cuerpoJson());
        assertEquals("GET", metodo.get());
        assertEquals("/api/historial-pruebas", ruta.get());
        assertEquals("Bearer " + TOKEN, autorizacion.get());
        assertTrue(respuesta.esExito());
    }

    @Test
    void distingueLosCodigosHttpDeErrorSinConvertirlosEnExito() throws Exception {
        ClienteHistorialPruebasHttp cliente = nuevoCliente();
        int[] estadosError = {400, 401, 403, 404, 405, 409, 500};

        for (int estado : estadosError) {
            estadoRespuesta.set(estado);
            ClienteHistorialPruebasHttp.ResultadoHttp respuesta = cliente.consultarHistorial();

            assertEquals(estado, respuesta.codigoHttp());
            assertTrue(respuesta.cuerpoJson().contains("\"codigoError\":\"ERROR\""));
            assertFalse(respuesta.esExito());
        }
    }

    @Test
    void errorDeConexionNoIncluyeElTokenEnElMensaje() {
        ClienteHistorialPruebasHttp cliente = nuevoCliente();
        servidor.stop(0);
        servidor = null;

        IOException error = assertThrows(IOException.class, () -> cliente.consultarHistorial());

        assertFalse(error.getMessage().contains(TOKEN));
    }

    private ClienteHistorialPruebasHttp nuevoCliente() {
        URI uri = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort());
        return new ClienteHistorialPruebasHttp(uri.toString(), TOKEN);
    }
}
