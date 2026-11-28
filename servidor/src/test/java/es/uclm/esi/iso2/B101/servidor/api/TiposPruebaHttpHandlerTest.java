package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.Rol;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioTiposPruebaMemoria;
import es.uclm.esi.iso2.B101.servidor.seguridad.AutenticadorSesion.IdentidadAutenticada;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioTiposPrueba;
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

class TiposPruebaHttpHandlerTest {

    private static final String TOKEN_ADMIN = "token-admin";
    private static final String TOKEN_MEDICO = "token-medico";
    private static final String BODY_VALIDO = """
            {"nombre":"RM Lumbar","modalidad":"RM","equipoRequerido":"RM 1.5T","salaRequerida":"Sala RM-2",\
            "duracionEstimadaMinutos":35,"preparacionPrevia":"Retirar objetos metálicos","requiereContraste":true,\
            "requiereConsentimientoInformado":true,"requisitosSeguridad":"Sin marcapasos",\
            "prioridad":"PREFERENTE","admiteSeguimientoPeriodico":true}""";

    private HttpServer servidor;
    private URI baseUri;
    private HttpClient http;

    @BeforeEach
    void setUp() throws IOException {
        ServicioTiposPrueba servicio = new ServicioTiposPrueba(new RepositorioTiposPruebaMemoria());
        AutorizadorTiposPrueba autorizador = cabecera -> switch (cabecera) {
            case "Bearer " + TOKEN_ADMIN -> Optional.of(new IdentidadAutenticada("USR-004", Rol.ADMINISTRADOR));
            case "Bearer " + TOKEN_MEDICO -> Optional.of(new IdentidadAutenticada("USR-002", Rol.MEDICO));
            default -> Optional.empty();
        };
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        TiposPruebaHttpHandler.registrarEn(servidor, servicio, autorizador);
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
    void cicloCompletoCrearListarObtenerActualizarYCambiarEstado() throws Exception {
        HttpResponse<String> creado = enviar("POST", "/api/tipos-prueba", BODY_VALIDO, TOKEN_ADMIN);
        assertEquals(201, creado.statusCode());
        assertTrue(creado.body().contains("\"id\":\"TP-1\""));
        assertTrue(creado.body().contains("\"activo\":true"));

        HttpResponse<String> listado = enviar("GET", "/api/tipos-prueba?soloActivos=true", null, TOKEN_ADMIN);
        assertEquals(200, listado.statusCode());
        assertTrue(listado.body().contains("\"RM Lumbar\""));

        HttpResponse<String> detalle = enviar("GET", "/api/tipos-prueba/TP-1", null, TOKEN_ADMIN);
        assertEquals(200, detalle.statusCode());
        assertTrue(detalle.body().contains("\"modalidad\":\"RM\""));

        String bodyActualizado = BODY_VALIDO.replace("RM Lumbar", "RM Cervical");
        HttpResponse<String> actualizado = enviar("PUT", "/api/tipos-prueba/TP-1", bodyActualizado, TOKEN_ADMIN);
        assertEquals(200, actualizado.statusCode());
        assertTrue(actualizado.body().contains("RM Cervical"));

        HttpResponse<String> desactivado = enviar(
                "POST", "/api/tipos-prueba/TP-1/estado", "{\"activo\":false}", TOKEN_ADMIN);
        assertEquals(200, desactivado.statusCode());
        assertTrue(desactivado.body().contains("\"activo\":false"));

        HttpResponse<String> soloActivos = enviar("GET", "/api/tipos-prueba?soloActivos=true", null, TOKEN_ADMIN);
        assertEquals("[]", soloActivos.body());
    }

    @Test
    void exigeRolAdministradorYValidaErroresHttp() throws Exception {
        assertEquals(401, enviar("GET", "/api/tipos-prueba", null, null).statusCode());
        assertEquals(403, enviar("GET", "/api/tipos-prueba", null, TOKEN_MEDICO).statusCode());
        assertEquals(405, enviar("DELETE", "/api/tipos-prueba", null, TOKEN_ADMIN).statusCode());
        assertEquals(405, enviar("GET", "/api/tipos-prueba/TP-1/estado", null, TOKEN_ADMIN).statusCode());
        assertEquals(404, enviar("GET", "/api/tipos-prueba/TP-999", null, TOKEN_ADMIN).statusCode());
        assertEquals(404, enviar("GET", "/api/tipos-prueba/a/b", null, TOKEN_ADMIN).statusCode());
        assertEquals(400, enviar("GET", "/api/tipos-prueba?soloActivos=quizas", null, TOKEN_ADMIN).statusCode());

        String sinConsentimiento = BODY_VALIDO.replace(
                "\"requiereConsentimientoInformado\":true", "\"requiereConsentimientoInformado\":false");
        assertEquals(400, enviar("POST", "/api/tipos-prueba", sinConsentimiento, TOKEN_ADMIN).statusCode());

        assertEquals(201, enviar("POST", "/api/tipos-prueba", BODY_VALIDO, TOKEN_ADMIN).statusCode());
        assertEquals(409, enviar("POST", "/api/tipos-prueba", BODY_VALIDO, TOKEN_ADMIN).statusCode());
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
