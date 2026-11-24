package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHorariosMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioHorarios;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HorariosHttpHandlerTest {

    private static final String TOKEN_ADMIN = "token-admin";
    private static final String TOKEN_ADMISION = "token-admision";
    private static final String BODY_MANANA = """
            {"recursoId":"medico-1","tipoRecurso":"MEDICO","diaSemana":"MONDAY",\
            "horaInicio":"09:00","horaFin":"13:00","duracionHuecoMinutos":30}""";

    private HttpServer servidor;
    private URI baseUri;
    private HttpClient http;

    @BeforeEach
    void setUp() throws IOException {
        ServicioHorarios servicio = new ServicioHorarios(new RepositorioHorariosMemoria());
        AutorizadorAdministrador autorizador = cabecera -> switch (cabecera) {
            case "Bearer " + TOKEN_ADMIN -> Optional.of(
                    new AutorizadorAdministrador.IdentidadAdministrador(true, "admin-1"));
            case "Bearer " + TOKEN_ADMISION -> Optional.of(
                    new AutorizadorAdministrador.IdentidadAdministrador(false, "adm-1"));
            default -> Optional.empty();
        };
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        HorariosHttpHandler.registrarEn(servidor, servicio, autorizador);
        servidor.start();
        baseUri = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort());
        http = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void cicloCompletoHorariosYFiltros() throws Exception {
        HttpResponse<String> creado = enviar("POST", "/api/horarios", BODY_MANANA, TOKEN_ADMIN);
        assertEquals(201, creado.statusCode());
        assertTrue(creado.body().contains("\"id\":\"HOR-1\""));

        HttpResponse<String> filtrado = enviar(
                "GET", "/api/horarios?recursoId=medico-1&diaSemana=MONDAY", null, TOKEN_ADMIN);
        assertEquals(200, filtrado.statusCode());
        assertTrue(filtrado.body().contains("\"09:00\""));

        HttpResponse<String> detalle = enviar("GET", "/api/horarios/HOR-1", null, TOKEN_ADMIN);
        assertEquals(200, detalle.statusCode());

        String actualizadoBody = BODY_MANANA.replace("13:00", "14:00");
        HttpResponse<String> actualizado = enviar("PUT", "/api/horarios/HOR-1", actualizadoBody, TOKEN_ADMIN);
        assertEquals(200, actualizado.statusCode());
        assertTrue(actualizado.body().contains("\"14:00\""));

        HttpResponse<String> estado = enviar(
                "POST", "/api/horarios/HOR-1/estado", "{\"activo\":false}", TOKEN_ADMIN);
        assertEquals(200, estado.statusCode());
        assertTrue(estado.body().contains("\"activo\":false"));
    }

    @Test
    void controlaSolapamientosAutorizacionYValidaciones() throws Exception {
        assertEquals(401, enviar("GET", "/api/horarios", null, null).statusCode());
        assertEquals(403, enviar("GET", "/api/horarios", null, TOKEN_ADMISION).statusCode());
        assertEquals(405, enviar("DELETE", "/api/horarios", null, TOKEN_ADMIN).statusCode());
        assertEquals(404, enviar("GET", "/api/horarios/HOR-999", null, TOKEN_ADMIN).statusCode());

        String horaInvertida = BODY_MANANA.replace("09:00", "15:00");
        assertEquals(400, enviar("POST", "/api/horarios", horaInvertida, TOKEN_ADMIN).statusCode());
        assertEquals(400, enviar("GET", "/api/horarios?diaSemana=NO_EXISTE", null, TOKEN_ADMIN).statusCode());

        assertEquals(201, enviar("POST", "/api/horarios", BODY_MANANA, TOKEN_ADMIN).statusCode());
        String solapado = BODY_MANANA.replace("09:00", "11:00").replace("13:00", "15:00");
        assertEquals(409, enviar("POST", "/api/horarios", solapado, TOKEN_ADMIN).statusCode());
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(ruta));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json; charset=utf-8")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(metodo, HttpRequest.BodyPublishers.noBody());
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
