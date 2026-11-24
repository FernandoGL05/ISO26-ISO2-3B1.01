package es.uclm.esi.iso2.B101.cliente.comunicacion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utilidad JSON ligera del módulo cliente para serializar y leer respuestas del servidor.
 */
public final class JsonClienteUtil {

    private JsonClienteUtil() {
    }

    public static String aJson(Map<String, String> campos) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean primero = true;
        for (Map.Entry<String, String> entry : campos.entrySet()) {
            if (!primero) {
                sb.append(',');
            }
            primero = false;
            sb.append('"').append(escapar(entry.getKey())).append("\":");
            if (entry.getValue() == null) {
                sb.append("null");
            } else {
                sb.append('"').append(escapar(entry.getValue())).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    public static Map<String, String> parsearObjetoPlano(String json) {
        Map<String, String> resultado = new LinkedHashMap<>();
        if (json == null) {
            return resultado;
        }
        String texto = json.trim();
        if (texto.length() < 2 || texto.charAt(0) != '{' || texto.charAt(texto.length() - 1) != '}') {
            return resultado;
        }

        int i = 1;
        int fin = texto.length() - 1;
        while (i < fin) {
            i = saltarEspacios(texto, i, fin);
            if (i >= fin) {
                break;
            }
            if (texto.charAt(i) == ',') {
                i++;
                continue;
            }
            if (texto.charAt(i) != '"') {
                break;
            }
            ParseResult clave = leerCadena(texto, i, fin);
            if (clave == null) {
                break;
            }
            i = saltarEspacios(texto, clave.siguienteIndice, fin);
            if (i >= fin || texto.charAt(i) != ':') {
                break;
            }
            i = saltarEspacios(texto, i + 1, fin);
            if (i >= fin) {
                break;
            }
            if (texto.charAt(i) == '"') {
                ParseResult valor = leerCadena(texto, i, fin);
                if (valor == null) {
                    break;
                }
                resultado.put(clave.valor, valor.valor);
                i = valor.siguienteIndice;
            } else {
                int inicio = i;
                while (i < fin && texto.charAt(i) != ',') {
                    i++;
                }
                String literal = texto.substring(inicio, i).trim();
                if (!"null".equals(literal)) {
                    resultado.put(clave.valor, literal);
                }
            }
        }
        return resultado;
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
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> sb.append(siguiente);
                }
                i += 2;
            } else if (c == '"') {
                return new ParseResult(sb.toString(), i + 1);
            } else {
                sb.append(c);
                i++;
            }
        }
        return null;
    }

    private static String escapar(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private record ParseResult(String valor, int siguienteIndice) {
    }
}
