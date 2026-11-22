package es.uclm.esi.iso2.B101.servidor.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.Incidencia;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioRealizacionPruebas;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RealizacionPruebasHttpHandler implements HttpHandler {
    private static final String RUTA = "/api/realizaciones-prueba";
    private static final int MAX_CUERPO_BYTES = 16 * 1024;
    private static final System.Logger LOGGER = System.getLogger(RealizacionPruebasHttpHandler.class.getName());

    private final ServicioRealizacionPruebas servicio;
    private final AutorizadorPrescripcionesDiagnosticas autorizador;

    public RealizacionPruebasHttpHandler(
            ServicioRealizacionPruebas servicio, AutorizadorPrescripcionesDiagnosticas autorizador) {
        this.servicio = Objects.requireNonNull(servicio, "servicio no puede ser nulo");
        this.autorizador = Objects.requireNonNull(autorizador, "autorizador no puede ser nulo");
    }

    public static void registrarEn(
            HttpServer servidor,
            ServicioRealizacionPruebas servicio,
            AutorizadorPrescripcionesDiagnosticas autorizador) {
        Objects.requireNonNull(servidor, "servidor no puede ser nulo")
                .createContext(RUTA, new RealizacionPruebasHttpHandler(servicio, autorizador));
    }

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            procesar(intercambio);
        } catch (ErrorHttp error) {
            responderError(intercambio, error.estado, error.codigo, error.getMessage());
        } catch (IllegalArgumentException error) {
            responderError(intercambio, 400, "SOLICITUD_INVALIDA", "La solicitud contiene datos inválidos");
        } catch (java.util.NoSuchElementException error) {
            responderError(intercambio, 404, "NO_ENCONTRADO", "Cita no encontrada");
        } catch (IllegalStateException error) {
            responderError(intercambio, 409, "ESTADO_INCOMPATIBLE", error.getMessage());
        } catch (RuntimeException error) {
            LOGGER.log(System.Logger.Level.ERROR, "Error inesperado registrando resultado de realización", error);
            responderError(intercambio, 500, "ERROR_INTERNO", "No se pudo registrar el resultado");
        } finally {
            intercambio.close();
        }
    }

    private void procesar(HttpExchange intercambio) throws IOException {
        if (!RUTA.equals(intercambio.getRequestURI().getRawPath())) {
            throw new ErrorHttp(404, "NO_ENCONTRADO", "Ruta no encontrada");
        }
        if (!"POST".equals(intercambio.getRequestMethod())) {
            intercambio.getResponseHeaders().set("Allow", "POST");
            throw new ErrorHttp(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
        }
        if (intercambio.getRequestURI().getRawQuery() != null) {
            throw new ErrorHttp(400, "SOLICITUD_INVALIDA", "No se admiten parámetros de consulta");
        }
        String medicoId = autenticarMedico(intercambio);
        JsonRealizacionPruebas.DatosRegistro datos = JsonRealizacionPruebas.parsear(leerCuerpo(intercambio));
        switch (datos.resultado()) {
            case "REALIZADA" -> {
                if (datos.descripcion() != null) {
                    throw new IllegalArgumentException("descripcion solo aplica al resultado INCIDENCIA");
                }
                responderJson(intercambio, 201, JsonRealizacionPruebas.prueba(
                        servicio.registrarRealizacion(medicoId, datos.citaId())));
            }
            case "INCIDENCIA" -> {
                if (datos.descripcion() == null || datos.descripcion().isBlank()) {
                    throw new IllegalArgumentException("descripcion es obligatoria para una incidencia");
                }
                Incidencia incidencia = servicio.registrarIncidencia(medicoId, datos.citaId(), datos.descripcion());
                responderJson(intercambio, 201, JsonIncidencias.serializar(incidencia));
            }
            default -> throw new IllegalArgumentException("resultado debe ser REALIZADA o INCIDENCIA");
        }
    }

    private String autenticarMedico(HttpExchange intercambio) {
        List<String> cabeceras = intercambio.getRequestHeaders().get("Authorization");
        if (cabeceras == null || cabeceras.size() != 1 || !esBearerValido(cabeceras.get(0))) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "Se requiere una sesión válida");
        }
        Optional<AutorizadorPrescripcionesDiagnosticas.IdentidadMedico> identidad =
                autorizador.autenticar(cabeceras.get(0));
        if (identidad.isEmpty()) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "La sesión no es válida");
        }
        AutorizadorPrescripcionesDiagnosticas.IdentidadMedico medico = identidad.orElseThrow();
        if (!medico.tieneRolMedico() || medico.medicoId() == null || medico.medicoId().isBlank()) {
            throw new ErrorHttp(403, "PROHIBIDO", "Se requiere el rol MEDICO");
        }
        return medico.medicoId();
    }

    private boolean esBearerValido(String cabecera) {
        if (cabecera == null || cabecera.length() <= 7
                || !cabecera.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        String token = cabecera.substring(7);
        return !token.isBlank() && token.chars().noneMatch(Character::isWhitespace);
    }

    private String leerCuerpo(HttpExchange intercambio) throws IOException {
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

    private boolean esTipoJson(String valor) {
        int parametros = valor.indexOf(';');
        String tipo = parametros < 0 ? valor.trim() : valor.substring(0, parametros).trim();
        return "application/json".equalsIgnoreCase(tipo);
    }

    private void responderJson(HttpExchange intercambio, int estado, String json) throws IOException {
        byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        intercambio.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        intercambio.getResponseBody().write(cuerpo);
    }

    private void responderError(HttpExchange intercambio, int estado, String codigo, String mensaje)
            throws IOException {
        responderJson(intercambio, estado, JsonRealizacionPruebas.error(codigo, mensaje));
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
