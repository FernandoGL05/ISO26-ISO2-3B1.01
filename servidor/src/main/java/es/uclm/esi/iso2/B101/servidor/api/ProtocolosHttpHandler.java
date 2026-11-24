package es.uclm.esi.iso2.B101.servidor.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.Protocolo;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioProtocolos;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Handler HTTP/REST para la configuración de protocolos clínicos/técnicos (CU21 / RF21).
 */
public final class ProtocolosHttpHandler implements HttpHandler {

    private static final String RUTA = "/api/protocolos";
    private static final Set<String> PARAMETROS_LISTA = Set.of("tipoPruebaId");
    private static final System.Logger LOGGER = System.getLogger(ProtocolosHttpHandler.class.getName());

    private final ServicioProtocolos servicio;
    private final AutorizadorAdministrador autorizador;

    public ProtocolosHttpHandler(ServicioProtocolos servicio, AutorizadorAdministrador autorizador) {
        this.servicio = Objects.requireNonNull(servicio, "servicio no puede ser nulo");
        this.autorizador = Objects.requireNonNull(autorizador, "autorizador no puede ser nulo");
    }

    public static void registrarEn(
            HttpServer servidor,
            ServicioProtocolos servicio,
            AutorizadorAdministrador autorizador) {
        Objects.requireNonNull(servidor, "servidor no puede ser nulo")
                .createContext(RUTA, new ProtocolosHttpHandler(servicio, autorizador));
    }

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            procesar(intercambio);
        } catch (HttpAdminSupport.ErrorHttp error) {
            HttpAdminSupport.responderError(intercambio, error.estado, error.codigo, error.getMessage());
        } catch (IllegalArgumentException error) {
            HttpAdminSupport.responderError(intercambio, 400, "SOLICITUD_INVALIDA", error.getMessage());
        } catch (NoSuchElementException error) {
            HttpAdminSupport.responderError(intercambio, 404, "NO_ENCONTRADO", error.getMessage());
        } catch (IllegalStateException error) {
            HttpAdminSupport.responderError(intercambio, 409, "CONFLICTO", error.getMessage());
        } catch (RuntimeException error) {
            LOGGER.log(System.Logger.Level.ERROR, "Error inesperado en CU21 protocolos", error);
            HttpAdminSupport.responderError(intercambio, 500, "ERROR_INTERNO", "Error interno del servidor");
        } finally {
            intercambio.close();
        }
    }

    private void procesar(HttpExchange intercambio) throws IOException {
        HttpAdminSupport.autenticarAdministrador(intercambio, autorizador);
        String ruta = intercambio.getRequestURI().getRawPath();
        String metodo = intercambio.getRequestMethod();
        String query = intercambio.getRequestURI().getRawQuery();

        if (RUTA.equals(ruta)) {
            switch (metodo) {
                case "GET" -> listar(intercambio, query);
                case "POST" -> crear(intercambio, query);
                default -> {
                    intercambio.getResponseHeaders().set("Allow", "GET, POST");
                    throw new HttpAdminSupport.ErrorHttp(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
                }
            }
            return;
        }

        if (!ruta.startsWith(RUTA + "/")) {
            throw new HttpAdminSupport.ErrorHttp(404, "NO_ENCONTRADO", "Ruta no encontrada");
        }
        String resto = ruta.substring(RUTA.length() + 1);
        if (resto.endsWith("/estado")) {
            String id = resto.substring(0, resto.length() - "/estado".length());
            if (id.isBlank() || id.contains("/")) {
                throw new HttpAdminSupport.ErrorHttp(404, "NO_ENCONTRADO", "Ruta no encontrada");
            }
            if (!"POST".equals(metodo)) {
                intercambio.getResponseHeaders().set("Allow", "POST");
                throw new HttpAdminSupport.ErrorHttp(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
            }
            if (query != null) {
                throw new IllegalArgumentException("No se admiten parámetros de consulta");
            }
            boolean activo = JsonProtocolos.parsearEstado(HttpAdminSupport.leerCuerpoJson(intercambio));
            Protocolo actualizado = servicio.cambiarEstado(id, activo);
            HttpAdminSupport.responderJson(intercambio, 200, JsonProtocolos.serializar(actualizado));
            return;
        }

        if (resto.isBlank() || resto.contains("/")) {
            throw new HttpAdminSupport.ErrorHttp(404, "NO_ENCONTRADO", "Ruta no encontrada");
        }
        if (query != null) {
            throw new IllegalArgumentException("No se admiten parámetros de consulta");
        }
        switch (metodo) {
            case "GET" -> HttpAdminSupport.responderJson(
                    intercambio, 200, JsonProtocolos.serializar(servicio.obtenerPorId(resto)));
            case "PUT" -> {
                JsonProtocolos.DatosProtocolo datos =
                        JsonProtocolos.parsear(HttpAdminSupport.leerCuerpoJson(intercambio));
                Protocolo actualizado = servicio.actualizarProtocolo(
                        resto,
                        datos.codigo(),
                        datos.nombre(),
                        datos.tipoPruebaId(),
                        datos.descripcion(),
                        datos.pasosPreparacion(),
                        datos.requisitosSeguridad(),
                        datos.usoContraste());
                HttpAdminSupport.responderJson(intercambio, 200, JsonProtocolos.serializar(actualizado));
            }
            default -> {
                intercambio.getResponseHeaders().set("Allow", "GET, PUT");
                throw new HttpAdminSupport.ErrorHttp(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
            }
        }
    }

    private void listar(HttpExchange intercambio, String query) throws IOException {
        Map<String, String> params = HttpAdminSupport.leerParametrosOpcionales(query, PARAMETROS_LISTA);
        List<Protocolo> protocolos = params.containsKey("tipoPruebaId")
                ? servicio.listarPorTipoPrueba(params.get("tipoPruebaId"))
                : servicio.listarTodos();
        HttpAdminSupport.responderJson(intercambio, 200, JsonProtocolos.serializarLista(protocolos));
    }

    private void crear(HttpExchange intercambio, String query) throws IOException {
        if (query != null) {
            throw new IllegalArgumentException("No se admiten parámetros de consulta");
        }
        JsonProtocolos.DatosProtocolo datos =
                JsonProtocolos.parsear(HttpAdminSupport.leerCuerpoJson(intercambio));
        Protocolo creado = servicio.crearProtocolo(
                datos.codigo(),
                datos.nombre(),
                datos.tipoPruebaId(),
                datos.descripcion(),
                datos.pasosPreparacion(),
                datos.requisitosSeguridad(),
                datos.usoContraste());
        HttpAdminSupport.responderJson(intercambio, 201, JsonProtocolos.serializar(creado));
    }
}
