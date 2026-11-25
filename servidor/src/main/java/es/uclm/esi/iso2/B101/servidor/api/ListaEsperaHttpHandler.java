package es.uclm.esi.iso2.B101.servidor.api;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadPrueba;
import es.uclm.esi.iso2.B101.servidor.dominio.Rol;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAsignacionListaEspera;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioListaEspera;

public final class ListaEsperaHttpHandler implements HttpHandler {
    private static final String RUTA_BASE = "/api/lista-espera";
    private static final int MAX_CUERPO = 16 * 1024;

    private final ServicioListaEspera gestion;
    private final ServicioAsignacionListaEspera asignacion;
    private final AutorizadorListaEspera autorizador;

    public ListaEsperaHttpHandler(
            ServicioListaEspera gestion,
            ServicioAsignacionListaEspera asignacion,
            AutorizadorListaEspera autorizador) {
        this.gestion = Objects.requireNonNull(gestion);
        this.asignacion = Objects.requireNonNull(asignacion);
        this.autorizador = Objects.requireNonNull(autorizador);
    }

    public static void registrarEn(
            HttpServer servidor,
            ServicioListaEspera gestion,
            ServicioAsignacionListaEspera asignacion,
            AutorizadorListaEspera autorizador) {
        Objects.requireNonNull(servidor).createContext(
                RUTA_BASE,
                new ListaEsperaHttpHandler(gestion, asignacion, autorizador));
    }

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            procesar(intercambio);
        } catch (ErrorHttp error) {
            responderError(
                    intercambio, error.estado, error.codigo, error.getMessage());
        } catch (IllegalArgumentException | DateTimeException error) {
            responderError(
                    intercambio, 400, "DATOS_INVALIDOS",
                    "Los datos de la peticion no son validos");
        } catch (NoSuchElementException error) {
            responderError(
                    intercambio, 404, "NO_ENCONTRADO",
                    "No existe la entrada o no hay candidatos pendientes");
        } catch (IllegalStateException error) {
            responderError(
                    intercambio, 409, "CONFLICTO",
                    "La operacion entra en conflicto con el estado actual "
                    + "o no se puede comprobar la disponibilidad");
        } catch (RuntimeException error) {
            responderError(
                    intercambio, 500, "ERROR_INTERNO",
                    "No se ha podido completar la operacion");
        } finally {
            intercambio.close();
        }
    }

    private void procesar(HttpExchange intercambio) throws IOException {
        autenticar(intercambio);

        String ruta = intercambio.getRequestURI().getRawPath();

        if (RUTA_BASE.equals(ruta)) {
            procesarColeccion(intercambio);
            return;
        }

        if (!ruta.startsWith(RUTA_BASE + "/")) {
            throw noEncontrado();
        }

        String[] partes = ruta.substring(RUTA_BASE.length() + 1)
                .split("/", -1);

        exigirSinConsulta(intercambio);

        if (partes.length == 1 && "asignaciones".equals(partes[0])) {
            exigirMetodo(intercambio, "POST");
            asignar(intercambio);
            return;
        }

        if (partes.length == 1 && !partes[0].isEmpty()) {
            exigirMetodo(intercambio, "GET");

            responder(intercambio, 200, JsonListaEspera.entrada(
                    gestion.consultarEntrada(identificador(partes[0]))));
            return;
        }

        if (partes.length == 2 && !partes[0].isEmpty()) {
            String entradaId = identificador(partes[0]);

            if ("prioridad".equals(partes[1])) {
                exigirMetodo(intercambio, "PUT");
                cambiarPrioridad(intercambio, entradaId);
                return;
            }

            if ("cancelacion".equals(partes[1])) {
                exigirMetodo(intercambio, "POST");
                exigirSinCuerpo(intercambio);

                responder(intercambio, 200, JsonListaEspera.entrada(
                        gestion.cancelar(entradaId)));
                return;
            }
        }

        throw noEncontrado();
    }

    private void autenticar(HttpExchange intercambio) {
        var cabeceras = intercambio.getRequestHeaders()
                .get("Authorization");

        if (cabeceras == null || cabeceras.size() != 1) {
            throw noAutenticado();
        }

        var identidad = autorizador.autenticar(cabeceras.get(0))
                .orElseThrow(ListaEsperaHttpHandler::noAutenticado);

        if (identidad.rol() != Rol.ADMISION) {
            throw new ErrorHttp(
                    403, "SIN_PERMISO",
                    "Se requiere el rol de admision");
        }
    }

    private void procesarColeccion(HttpExchange intercambio)
            throws IOException {
        String metodo = intercambio.getRequestMethod();

        if ("GET".equals(metodo)) {
            consultar(intercambio);
            return;
        }

        if ("POST".equals(metodo)) {
            exigirSinConsulta(intercambio);

            Map<String, Object> campos = leer(
                    intercambio,
                    "pacienteId", "medicoId", "tipoPrueba", "prioridad");

            var entrada = gestion.inscribir(
                    texto(campos, "pacienteId"),
                    texto(campos, "medicoId"),
                    texto(campos, "tipoPrueba"),
                    PrioridadPrueba.valueOf(texto(campos, "prioridad")));

            responder(intercambio, 201, JsonListaEspera.entrada(entrada));
            return;
        }

        intercambio.getResponseHeaders().set("Allow", "GET, POST");
        throw metodoNoPermitido();
    }

    private void consultar(HttpExchange intercambio) throws IOException {
        Map<String, String> consulta = leerConsulta(intercambio);
        String respuesta;

        if (consulta.isEmpty()) {
            respuesta = JsonListaEspera.entradas(
                    gestion.consultarPendientes());
        } else if (consulta.keySet().equals(Set.of("pacienteId"))) {
            respuesta = JsonListaEspera.entradas(
                    gestion.consultarPorPaciente(consulta.get("pacienteId")));
        } else if (consulta.keySet().equals(
                Set.of("medicoId", "tipoPrueba"))) {
            respuesta = JsonListaEspera.entradas(
                    gestion.consultarPendientes(
                            consulta.get("medicoId"),
                            consulta.get("tipoPrueba")));
        } else {
            throw new IllegalArgumentException(
                    "Los filtros de consulta no son validos");
        }

        responder(intercambio, 200, respuesta);
    }

    private void cambiarPrioridad(
            HttpExchange intercambio,
            String entradaId) throws IOException {
        Map<String, Object> campos = leer(intercambio, "prioridad");

        var entrada = gestion.cambiarPrioridad(
                entradaId,
                PrioridadPrueba.valueOf(texto(campos, "prioridad")));

        responder(intercambio, 200, JsonListaEspera.entrada(entrada));
    }

    private void asignar(HttpExchange intercambio) throws IOException {
        Map<String, Object> campos = leer(
                intercambio,
                "medicoId", "tipoPrueba", "fechaHora", "duracionMinutos");

        var resultado = asignacion.asignarSiguiente(
                texto(campos, "medicoId"),
                texto(campos, "tipoPrueba"),
                LocalDateTime.parse(texto(campos, "fechaHora")),
                ParserJsonListaEspera.entero(campos, "duracionMinutos"));

        responder(intercambio, 201, JsonListaEspera.asignacion(resultado));
    }

    private Map<String, Object> leer(
            HttpExchange intercambio,
            String... campos) throws IOException {
        String tipo = intercambio.getRequestHeaders()
                .getFirst("Content-Type");

        if (tipo == null
                || !tipo.split(";", 2)[0].trim()
                        .equalsIgnoreCase("application/json")) {
            throw new ErrorHttp(
                    415, "TIPO_NO_ADMITIDO",
                    "El cuerpo debe enviarse como application/json");
        }

        byte[] bytes = intercambio.getRequestBody()
                .readNBytes(MAX_CUERPO + 1);

        if (bytes.length > MAX_CUERPO) {
            throw new ErrorHttp(
                    413, "CUERPO_DEMASIADO_GRANDE",
                    "El cuerpo supera el limite de 16 KiB");
        }

        String cuerpo;
        try {
            cuerpo = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException(
                    "El cuerpo debe utilizar UTF-8", error);
        }

        return ParserJsonListaEspera.leer(cuerpo, campos);
    }

    private Map<String, String> leerConsulta(HttpExchange intercambio) {
        Map<String, String> campos = new LinkedHashMap<>();
        String consulta = intercambio.getRequestURI().getRawQuery();

        if (consulta == null) {
            return campos;
        }

        if (consulta.isEmpty()) {
            throw new IllegalArgumentException("Consulta vacia");
        }

        for (String parametro : consulta.split("&", -1)) {
            String[] partes = parametro.split("=", 2);

            if (partes.length != 2) {
                throw new IllegalArgumentException("Parametro invalido");
            }

            String nombre = decodificar(partes[0]);
            String valor = decodificar(partes[1]);

            if (nombre.isBlank() || valor.isBlank()
                    || campos.putIfAbsent(nombre, valor) != null) {
                throw new IllegalArgumentException(
                        "Parametro vacio o repetido");
            }
        }

        return campos;
    }

    private static String texto(
            Map<String, Object> campos,
            String nombre) {
        return ParserJsonListaEspera.texto(campos, nombre);
    }

    private static String identificador(String valor) {
        String resultado = decodificar(valor.replace("+", "%2B"));

        if (resultado.isBlank()
                || resultado.contains("/")
                || resultado.contains("\\")
                || resultado.equals(".")
                || resultado.equals("..")
                || resultado.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Identificador invalido");
        }

        return resultado;
    }

    private static String decodificar(String valor) {
        return URLDecoder.decode(valor, StandardCharsets.UTF_8);
    }

    private static void exigirSinConsulta(HttpExchange intercambio) {
        if (intercambio.getRequestURI().getRawQuery() != null) {
            throw new IllegalArgumentException(
                    "Esta operacion no admite parametros de consulta");
        }
    }

    private static void exigirSinCuerpo(HttpExchange intercambio)
            throws IOException {
        if (intercambio.getRequestBody().read() != -1) {
            throw new IllegalArgumentException(
                    "Esta operacion no admite cuerpo");
        }
    }

    private static void exigirMetodo(
            HttpExchange intercambio,
            String metodo) {
        if (!metodo.equals(intercambio.getRequestMethod())) {
            intercambio.getResponseHeaders().set("Allow", metodo);
            throw metodoNoPermitido();
        }
    }

    private static void responderError(
            HttpExchange intercambio,
            int estado,
            String codigo,
            String mensaje) throws IOException {
        if (estado == 401) {
            intercambio.getResponseHeaders().set(
                    "WWW-Authenticate", "Bearer");
        }

        responder(
                intercambio, estado,
                JsonListaEspera.error(codigo, mensaje));
    }

    private static void responder(
            HttpExchange intercambio,
            int estado,
            String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        intercambio.getResponseHeaders().set(
                "Content-Type", "application/json; charset=utf-8");
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        intercambio.getResponseHeaders().set(
                "X-Content-Type-Options", "nosniff");

        intercambio.sendResponseHeaders(estado, bytes.length);

        try (var salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    private static ErrorHttp noAutenticado() {
        return new ErrorHttp(
                401, "NO_AUTENTICADO",
                "Se requiere una sesion valida");
    }

    private static ErrorHttp noEncontrado() {
        return new ErrorHttp(
                404, "RUTA_NO_ENCONTRADA",
                "La ruta solicitada no existe");
    }

    private static ErrorHttp metodoNoPermitido() {
        return new ErrorHttp(
                405, "METODO_NO_PERMITIDO",
                "El metodo HTTP no esta permitido");
    }

    private static final class ErrorHttp extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final int estado;
        private final String codigo;

        private ErrorHttp(int estado, String codigo, String mensaje) {
            super(mensaje);
            this.estado = estado;
            this.codigo = codigo;
        }
    }
}