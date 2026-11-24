package es.uclm.esi.iso2.B101.servidor.api;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Utilidades HTTP y JSON compartidas por los handlers administrativos (CU20, CU21 y CU22).
 */
final class HttpAdminSupport {

    private static final int MAX_CUERPO_BYTES = 16 * 1024;
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private HttpAdminSupport() {
    }

    static String autenticarAdministrador(HttpExchange intercambio, AutorizadorAdministrador autorizador) {
        List<String> cabeceras = intercambio.getRequestHeaders().get("Authorization");
        if (cabeceras == null || cabeceras.size() != 1 || !esBearerValido(cabeceras.get(0))) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "Se requiere una sesión válida");
        }
        Optional<AutorizadorAdministrador.IdentidadAdministrador> identidad =
                autorizador.autenticar(cabeceras.get(0));
        if (identidad.isEmpty()) {
            throw new ErrorHttp(401, "NO_AUTENTICADO", "La sesión no es válida");
        }
        AutorizadorAdministrador.IdentidadAdministrador admin = identidad.orElseThrow();
        if (!admin.tieneRolAdministrador()
                || admin.administradorId() == null
                || admin.administradorId().isBlank()) {
            throw new ErrorHttp(403, "PROHIBIDO", "Se requiere el rol ADMINISTRADOR");
        }
        return admin.administradorId();
    }

    private static boolean esBearerValido(String cabecera) {
        if (cabecera == null || cabecera.length() <= 7
                || !cabecera.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        String token = cabecera.substring(7);
        return !token.isBlank() && token.chars().noneMatch(Character::isWhitespace);
    }

    static String leerCuerpoJson(HttpExchange intercambio) throws IOException {
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

    static Map<String, String> leerParametrosOpcionales(String consulta, Set<String> permitidos) {
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
            if (!permitidos.contains(nombre) || valor.isBlank() || parametros.putIfAbsent(nombre, valor) != null) {
                throw new IllegalArgumentException("Parámetros de consulta no válidos");
            }
        }
        return parametros;
    }

    static void responderJson(HttpExchange intercambio, int estado, String json) throws IOException {
        byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        intercambio.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        intercambio.getResponseBody().write(cuerpo);
    }

    static void responderError(HttpExchange intercambio, int estado, String codigo, String mensaje)
            throws IOException {
        responderJson(intercambio, estado, errorJson(codigo, mensaje));
    }

    static String errorJson(String codigo, String mensaje) {
        return "{\"exito\":false,\"codigoError\":" + cadenaJson(codigo)
                + ",\"mensaje\":" + cadenaJson(mensaje) + "}";
    }

    static String cadenaJson(String valor) {
        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20 || Character.isSurrogate(c)) {
                        json.append("\\u")
                                .append(HEX_DIGITS[(c >>> 12) & 0x0f])
                                .append(HEX_DIGITS[(c >>> 8) & 0x0f])
                                .append(HEX_DIGITS[(c >>> 4) & 0x0f])
                                .append(HEX_DIGITS[c & 0x0f]);
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    static Map<String, Object> parsearObjetoJson(String json) {
        if (json == null) {
            throw new IllegalArgumentException("El cuerpo JSON no puede ser nulo");
        }
        String texto = json.trim();
        if (texto.length() < 2 || texto.charAt(0) != '{' || texto.charAt(texto.length() - 1) != '}') {
            throw new IllegalArgumentException("Se esperaba un objeto JSON válido");
        }
        Map<String, Object> resultado = new LinkedHashMap<>();
        int i = saltarEspacios(texto, 1, texto.length() - 1);
        int fin = texto.length() - 1;
        boolean esperandoEntrada = false;
        while (i < fin) {
            if (texto.charAt(i) != '"') {
                throw new IllegalArgumentException("Se esperaba una clave JSON entre comillas");
            }
            ParseResult clave = leerCadena(texto, i, fin);
            i = saltarEspacios(texto, clave.siguiente, fin);
            if (i >= fin || texto.charAt(i) != ':') {
                throw new IllegalArgumentException("Se esperaba ':' tras la clave JSON");
            }
            i = saltarEspacios(texto, i + 1, fin);
            if (i >= fin) {
                throw new IllegalArgumentException("Falta el valor para la clave " + clave.valor);
            }
            Object valor;
            char inicioValor = texto.charAt(i);
            if (inicioValor == '"') {
                ParseResult cadena = leerCadena(texto, i, fin);
                valor = cadena.valor;
                i = cadena.siguiente;
            } else {
                int inicio = i;
                while (i < fin && texto.charAt(i) != ',' && !Character.isWhitespace(texto.charAt(i))) {
                    i++;
                }
                String literal = texto.substring(inicio, i);
                valor = parsearLiteral(literal);
            }
            if (resultado.putIfAbsent(clave.valor, valor) != null) {
                throw new IllegalArgumentException("Clave JSON duplicada: " + clave.valor);
            }
            i = saltarEspacios(texto, i, fin);
            if (i < fin) {
                if (texto.charAt(i) != ',') {
                    throw new IllegalArgumentException("Se esperaba ',' entre propiedades JSON");
                }
                i = saltarEspacios(texto, i + 1, fin);
                esperandoEntrada = true;
            } else {
                esperandoEntrada = false;
            }
        }
        if (esperandoEntrada) {
            throw new IllegalArgumentException("Coma final no válida en el objeto JSON");
        }
        return resultado;
    }

    static String requerirCadena(Map<String, Object> campos, String clave) {
        Object valor = campos.get(clave);
        if (!(valor instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("El campo '" + clave + "' es obligatorio y debe ser texto");
        }
        return s;
    }

    static boolean requerirBooleano(Map<String, Object> campos, String clave) {
        Object valor = campos.get(clave);
        if (!(valor instanceof Boolean b)) {
            throw new IllegalArgumentException("El campo '" + clave + "' es obligatorio y debe ser booleano");
        }
        return b;
    }

    static int requerirEntero(Map<String, Object> campos, String clave) {
        Object valor = campos.get(clave);
        if (!(valor instanceof Integer n)) {
            throw new IllegalArgumentException("El campo '" + clave + "' es obligatorio y debe ser entero");
        }
        return n;
    }

    private static Object parsearLiteral(String literal) {
        if ("true".equals(literal)) {
            return Boolean.TRUE;
        }
        if ("false".equals(literal)) {
            return Boolean.FALSE;
        }
        if ("null".equals(literal)) {
            return null;
        }
        try {
            return Integer.parseInt(literal);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Valor JSON no soportado: " + literal, error);
        }
    }

    private static int saltarEspacios(String texto, int i, int fin) {
        while (i < fin && Character.isWhitespace(texto.charAt(i))) {
            i++;
        }
        return i;
    }

    private static ParseResult leerCadena(String texto, int inicioComilla, int fin) {
        StringBuilder sb = new StringBuilder();
        int i = inicioComilla + 1;
        while (i < fin) {
            char c = texto.charAt(i);
            if (c == '\\' && i + 1 < fin) {
                char siguiente = texto.charAt(i + 1);
                switch (siguiente) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 5 >= fin) {
                            throw new IllegalArgumentException("Secuencia unicode incompleta");
                        }
                        sb.append((char) Integer.parseInt(texto.substring(i + 2, i + 6), 16));
                        i += 4;
                    }
                    default -> throw new IllegalArgumentException("Escape JSON no válido: \\" + siguiente);
                }
                i += 2;
            } else if (c == '"') {
                return new ParseResult(sb.toString(), i + 1);
            } else {
                sb.append(c);
                i++;
            }
        }
        throw new IllegalArgumentException("Cadena JSON sin cerrar");
    }

    private record ParseResult(String valor, int siguiente) {
    }

    static final class ErrorHttp extends RuntimeException {
        final int estado;
        final String codigo;

        ErrorHttp(int estado, String codigo, String mensaje) {
            super(mensaje);
            this.estado = estado;
            this.codigo = codigo;
        }
    }
}
