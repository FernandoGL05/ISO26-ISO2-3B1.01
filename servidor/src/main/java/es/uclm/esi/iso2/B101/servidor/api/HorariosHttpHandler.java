package es.uclm.esi.iso2.B101.servidor.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.HorarioOperativo;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioHorarios;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Handler HTTP/REST para la configuración de horarios operativos (CU22 / RF22).
 */
public final class HorariosHttpHandler implements HttpHandler {

    private static final String RUTA = "/api/horarios";
    private static final Set<String> PARAMETROS_LISTA = Set.of("recursoId", "diaSemana");
    private static final System.Logger LOGGER = System.getLogger(HorariosHttpHandler.class.getName());

    private final ServicioHorarios servicio;
    private final AutorizadorAdministrador autorizador;

    public HorariosHttpHandler(ServicioHorarios servicio, AutorizadorAdministrador autorizador) {
        this.servicio = Objects.requireNonNull(servicio, "servicio no puede ser nulo");
        this.autorizador = Objects.requireNonNull(autorizador, "autorizador no puede ser nulo");
    }

    public static void registrarEn(
            HttpServer servidor,
            ServicioHorarios servicio,
            AutorizadorAdministrador autorizador) {
        Objects.requireNonNull(servidor, "servidor no puede ser nulo")
                .createContext(RUTA, new HorariosHttpHandler(servicio, autorizador));
    }

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            procesar(intercambio);
        } catch (HttpAdminSupport.ErrorHttp error) {
            HttpAdminSupport.responderError(intercambio, error.estado, error.codigo, error.getMessage());
        } catch (IllegalArgumentException | DateTimeException error) {
            HttpAdminSupport.responderError(intercambio, 400, "SOLICITUD_INVALIDA", error.getMessage());
        } catch (NoSuchElementException error) {
            HttpAdminSupport.responderError(intercambio, 404, "NO_ENCONTRADO", error.getMessage());
        } catch (IllegalStateException error) {
            HttpAdminSupport.responderError(intercambio, 409, "CONFLICTO", error.getMessage());
        } catch (RuntimeException error) {
            LOGGER.log(System.Logger.Level.ERROR, "Error inesperado en CU22 horarios", error);
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
            boolean activo = JsonHorarios.parsearEstado(HttpAdminSupport.leerCuerpoJson(intercambio));
            HorarioOperativo actualizado = servicio.cambiarEstado(id, activo);
            HttpAdminSupport.responderJson(intercambio, 200, JsonHorarios.serializar(actualizado));
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
                    intercambio, 200, JsonHorarios.serializar(servicio.obtenerPorId(resto)));
            case "PUT" -> {
                JsonHorarios.DatosHorario datos =
                        JsonHorarios.parsear(HttpAdminSupport.leerCuerpoJson(intercambio));
                HorarioOperativo actualizado = servicio.actualizarHorario(
                        resto,
                        datos.recursoId(),
                        datos.tipoRecurso(),
                        datos.diaSemana(),
                        datos.horaInicio(),
                        datos.horaFin(),
                        datos.duracionHuecoMinutos());
                HttpAdminSupport.responderJson(intercambio, 200, JsonHorarios.serializar(actualizado));
            }
            default -> {
                intercambio.getResponseHeaders().set("Allow", "GET, PUT");
                throw new HttpAdminSupport.ErrorHttp(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
            }
        }
    }

    private void listar(HttpExchange intercambio, String query) throws IOException {
        Map<String, String> params = HttpAdminSupport.leerParametrosOpcionales(query, PARAMETROS_LISTA);
        List<HorarioOperativo> horarios = params.containsKey("recursoId")
                ? servicio.listarPorRecurso(params.get("recursoId"))
                : servicio.listarTodos();
        if (params.containsKey("diaSemana")) {
            DayOfWeek dia = DayOfWeek.valueOf(params.get("diaSemana").trim().toUpperCase(Locale.ROOT));
            horarios = horarios.stream().filter(h -> h.getDiaSemana() == dia).toList();
        }
        HttpAdminSupport.responderJson(intercambio, 200, JsonHorarios.serializarLista(horarios));
    }

    private void crear(HttpExchange intercambio, String query) throws IOException {
        if (query != null) {
            throw new IllegalArgumentException("No se admiten parámetros de consulta");
        }
        JsonHorarios.DatosHorario datos =
                JsonHorarios.parsear(HttpAdminSupport.leerCuerpoJson(intercambio));
        HorarioOperativo creado = servicio.crearHorario(
                datos.recursoId(),
                datos.tipoRecurso(),
                datos.diaSemana(),
                datos.horaInicio(),
                datos.horaFin(),
                datos.duracionHuecoMinutos());
        HttpAdminSupport.responderJson(intercambio, 201, JsonHorarios.serializar(creado));
    }
}
