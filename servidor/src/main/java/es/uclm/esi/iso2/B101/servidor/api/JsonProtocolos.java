package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.Protocolo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serialización y deserialización JSON autónoma para protocolos clínicos/técnicos (CU21).
 */
final class JsonProtocolos {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private JsonProtocolos() {
    }

    static DatosProtocolo parsear(String cuerpo) {
        Map<String, Object> campos = parsearObjetoJson(cuerpo);
        return new DatosProtocolo(
                requerirCadena(campos, "codigo"),
                requerirCadena(campos, "nombre"),
                requerirCadena(campos, "tipoPruebaId"),
                requerirCadena(campos, "descripcion"),
                requerirCadena(campos, "pasosPreparacion"),
                requerirCadena(campos, "requisitosSeguridad"),
                requerirBooleano(campos, "usoContraste"));
    }

    static boolean parsearEstado(String cuerpo) {
        Map<String, Object> campos = parsearObjetoJson(cuerpo);
        return requerirBooleano(campos, "activo");
    }

    static String serializar(Protocolo protocolo) {
        return "{\"id\":" + cadenaJson(protocolo.getId())
                + ",\"codigo\":" + cadenaJson(protocolo.getCodigo())
                + ",\"nombre\":" + cadenaJson(protocolo.getNombre())
                + ",\"tipoPruebaId\":" + cadenaJson(protocolo.getTipoPruebaId())
                + ",\"descripcion\":" + cadenaJson(protocolo.getDescripcion())
                + ",\"pasosPreparacion\":" + cadenaJson(protocolo.getPasosPreparacion())
                + ",\"requisitosSeguridad\":" + cadenaJson(protocolo.getRequisitosSeguridad())
                + ",\"usoContraste\":" + protocolo.isUsoContraste()
                + ",\"version\":" + protocolo.getVersion()
                + ",\"activo\":" + protocolo.isActivo()
                + '}';
    }

    static String serializarLista(List<Protocolo> protocolos) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < protocolos.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(serializar(protocolos.get(i)));
        }
        return sb.append(']').toString();
    }

    static String error(String codigo, String mensaje) {
        return "{\"exito\":false,\"codigoError\":" + cadenaJson(codigo)
                + ",\"mensaje\":" + cadenaJson(mensaje) + "}";
    }

    private static String cadenaJson(String valor) {
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

    private static Map<String, Object> parsearObjetoJson(String json) {
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
            if (texto.charAt(i) == '"') {
                ParseResult cadena = leerCadena(texto, i, fin);
                valor = cadena.valor;
                i = cadena.siguiente;
            } else {
                int inicio = i;
                while (i < fin && texto.charAt(i) != ',' && !Character.isWhitespace(texto.charAt(i))) {
                    i++;
                }
                valor = parsearLiteral(texto.substring(inicio, i));
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

    private static String requerirCadena(Map<String, Object> campos, String clave) {
        Object valor = campos.get(clave);
        if (!(valor instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("El campo '" + clave + "' es obligatorio y debe ser texto");
        }
        return s;
    }

    private static boolean requerirBooleano(Map<String, Object> campos, String clave) {
        Object valor = campos.get(clave);
        if (!(valor instanceof Boolean b)) {
            throw new IllegalArgumentException("El campo '" + clave + "' es obligatorio y debe ser booleano");
        }
        return b;
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

    record DatosProtocolo(
            String codigo,
            String nombre,
            String tipoPruebaId,
            String descripcion,
            String pasosPreparacion,
            String requisitosSeguridad,
            boolean usoContraste) {
    }
}
