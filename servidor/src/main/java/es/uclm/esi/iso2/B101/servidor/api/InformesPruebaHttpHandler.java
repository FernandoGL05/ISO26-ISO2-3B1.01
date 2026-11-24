package es.uclm.esi.iso2.B101.servidor.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import es.uclm.esi.iso2.B101.servidor.dominio.InformePrueba;
import es.uclm.esi.iso2.B101.servidor.servicio.InformePruebaNoPermitidoException;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioInformesPrueba;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class InformesPruebaHttpHandler implements HttpHandler {
    private static final String RUTA = "/api/informes-prueba";
    private static final int MAX_CUERPO_BYTES = 7 * 1024 * 1024;
    private static final System.Logger LOGGER = System.getLogger(InformesPruebaHttpHandler.class.getName());

    private final ServicioInformesPrueba servicio;
    private final AutorizadorInformesPrueba autorizador;

    public InformesPruebaHttpHandler(
            ServicioInformesPrueba servicio, AutorizadorInformesPrueba autorizador) {
        this.servicio = Objects.requireNonNull(servicio, "servicio no puede ser nulo");
        this.autorizador = Objects.requireNonNull(autorizador, "autorizador no puede ser nulo");
    }

    public static void registrarEn(
            HttpServer servidor,
            ServicioInformesPrueba servicio,
            AutorizadorInformesPrueba autorizador) {
        Objects.requireNonNull(servidor, "servidor no puede ser nulo")
                .createContext(RUTA, new InformesPruebaHttpHandler(servicio, autorizador));
    }

    @Override
    public void handle(HttpExchange intercambio) throws IOException {
        try {
            procesar(intercambio);
        } catch (ErrorHttp error) {
            responderError(intercambio, error.estado, error.codigo, error.getMessage());
        } catch (InformePruebaNoPermitidoException error) {
            responderError(intercambio, 403, "PROHIBIDO", "No está autorizado para crear un informe de esta prueba");
        } catch (IllegalArgumentException error) {
            responderError(intercambio, 400, "SOLICITUD_INVALIDA", "La solicitud contiene datos inválidos");
        } catch (java.util.NoSuchElementException error) {
            responderError(intercambio, 404, "NO_ENCONTRADO", "Informe o prueba no encontrada");
        } catch (IllegalStateException error) {
            responderError(intercambio, 409, "CONFLICTO", error.getMessage());
        } catch (RuntimeException error) {
            LOGGER.log(System.Logger.Level.ERROR, "Error inesperado procesando un informe de prueba", error);
            responderError(intercambio, 500, "ERROR_INTERNO", "No se pudo procesar la solicitud");
        } finally {
            intercambio.close();
        }
    }

    private void procesar(HttpExchange intercambio) throws IOException {
        Ruta ruta = buscarRuta(intercambio.getRequestURI().getRawPath());
        if (ruta == null) {
            throw new ErrorHttp(404, "NO_ENCONTRADO", "Ruta no encontrada");
        }
        if (intercambio.getRequestURI().getRawQuery() != null) {
            throw new ErrorHttp(400, "SOLICITUD_INVALIDA", "No se admiten parámetros de consulta");
        }
        if (!ruta.metodoPermitido(intercambio.getRequestMethod())) {
            intercambio.getResponseHeaders().set("Allow", ruta.metodosPermitidos());
            throw new ErrorHttp(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido");
        }
        AutorizadorInformesPrueba.Identidad identidad = autenticar(intercambio);

        switch (ruta.operacion) {
            case COLECCION -> {
                if ("POST".equals(intercambio.getRequestMethod())) {
                    exigirMedico(identidad);
                    JsonInformesPrueba.DatosCreacion datos =
                            JsonInformesPrueba.parsearCreacion(leerCuerpo(intercambio));
                    InformePrueba informe = servicio.crear(
                            identidad.medicoId(), datos.pruebaId(), datos.hallazgos(), datos.conclusion());
                    responderJson(intercambio, 201, JsonInformesPrueba.serializar(informe));
                } else {
                    exigirPaciente(identidad);
                    responderJson(intercambio, 200,
                            JsonInformesPrueba.serializarLista(servicio.consultarPorPaciente(identidad.pacienteId())));
                }
            }
            case COLECCION_MEDICO -> {
                exigirMedico(identidad);
                responderJson(intercambio, 200,
                        JsonInformesPrueba.serializarLista(servicio.consultarPorMedico(identidad.medicoId())));
            }
            case DETALLE -> {
                exigirPacienteOMedico(identidad);
                InformePrueba informe = servicio.consultar(
                        ruta.informeId,
                        identidad.tieneRolPaciente() ? identidad.pacienteId() : null,
                        identidad.tieneRolMedico() ? identidad.medicoId() : null);
                responderJson(intercambio, 200, JsonInformesPrueba.serializar(informe));
            }
            case IMAGEN -> {
                if ("POST".equals(intercambio.getRequestMethod())) {
                    exigirMedico(identidad);
                    JsonInformesPrueba.DatosImagen datos = JsonInformesPrueba.parsearImagen(leerCuerpo(intercambio));
                    servicio.adjuntarImagen(
                            identidad.medicoId(), ruta.informeId, datos.tipoImagen(), datos.imagenBase64());
                    responderJson(intercambio, 200, JsonInformesPrueba.serializar(
                            servicio.consultar(ruta.informeId, null, identidad.medicoId())));
                } else {
                    exigirPacienteOMedico(identidad);
                    InformePrueba informe = servicio.consultar(
                            ruta.informeId,
                            identidad.tieneRolPaciente() ? identidad.pacienteId() : null,
                            identidad.tieneRolMedico() ? identidad.medicoId() : null);
                    if (!informe.tieneImagen()) {
                        throw new java.util.NoSuchElementException("Imagen no encontrada");
                    }
                    responderImagen(intercambio, informe);
                }
            }
        }
    }

    private AutorizadorInformesPrueba.Identidad autenticar(HttpExchange intercambio) {
        List<String> cabeceras = intercambio.getRequestHeaders().get("Authorization");
        if (cabeceras == null || cabeceras.size() != 1 || !esBearerValido(cabeceras.get(0))) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "Se requiere una sesión válida");
        }
        Optional<AutorizadorInformesPrueba.Identidad> identidad = autorizador.autenticar(cabeceras.get(0));
        if (identidad.isEmpty()) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "La sesión no es válida");
        }
        return identidad.orElseThrow();
    }

    private boolean esBearerValido(String cabecera) {
        if (cabecera == null || cabecera.length() <= 7
                || !cabecera.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        String token = cabecera.substring(7);
        return !token.isBlank() && token.chars().noneMatch(Character::isWhitespace);
    }

    private void exigirPaciente(AutorizadorInformesPrueba.Identidad identidad) {
        if (!identidad.tieneRolPaciente() || identidad.pacienteId() == null || identidad.pacienteId().isBlank()) {
            throw new ErrorHttp(403, "PROHIBIDO", "Se requiere el rol PACIENTE");
        }
    }

    private void exigirMedico(AutorizadorInformesPrueba.Identidad identidad) {
        if (!identidad.tieneRolMedico() || identidad.medicoId() == null || identidad.medicoId().isBlank()) {
            throw new ErrorHttp(403, "PROHIBIDO", "Se requiere el rol MEDICO");
        }
    }

    private void exigirPacienteOMedico(AutorizadorInformesPrueba.Identidad identidad) {
        boolean pacienteValido = identidad.tieneRolPaciente()
                && identidad.pacienteId() != null && !identidad.pacienteId().isBlank();
        boolean medicoValido = identidad.tieneRolMedico()
                && identidad.medicoId() != null && !identidad.medicoId().isBlank();
        if (!pacienteValido && !medicoValido) {
            throw new ErrorHttp(403, "PROHIBIDO", "Se requiere el rol PACIENTE o MEDICO");
        }
    }

    private Ruta buscarRuta(String ruta) {
        if (RUTA.equals(ruta)) {
            return new Ruta(Operacion.COLECCION, null);
        }
        if ((RUTA + "/medico").equals(ruta)) {
            return new Ruta(Operacion.COLECCION_MEDICO, null);
        }
        String[] segmentos = ruta.split("/", -1);
        if (segmentos.length == 4 && segmentos[1].equals("api")
                && segmentos[2].equals("informes-prueba")) {
            return new Ruta(Operacion.DETALLE, decodificar(segmentos[3]));
        }
        if (segmentos.length == 5 && segmentos[1].equals("api")
                && segmentos[2].equals("informes-prueba")
                && segmentos[4].equals("imagen")) {
            return new Ruta(Operacion.IMAGEN, decodificar(segmentos[3]));
        }
        return null;
    }

    private String decodificar(String segmento) {
        return URLDecoder.decode(segmento.replace("+", "%2B"), StandardCharsets.UTF_8);
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
        responderJson(intercambio, estado, JsonInformesPrueba.error(codigo, mensaje));
    }

    private void responderImagen(HttpExchange intercambio, InformePrueba informe) throws IOException {
        byte[] imagen = informe.getImagen();
        intercambio.getResponseHeaders().set("Content-Type", informe.getTipoImagen());
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        intercambio.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        intercambio.sendResponseHeaders(200, imagen.length);
        intercambio.getResponseBody().write(imagen);
    }

    private enum Operacion {
        COLECCION,
        COLECCION_MEDICO,
        DETALLE,
        IMAGEN
    }

    private record Ruta(Operacion operacion, String informeId) {
        private boolean metodoPermitido(String metodo) {
            return switch (operacion) {
                case COLECCION -> "GET".equals(metodo) || "POST".equals(metodo);
                case COLECCION_MEDICO, DETALLE -> "GET".equals(metodo);
                case IMAGEN -> "GET".equals(metodo) || "POST".equals(metodo);
            };
        }

        private String metodosPermitidos() {
            return switch (operacion) {
                case COLECCION, IMAGEN -> "GET, POST";
                case COLECCION_MEDICO, DETALLE -> "GET";
            };
        }
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
