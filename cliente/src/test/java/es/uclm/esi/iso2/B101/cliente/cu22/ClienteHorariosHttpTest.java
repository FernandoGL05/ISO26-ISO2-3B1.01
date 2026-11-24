package es.uclm.esi.iso2.B101.cliente.cu22;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClienteHorariosHttpTest {

    private static final String TOKEN = "token-admin";
    private HttpServer servidor;
    private ClienteHorariosHttp cliente;
    private final AtomicReference<PeticionRecibida> ultimaPeticion = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::responder);
        servidor.start();
        cliente = new ClienteHorariosHttp(
                "http://127.0.0.1:" + servidor.getAddress().getPort(), TOKEN);
    }

    @AfterEach
    void tearDown() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void enviaOperacionesDeHorariosConToken() throws Exception {
        ClienteHorariosHttp.ResultadoHttp crear = cliente.crearHorario(
                "medico-1", "MEDICO", DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(13, 0), 30);
        assertTrue(crear.esExito());
        assertEquals("POST", ultimaPeticion.get().metodo());
        assertEquals("/api/horarios", ultimaPeticion.get().ruta());

        cliente.listarHorarios("medico-1", DayOfWeek.MONDAY);
        assertEquals("/api/horarios?recursoId=medico-1&diaSemana=MONDAY", ultimaPeticion.get().ruta());

        cliente.obtenerHorario("HOR-1");
        assertEquals("/api/horarios/HOR-1", ultimaPeticion.get().ruta());

        cliente.actualizarHorario(
                "HOR-1", "medico-1", "MEDICO", DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(14, 0), 30);
        assertEquals("PUT", ultimaPeticion.get().metodo());
        assertEquals("/api/horarios/HOR-1", ultimaPeticion.get().ruta());

        cliente.cambiarEstado("HOR-1", false);
        assertEquals("POST", ultimaPeticion.get().metodo());
        assertEquals("/api/horarios/HOR-1/estado", ultimaPeticion.get().ruta());
    }

    @Test
    void rechazaHorariosInvalidosAntesDeEnviar() {
        assertThrows(IllegalArgumentException.class, () -> cliente.crearHorario(
                "medico-1", "MEDICO", DayOfWeek.MONDAY,
                LocalTime.of(14, 0), LocalTime.of(9, 0), 30));
        assertThrows(IllegalArgumentException.class, () -> cliente.obtenerHorario(" "));
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
