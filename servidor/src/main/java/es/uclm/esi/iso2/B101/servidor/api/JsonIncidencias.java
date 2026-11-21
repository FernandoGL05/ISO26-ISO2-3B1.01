package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.Incidencia;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JsonIncidencias {
    private static final char[] DIGITOS_HEXADECIMALES = "0123456789abcdef".toCharArray();
    private static final Set<String> CAMPOS_CREACION = Set.of("asunto", "descripcion", "citaId");

    private JsonIncidencias() {
    }

    static DatosCreacion parsearCreacion(String json) {
        return new Analizador(json).parsear();
    }

    static String serializarLista(List<Incidencia> incidencias) {
        StringBuilder json = new StringBuilder("[");
        for (int indice = 0; indice < incidencias.size(); indice++) {
            if (indice > 0) {
                json.append(',');
            }
            appendIncidencia(json, incidencias.get(indice));
        }
        return json.append(']').toString();
    }

    static String serializar(Incidencia incidencia) {
        StringBuilder json = new StringBuilder();
        appendIncidencia(json, incidencia);
        return json.toString();
    }

    static String error(String codigo, String mensaje) {
        return "{\"exito\":false,\"codigoError\":" + cadena(codigo)
                + ",\"mensaje\":" + cadena(mensaje) + "}";
    }

    private static void appendIncidencia(StringBuilder json, Incidencia incidencia) {
        json.append("{\"id\":").append(cadena(incidencia.getId()))
                .append(",\"pacienteId\":").append(cadena(incidencia.getPacienteId()))
                .append(",\"asunto\":").append(cadena(incidencia.getAsunto()))
                .append(",\"descripcion\":").append(cadena(incidencia.getDescripcion()))
                .append(",\"citaId\":");
        if (incidencia.getCitaId() == null) {
            json.append("null");
        } else {
            json.append(cadena(incidencia.getCitaId()));
        }
        json.append(",\"fechaHoraCreacion\":")
                .append(cadena(incidencia.getFechaHoraCreacion().toString()))
                .append(",\"estado\":").append(cadena(incidencia.getEstado().name()))
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

    record DatosCreacion(String asunto, String descripcion, String citaId) {
    }

    private static final class Analizador {
        private final String json;
        private int posicion;

        private Analizador(String json) {
            if (json == null) {
                throw new IllegalArgumentException("El cuerpo JSON no puede ser nulo");
            }
            this.json = json;
        }

        private DatosCreacion parsear() {
            Map<String, String> campos = new HashMap<>();
            saltarEspacios();
            esperar('{');
            saltarEspacios();
            if (!consumir('}')) {
                while (true) {
                    saltarEspacios();
                    String nombre = leerCadena();
                    if (!CAMPOS_CREACION.contains(nombre)) {
                        throw invalido();
                    }
                    saltarEspacios();
                    esperar(':');
                    saltarEspacios();
                    String valor = leerCadenaONull();
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
            if (posicion != json.length()
                    || !campos.containsKey("asunto")
                    || !campos.containsKey("descripcion")) {
                throw invalido();
            }
            return new DatosCreacion(campos.get("asunto"), campos.get("descripcion"), campos.get("citaId"));
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
