package es.uclm.esi.iso2.B101.cliente.cu20;

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

class ClienteTiposPruebaHttpTest {

    private static final String TOKEN = "token-admin";
    private HttpServer servidor;
    private ClienteTiposPruebaHttp cliente;
    private final AtomicReference<PeticionRecibida> ultimaPeticion = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::responder);
        servidor.start();
        cliente = new ClienteTiposPruebaHttp(
                "http://127.0.0.1:" + servidor.getAddress().getPort(), TOKEN);
    }

    @AfterEach
    void tearDown() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void enviaTodasLasOperacionesDeTiposPruebaConToken() throws Exception {
        ClienteTiposPruebaHttp.ResultadoHttp crear = cliente.crearTipoPrueba(
                "TAC Abdominal", "TAC", "Tomógrafo", "Sala 1",
                30, "Ayuno 6h", true, true,
                "Control renal", "PREFERENTE", true);
        assertTrue(crear.esExito());
        assertEquals("POST", ultimaPeticion.get().metodo());
        assertEquals("/api/tipos-prueba", ultimaPeticion.get().ruta());
        assertEquals("Bearer " + TOKEN, ultimaPeticion.get().autorizacion());

        cliente.listarTiposPrueba(true);
        assertEquals("GET", ultimaPeticion.get().metodo());
        assertEquals("/api/tipos-prueba?soloActivos=true", ultimaPeticion.get().ruta());

        cliente.obtenerTipoPrueba("TP-1");
        assertEquals("/api/tipos-prueba/TP-1", ultimaPeticion.get().ruta());

        cliente.actualizarTipoPrueba(
                "TP-1", "TAC Torácico", "TAC", "Tomógrafo", "Sala 2",
                20, "Sin prep", false, false,
                "Protección", "URGENTE", false);
        assertEquals("PUT", ultimaPeticion.get().metodo());
        assertEquals("/api/tipos-prueba/TP-1", ultimaPeticion.get().ruta());

        cliente.cambiarEstado("TP-1", false);
        assertEquals("POST", ultimaPeticion.get().metodo());
        assertEquals("/api/tipos-prueba/TP-1/estado", ultimaPeticion.get().ruta());
        assertTrue(ultimaPeticion.get().cuerpo().contains("\"activo\":false"));
    }

    @Test
    void validaLocalmenteAntesDeEnviarPeticion() {
        assertThrows(IllegalArgumentException.class, () -> cliente.crearTipoPrueba(
                "TAC", "TAC", "Tomógrafo", "Sala 1", 30, "Ayuno",
                true, false, "Seg", "ORDINARIA", false));
        assertThrows(IllegalArgumentException.class, () -> cliente.obtenerTipoPrueba(" "));
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
