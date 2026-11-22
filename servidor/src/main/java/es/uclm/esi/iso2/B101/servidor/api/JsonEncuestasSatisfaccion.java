package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.EncuestaSatisfaccion;
import es.uclm.esi.iso2.B101.servidor.dominio.RespuestaEncuestaSatisfaccion;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JsonEncuestasSatisfaccion {
    private static final char[] DIGITOS_HEXADECIMALES = "0123456789abcdef".toCharArray();
    private static final Set<String> CAMPOS_RESPUESTA = Set.of("puntuacion", "comentario");

    private JsonEncuestasSatisfaccion() {
    }

    static DatosRespuesta parsearRespuesta(String json) {
        return new Analizador(json).parsear();
    }

    static String serializarEncuestas(List<EncuestaSatisfaccion> encuestas) {
        StringBuilder json = new StringBuilder("[");
        for (int indice = 0; indice < encuestas.size(); indice++) {
            if (indice > 0) {
                json.append(',');
            }
            EncuestaSatisfaccion encuesta = encuestas.get(indice);
            json.append("{\"id\":").append(cadena(encuesta.getId()))
                    .append(",\"titulo\":").append(cadena(encuesta.getTitulo()))
                    .append(",\"descripcion\":").append(cadena(encuesta.getDescripcion()))
                    .append(",\"disponible\":").append(encuesta.estaDisponible())
                    .append('}');
        }
        return json.append(']').toString();
    }

    static String serializarRespuestas(List<RespuestaEncuestaSatisfaccion> respuestas) {
        StringBuilder json = new StringBuilder("[");
        for (int indice = 0; indice < respuestas.size(); indice++) {
            if (indice > 0) {
                json.append(',');
            }
            appendRespuesta(json, respuestas.get(indice));
        }
        return json.append(']').toString();
    }

    static String serializarRespuesta(RespuestaEncuestaSatisfaccion respuesta) {
        StringBuilder json = new StringBuilder();
        appendRespuesta(json, respuesta);
        return json.toString();
    }

    static String error(String codigo, String mensaje) {
        return "{\"exito\":false,\"codigoError\":" + cadena(codigo)
                + ",\"mensaje\":" + cadena(mensaje) + "}";
    }

    private static void appendRespuesta(StringBuilder json, RespuestaEncuestaSatisfaccion respuesta) {
        json.append("{\"id\":").append(cadena(respuesta.getId()))
                .append(",\"encuestaId\":").append(cadena(respuesta.getEncuestaId()))
                .append(",\"puntuacion\":").append(respuesta.getPuntuacion())
                .append(",\"comentario\":");
        if (respuesta.getComentario() == null) {
            json.append("null");
        } else {
            json.append(cadena(respuesta.getComentario()));
        }
        json.append(",\"fechaRespuesta\":").append(cadena(respuesta.getFechaRespuesta().toString()))
                .append('}');
    }

    private static String cadena(String valor) {
        StringBuilder json = new StringBuilder("\"");
        for (int indice = 0; indice < valor.length(); indice++) {
            char caracter = valor.charAt(indice);
            switch (caracter) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (caracter < 0x20 || Character.isSurrogate(caracter)) {
                        json.append("\\u")
                                .append(DIGITOS_HEXADECIMALES[(caracter >>> 12) & 0x0f])
                                .append(DIGITOS_HEXADECIMALES[(caracter >>> 8) & 0x0f])
                                .append(DIGITOS_HEXADECIMALES[(caracter >>> 4) & 0x0f])
                                .append(DIGITOS_HEXADECIMALES[caracter & 0x0f]);
                    } else {
                        json.append(caracter);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    record DatosRespuesta(int puntuacion, String comentario) {
    }

    private static final class Analizador {
        private final String json;
        private int posicion;

        private Analizador(String json) {
            if (json == null) {
                throw invalido();
            }
            this.json = json;
        }

        private DatosRespuesta parsear() {
            Map<String, Object> campos = new HashMap<>();
            saltarEspacios();
            esperar('{');
            saltarEspacios();
            if (!consumir('}')) {
                while (true) {
                    saltarEspacios();
                    String nombre = leerCadena();
                    if (!CAMPOS_RESPUESTA.contains(nombre)) {
                        throw invalido();
                    }
                    saltarEspacios();
                    esperar(':');
                    saltarEspacios();
                    Object valor = "puntuacion".equals(nombre) ? leerNumero() : leerCadenaONull();
                    if (campos.containsKey(nombre)) {
                        throw invalido();
                    }
                    campos.put(nombre, valor);
                    saltarEspacios();
                    if (consumir('}')) {
                        break;
                    }
                    esperar(',');
                }
            }
            saltarEspacios();
            if (posicion != json.length() || !(campos.get("puntuacion") instanceof Integer puntuacion)) {
                throw invalido();
            }
            Object comentario = campos.get("comentario");
            if (comentario != null && !(comentario instanceof String)) {
                throw invalido();
            }
            return new DatosRespuesta(puntuacion, (String) comentario);
        }

        private Integer leerNumero() {
            int inicio = posicion;
            if (consumir('-')) {
                if (posicion >= json.length() || !esDigitoJson(json.charAt(posicion))) {
                    throw invalido();
                }
            }
            if (posicion >= json.length() || !esDigitoJson(json.charAt(posicion))) {
                throw invalido();
            }
            if (json.charAt(posicion) == '0') {
                posicion++;
                if (posicion < json.length() && esDigitoJson(json.charAt(posicion))) {
                    throw invalido();
                }
            } else {
                while (posicion < json.length() && esDigitoJson(json.charAt(posicion))) {
                    posicion++;
                }
            }
            try {
                return Integer.valueOf(json.substring(inicio, posicion));
            } catch (NumberFormatException error) {
                throw invalido();
            }
        }

        private boolean esDigitoJson(char caracter) {
            return caracter >= '0' && caracter <= '9';
        }

        private String leerCadenaONull() {
            if (json.startsWith("null", posicion)) {
                posicion += 4;
                return null;
            }
            return leerCadena();
        }

        private String leerCadena() {
            esperar('"');
            StringBuilder resultado = new StringBuilder();
            while (posicion < json.length()) {
                char caracter = json.charAt(posicion++);
                if (caracter == '"') {
                    return resultado.toString();
                }
                if (caracter < 0x20) {
                    throw invalido();
                }
                if (caracter != '\\') {
                    resultado.append(caracter);
                    continue;
                }
                if (posicion >= json.length()) {
                    throw invalido();
                }
                char escape = json.charAt(posicion++);
                switch (escape) {
                    case '"', '\\', '/' -> resultado.append(escape);
                    case 'b' -> resultado.append('\b');
                    case 'f' -> resultado.append('\f');
                    case 'n' -> resultado.append('\n');
                    case 'r' -> resultado.append('\r');
                    case 't' -> resultado.append('\t');
                    case 'u' -> resultado.append(leerCaracterUnicode());
                    default -> throw invalido();
                }
            }
            throw invalido();
        }

        private char leerCaracterUnicode() {
            if (posicion + 4 > json.length()) {
                throw invalido();
            }
            int valor = 0;
            for (int indice = 0; indice < 4; indice++) {
                int digito = Character.digit(json.charAt(posicion++), 16);
                if (digito < 0) {
                    throw invalido();
                }
                valor = (valor << 4) | digito;
            }
            return (char) valor;
        }

        private void saltarEspacios() {
            while (posicion < json.length()) {
                char caracter = json.charAt(posicion);
                if (caracter != ' ' && caracter != '\t' && caracter != '\r' && caracter != '\n') {
                    return;
                }
                posicion++;
            }
        }

        private void esperar(char esperado) {
            if (!consumir(esperado)) {
                throw invalido();
            }
        }

        private boolean consumir(char esperado) {
            if (posicion < json.length() && json.charAt(posicion) == esperado) {
                posicion++;
                return true;
            }
            return false;
        }

        private IllegalArgumentException invalido() {
            return new IllegalArgumentException("El cuerpo JSON no es válido");
        }
    }
}
