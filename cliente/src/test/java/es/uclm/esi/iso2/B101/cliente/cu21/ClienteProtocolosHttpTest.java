package es.uclm.esi.iso2.B101.cliente.cu21;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClienteProtocolosHttpTest {

    private static final String TOKEN = "token-admin";
    private HttpServer servidor;
    private ClienteProtocolosHttp cliente;
    private final AtomicReference<PeticionRecibida> ultimaPeticion = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::responder);
        servidor.start();
        cliente = new ClienteProtocolosHttp(
                "http://127.0.0.1:" + servidor.getAddress().getPort(), TOKEN);
    }

    @AfterEach
    void tearDown() {
        if (cliente != null) {
            cliente.close();
        }
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void enviaOperacionesDeProtocolosConToken() throws Exception {
        ClienteProtocolosHttp.ResultadoHttp crear = cliente.crearProtocolo(
                "P-01", "Protocolo RM", "TP-1", "Desc", "Prep", "Seg", true);
        assertTrue(crear.esExito());
        assertEquals("POST", ultimaPeticion.get().metodo());
        assertEquals("/api/protocolos", ultimaPeticion.get().ruta());

        cliente.listarProtocolos("TP-1");
        assertEquals("/api/protocolos?tipoPruebaId=TP-1", ultimaPeticion.get().ruta());

        cliente.obtenerProtocolo("PROT-1");
        assertEquals("/api/protocolos/PROT-1", ultimaPeticion.get().ruta());

        cliente.actualizarProtocolo(
                "PROT-1", "P-01", "Protocolo RM v2", "TP-1", "Desc", "Prep", "Seg", false);
        assertEquals("PUT", ultimaPeticion.get().metodo());
        assertEquals("/api/protocolos/PROT-1", ultimaPeticion.get().ruta());

        cliente.cambiarEstado("PROT-1", true);
        assertEquals("POST", ultimaPeticion.get().metodo());
        assertEquals("/api/protocolos/PROT-1/estado", ultimaPeticion.get().ruta());
    }

    @Test
    void validaCamposObligatoriosAntesDeEnviar() {
        assertThrows(IllegalArgumentException.class, () -> cliente.crearProtocolo(
                " ", "Nombre", "TP-1", "Desc", "Prep", "Seg", false));
        assertThrows(IllegalArgumentException.class, () -> cliente.obtenerProtocolo(""));
        assertNull(ultimaPeticion.get());
    }

    private void responder(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        ultimaPeticion.set(new PeticionRecibida(
                exchange.getRequestMethod(),
                exchange.getRequestURI().toASCIIString(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                body));
        byte[] respuesta = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, respuesta.length);
        try (var salida = exchange.getResponseBody()) {
            salida.write(respuesta);
        }
    }

    private record PeticionRecibida(String metodo, String ruta, String autorizacion, String cuerpo) {
    }
}
