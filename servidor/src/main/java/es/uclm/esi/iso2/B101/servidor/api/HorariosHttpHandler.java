package es.uclm.esi.iso2.B101.servidor.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.HorarioOperativo;
import es.uclm.esi.iso2.B101.servidor.dominio.Rol;
import es.uclm.esi.iso2.B101.servidor.seguridad.AutenticadorSesion.IdentidadAutenticada;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioHorarios;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Handler HTTP/REST para la configuración de horarios operativos (CU22 / RF22).
 */
public final class HorariosHttpHandler implements HttpHandler {

    private static final String RUTA = "/api/horarios";
    private static final int MAX_CUERPO_BYTES = 16 * 1024;
    private static final Set<String> PARAMETROS_LISTA = Set.of("recursoId", "diaSemana");
    private static final System.Logger LOGGER = System.getLogger(HorariosHttpHandler.class.getName());

    private final ServicioHorarios servicio;
    private final AutorizadorHorarios autorizador;

    public HorariosHttpHandler(ServicioHorarios servicio, AutorizadorHorarios autorizador) {
        this.servicio = Objects.requireNonNull(servicio, "servicio no puede ser nulo");
        this.autorizador = Objects.requireNonNull(autorizador, "autorizador no puede ser nulo");
    }

    public static void registrarEn(
            HttpServer servidor,
            ServicioHorarios servicio,
            AutorizadorHorarios autorizador) {
        Objects.requireNonNull(servidor, "servidor no puede ser nulo")
                .createContext(RUTA, new HorariosHttpHandler(servicio, autorizador));
    }

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            procesar(intercambio);
        } catch (ErrorHttp error) {
            responderError(intercambio, error.estado, error.codigo, error.getMessage());
        } catch (IllegalArgumentException | DateTimeException error) {
            responderError(intercambio, 400, "SOLICITUD_INVALIDA", error.getMessage());
        } catch (NoSuchElementException error) {
            responderError(intercambio, 404, "NO_ENCONTRADO", error.getMessage());
        } catch (IllegalStateException error) {
            responderError(intercambio, 409, "CONFLICTO", error.getMessage());
        } catch (RuntimeException error) {
            LOGGER.log(System.Logger.Level.ERROR, "Error inesperado en CU22 horarios", error);
            responderError(intercambio, 500, "ERROR_INTERNO", "Error interno del servidor");
        } finally {
            intercambio.close();
        }
    }

    private void procesar(HttpExchange intercambio) throws IOException {
        autenticarAdministrador(intercambio);
        String ruta = intercambio.getRequestURI().getRawPath();
        String metodo = intercambio.getRequestMethod();
        String query = intercambio.getRequestURI().getRawQuery();

        if (RUTA.equals(ruta)) {
            switch (metodo) {
                case "GET" -> listar(intercambio, query);
                case "POST" -> crear(intercambio, query);
                default -> {
                    intercambio.getResponseHeaders().set("Allow", "GET, POST");
                    throw new ErrorHttp(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
                }
            }
            return;
        }

        if (!ruta.startsWith(RUTA + "/")) {
            throw new ErrorHttp(404, "NO_ENCONTRADO", "Ruta no encontrada");
        }
        String resto = ruta.substring(RUTA.length() + 1);
        if (resto.endsWith("/estado")) {
            String id = resto.substring(0, resto.length() - "/estado".length());
            if (id.isBlank() || id.contains("/")) {
                throw new ErrorHttp(404, "NO_ENCONTRADO", "Ruta no encontrada");
            }
            if (!"POST".equals(metodo)) {
                intercambio.getResponseHeaders().set("Allow", "POST");
                throw new ErrorHttp(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
            }
            if (query != null) {
                throw new IllegalArgumentException("No se admiten parámetros de consulta");
            }
            boolean activo = JsonHorarios.parsearEstado(leerCuerpoJson(intercambio));
            HorarioOperativo actualizado = servicio.cambiarEstado(id, activo);
            responderJson(intercambio, 200, JsonHorarios.serializar(actualizado));
            return;
        }

        if (resto.isBlank() || resto.contains("/")) {
            throw new ErrorHttp(404, "NO_ENCONTRADO", "Ruta no encontrada");
        }
        if (query != null) {
            throw new IllegalArgumentException("No se admiten parámetros de consulta");
        }
        switch (metodo) {
            case "GET" -> responderJson(intercambio, 200, JsonHorarios.serializar(servicio.obtenerPorId(resto)));
            case "PUT" -> {
                JsonHorarios.DatosHorario datos = JsonHorarios.parsear(leerCuerpoJson(intercambio));
                HorarioOperativo actualizado = servicio.actualizarHorario(
                        resto,
                        datos.recursoId(),
                        datos.tipoRecurso(),
                        datos.diaSemana(),
                        datos.horaInicio(),
                        datos.horaFin(),
                        datos.duracionHuecoMinutos());
                responderJson(intercambio, 200, JsonHorarios.serializar(actualizado));
            }
            default -> {
                intercambio.getResponseHeaders().set("Allow", "GET, PUT");
                throw new ErrorHttp(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
            }
        }
    }

    private void listar(HttpExchange intercambio, String query) throws IOException {
        Map<String, String> params = leerParametrosOpcionales(query);
        List<HorarioOperativo> horarios = params.containsKey("recursoId")
                ? servicio.listarPorRecurso(params.get("recursoId"))
                : servicio.listarTodos();
        if (params.containsKey("diaSemana")) {
            DayOfWeek dia = DayOfWeek.valueOf(params.get("diaSemana").trim().toUpperCase(Locale.ROOT));
            horarios = horarios.stream().filter(h -> h.getDiaSemana() == dia).toList();
        }
        responderJson(intercambio, 200, JsonHorarios.serializarLista(horarios));
    }

    private void crear(HttpExchange intercambio, String query) throws IOException {
        if (query != null) {
            throw new IllegalArgumentException("No se admiten parámetros de consulta");
        }
        JsonHorarios.DatosHorario datos = JsonHorarios.parsear(leerCuerpoJson(intercambio));
        HorarioOperativo creado = servicio.crearHorario(
                datos.recursoId(),
                datos.tipoRecurso(),
                datos.diaSemana(),
                datos.horaInicio(),
                datos.horaFin(),
                datos.duracionHuecoMinutos());
        responderJson(intercambio, 201, JsonHorarios.serializar(creado));
    }

    private void autenticarAdministrador(HttpExchange intercambio) {
        List<String> cabeceras = intercambio.getRequestHeaders().get("Authorization");
        if (cabeceras == null || cabeceras.size() != 1 || !esBearerValido(cabeceras.get(0))) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "Se requiere una sesión válida");
        }
        Optional<IdentidadAutenticada> identidadOpt = autorizador.autenticar(cabeceras.get(0));
        if (identidadOpt.isEmpty()) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "La sesión no es válida");
        }
        IdentidadAutenticada identidad = identidadOpt.orElseThrow();
        if (identidad.rol() != Rol.ADMINISTRADOR) {
            throw new ErrorHttp(403, "PROHIBIDO", "Se requiere el rol ADMINISTRADOR");
        }
    }

    private static boolean esBearerValido(String cabecera) {
        if (cabecera == null || cabecera.length() <= 7
                || !cabecera.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        String token = cabecera.substring(7);
        return !token.isBlank() && token.chars().noneMatch(Character::isWhitespace);
    }

    private static String leerCuerpoJson(HttpExchange intercambio) throws IOException {
        List<String> tipos = intercambio.getRequestHeaders().get("Content-Type");
        if (tipos == null || tipos.size() != 1 || !esTipoJson(tipos.get(0))) {
            throw new ErrorHttp(400, "SOLICITUD_INVALIDA", "El cuerpo debe utilizar application/json");
        }
        byte[] cuerpo = intercambio.getRequestBody().readNBytes(MAX_CUERPO_BYTES + 1);
        if (cuerpo.length > MAX_CUERPO_BYTES) {
            throw new ErrorHttp(400, "SOLICITUD_INVALIDA", "El cuerpo supera el tamaño permitido");
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(cuerpo))
                    .toString();
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("El cuerpo debe estar codificado en UTF-8", error);
        }
    }

    private static boolean esTipoJson(String valor) {
        int parametros = valor.indexOf(';');
        String tipo = parametros < 0 ? valor.trim() : valor.substring(0, parametros).trim();
        return "application/json".equalsIgnoreCase(tipo);
    }

    private static Map<String, String> leerParametrosOpcionales(String consulta) {
        Map<String, String> parametros = new HashMap<>();
        if (consulta == null || consulta.isBlank()) {
            return parametros;
        }
        for (String par : consulta.split("&", -1)) {
            int separador = par.indexOf('=');
            if (separador <= 0) {
                throw new IllegalArgumentException("Parámetro de consulta no válido");
            }
            String nombre = URLDecoder.decode(par.substring(0, separador), StandardCharsets.UTF_8);
            String valor = URLDecoder.decode(par.substring(separador + 1), StandardCharsets.UTF_8);
            if (!PARAMETROS_LISTA.contains(nombre) || valor.isBlank()
                    || parametros.putIfAbsent(nombre, valor) != null) {
                throw new IllegalArgumentException("Parámetros de consulta no válidos");
            }
        }
        return parametros;
    }

    private static void responderJson(HttpExchange intercambio, int estado, String json) throws IOException {
        byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        intercambio.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        intercambio.getResponseBody().write(cuerpo);
    }

    private static void responderError(HttpExchange intercambio, int estado, String codigo, String mensaje)
            throws IOException {
        responderJson(intercambio, estado, JsonHorarios.error(codigo, mensaje));
    }

    private static final class ErrorHttp extends RuntimeException {
        private final int estado;
        private final String codigo;

        private ErrorHttp(int estado, String codigo, String mensaje) {
            super(mensaje);
            this.estado = estado;
            this.codigo = codigo;
        }
    }
}
