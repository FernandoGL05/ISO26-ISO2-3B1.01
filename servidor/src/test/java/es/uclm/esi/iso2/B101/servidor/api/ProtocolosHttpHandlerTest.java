package es.uclm.esi.iso2.B101.servidor.api;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadPrueba;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoPrueba;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioProtocolosMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioTiposPruebaMemoria;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioProtocolos;
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

class ProtocolosHttpHandlerTest {

    private static final String TOKEN_ADMIN = "token-admin";
    private static final String TOKEN_PACIENTE = "token-paciente";

    private HttpServer servidor;
    private URI baseUri;
    private HttpClient http;
    private TipoPrueba tipoPrueba;

    @BeforeEach
    void setUp() throws IOException {
        RepositorioTiposPruebaMemoria repoTipos = new RepositorioTiposPruebaMemoria();
        ServicioTiposPrueba servicioTipos = new ServicioTiposPrueba(repoTipos);
        tipoPrueba = servicioTipos.crearTipoPrueba(
                "TAC Torácico", "TAC", "Tomógrafo", "Sala TAC-1",
                20, "Ayuno 4h", true, true,
                "Protección radiológica", PrioridadPrueba.PREFERENTE, false);

        ServicioProtocolos servicioProtocolos = new ServicioProtocolos(
                new RepositorioProtocolosMemoria(), repoTipos);
        AutorizadorAdministrador autorizador = cabecera -> switch (cabecera) {
            case "Bearer " + TOKEN_ADMIN -> Optional.of(
                    new AutorizadorAdministrador.IdentidadAdministrador(true, "admin-1"));
            case "Bearer " + TOKEN_PACIENTE -> Optional.of(
                    new AutorizadorAdministrador.IdentidadAdministrador(false, "pac-1"));
            default -> Optional.empty();
        };

        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ProtocolosHttpHandler.registrarEn(servidor, servicioProtocolos, autorizador);
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
    void cicloCompletoProtocolosConVersionado() throws Exception {
        String body = """
                {"codigo":"P-TAC-01","nombre":"Protocolo TAC Torácico","tipoPruebaId":"%s",\
                "descripcion":"Cortes helicoidales","pasosPreparacion":"Retirar objetos metálicos",\
                "requisitosSeguridad":"Verificar creatinina","usoContraste":true}""".formatted(tipoPrueba.getId());

        HttpResponse<String> creado = enviar("POST", "/api/protocolos", body, TOKEN_ADMIN);
        assertEquals(201, creado.statusCode());
        assertTrue(creado.body().contains("\"id\":\"PROT-1\""));
        assertTrue(creado.body().contains("\"version\":1"));

        HttpResponse<String> listado = enviar(
                "GET", "/api/protocolos?tipoPruebaId=" + tipoPrueba.getId(), null, TOKEN_ADMIN);
        assertEquals(200, listado.statusCode());
        assertTrue(listado.body().contains("\"P-TAC-01\""));

        HttpResponse<String> detalle = enviar("GET", "/api/protocolos/PROT-1", null, TOKEN_ADMIN);
        assertEquals(200, detalle.statusCode());

        String bodyV2 = body.replace("Protocolo TAC Torácico", "Protocolo TAC Torácico Baja Dosis");
        HttpResponse<String> actualizado = enviar("PUT", "/api/protocolos/PROT-1", bodyV2, TOKEN_ADMIN);
        assertEquals(200, actualizado.statusCode());
        assertTrue(actualizado.body().contains("\"version\":2"));

        HttpResponse<String> estado = enviar(
                "POST", "/api/protocolos/PROT-1/estado", "{\"activo\":false}", TOKEN_ADMIN);
        assertEquals(200, estado.statusCode());
        assertTrue(estado.body().contains("\"activo\":false"));
    }

    @Test
    void controlaPermisosYErroresDeProtocolos() throws Exception {
        assertEquals(401, enviar("GET", "/api/protocolos", null, null).statusCode());
        assertEquals(403, enviar("GET", "/api/protocolos", null, TOKEN_PACIENTE).statusCode());
        assertEquals(405, enviar("DELETE", "/api/protocolos", null, TOKEN_ADMIN).statusCode());
        assertEquals(404, enviar("GET", "/api/protocolos/PROT-999", null, TOKEN_ADMIN).statusCode());
        assertEquals(400, enviar("POST", "/api/protocolos", "{\"codigo\":\"\"}", TOKEN_ADMIN).statusCode());

        String tipoInexistente = """
                {"codigo":"P-ERR","nombre":"Error","tipoPruebaId":"TP-999",\
                "descripcion":"D","pasosPreparacion":"P","requisitosSeguridad":"S","usoContraste":false}""";
        assertEquals(404, enviar("POST", "/api/protocolos", tipoInexistente, TOKEN_ADMIN).statusCode());

        String valido = """
                {"codigo":"P-UNQ","nombre":"Válido","tipoPruebaId":"%s",\
                "descripcion":"D","pasosPreparacion":"P","requisitosSeguridad":"S","usoContraste":false}"""
                .formatted(tipoPrueba.getId());
        assertEquals(201, enviar("POST", "/api/protocolos", valido, TOKEN_ADMIN).statusCode());
        assertEquals(409, enviar("POST", "/api/protocolos", valido, TOKEN_ADMIN).statusCode());
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
