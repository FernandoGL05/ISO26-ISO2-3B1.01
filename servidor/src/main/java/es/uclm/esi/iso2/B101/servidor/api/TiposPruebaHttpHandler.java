package es.uclm.esi.iso2.B101.servidor.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoPrueba;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioTiposPrueba;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Handler HTTP/REST para la configuración del catálogo de tipos de prueba (CU20 / RF20).
 */
public final class TiposPruebaHttpHandler implements HttpHandler {

    private static final String RUTA = "/api/tipos-prueba";
    private static final Set<String> PARAMETROS_LISTA = Set.of("soloActivos");
    private static final System.Logger LOGGER = System.getLogger(TiposPruebaHttpHandler.class.getName());

    private final ServicioTiposPrueba servicio;
    private final AutorizadorAdministrador autorizador;

    public TiposPruebaHttpHandler(ServicioTiposPrueba servicio, AutorizadorAdministrador autorizador) {
        this.servicio = Objects.requireNonNull(servicio, "servicio no puede ser nulo");
        this.autorizador = Objects.requireNonNull(autorizador, "autorizador no puede ser nulo");
    }

    public static void registrarEn(
            HttpServer servidor,
            ServicioTiposPrueba servicio,
            AutorizadorAdministrador autorizador) {
        Objects.requireNonNull(servidor, "servidor no puede ser nulo")
                .createContext(RUTA, new TiposPruebaHttpHandler(servicio, autorizador));
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
            LOGGER.log(System.Logger.Level.ERROR, "Error inesperado en CU20 tipos-prueba", error);
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
            boolean activo = JsonTiposPrueba.parsearEstado(HttpAdminSupport.leerCuerpoJson(intercambio));
            TipoPrueba actualizado = servicio.cambiarEstado(id, activo);
            HttpAdminSupport.responderJson(intercambio, 200, JsonTiposPrueba.serializar(actualizado));
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
                    intercambio, 200, JsonTiposPrueba.serializar(servicio.obtenerPorId(resto)));
            case "PUT" -> {
                JsonTiposPrueba.DatosTipoPrueba datos =
                        JsonTiposPrueba.parsear(HttpAdminSupport.leerCuerpoJson(intercambio));
                TipoPrueba actualizado = servicio.actualizarTipoPrueba(
                        resto,
                        datos.nombre(),
                        datos.modalidad(),
                        datos.equipoRequerido(),
                        datos.salaRequerida(),
                        datos.duracionEstimadaMinutos(),
                        datos.preparacionPrevia(),
                        datos.requiereContraste(),
                        datos.requiereConsentimientoInformado(),
                        datos.requisitosSeguridad(),
                        datos.prioridad(),
                        datos.admiteSeguimientoPeriodico());
                HttpAdminSupport.responderJson(intercambio, 200, JsonTiposPrueba.serializar(actualizado));
            }
            default -> {
                intercambio.getResponseHeaders().set("Allow", "GET, PUT");
                throw new HttpAdminSupport.ErrorHttp(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
            }
        }
    }

    private void listar(HttpExchange intercambio, String query) throws IOException {
        Map<String, String> params = HttpAdminSupport.leerParametrosOpcionales(query, PARAMETROS_LISTA);
        boolean soloActivos = false;
        if (params.containsKey("soloActivos")) {
            String valor = params.get("soloActivos");
            if (!"true".equalsIgnoreCase(valor) && !"false".equalsIgnoreCase(valor)) {
                throw new IllegalArgumentException("soloActivos debe ser true o false");
            }
            soloActivos = Boolean.parseBoolean(valor);
        }
        List<TipoPrueba> tipos = soloActivos ? servicio.listarActivos() : servicio.listarTodos();
        HttpAdminSupport.responderJson(intercambio, 200, JsonTiposPrueba.serializarLista(tipos));
    }

    private void crear(HttpExchange intercambio, String query) throws IOException {
        if (query != null) {
            throw new IllegalArgumentException("No se admiten parámetros de consulta");
        }
        JsonTiposPrueba.DatosTipoPrueba datos =
                JsonTiposPrueba.parsear(HttpAdminSupport.leerCuerpoJson(intercambio));
        TipoPrueba creado = servicio.crearTipoPrueba(
                datos.nombre(),
                datos.modalidad(),
                datos.equipoRequerido(),
                datos.salaRequerida(),
                datos.duracionEstimadaMinutos(),
                datos.preparacionPrevia(),
                datos.requiereContraste(),
                datos.requiereConsentimientoInformado(),
                datos.requisitosSeguridad(),
                datos.prioridad(),
                datos.admiteSeguimientoPeriodico());
        HttpAdminSupport.responderJson(intercambio, 201, JsonTiposPrueba.serializar(creado));
    }
}
