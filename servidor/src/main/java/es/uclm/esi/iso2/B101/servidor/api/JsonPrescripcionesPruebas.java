package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.SolicitudPruebaDiagnostica;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JsonPrescripcionesPruebas {
    private static final char[] DIGITOS_HEXADECIMALES = "0123456789abcdef".toCharArray();
    private static final Set<String> CAMPOS_CREACION =
            Set.of("pacienteId", "tipoPrueba", "indicacionClinica", "justificacion", "prioridad");

    private JsonPrescripcionesPruebas() {
    }

    static DatosCreacion parsearCreacion(String json) {
        Map<String, String> campos = new Parser(json).leerObjeto();
        if (!campos.keySet().equals(CAMPOS_CREACION)) {
            throw formatoInvalido();
        }
        return new DatosCreacion(
                campos.get("pacienteId"),
                campos.get("tipoPrueba"),
                campos.get("indicacionClinica"),
                campos.get("justificacion"),
                campos.get("prioridad"));
    }

    static String serializar(SolicitudPruebaDiagnostica solicitud) {
        return new StringBuilder("{\"id\":")
                .append(cadena(solicitud.getId()))
                .append(",\"medicoId\":").append(cadena(solicitud.getMedicoId()))
                .append(",\"pacienteId\":").append(cadena(solicitud.getPacienteId()))
                .append(",\"tipoPrueba\":").append(cadena(solicitud.getTipoPrueba()))
                .append(",\"indicacionClinica\":").append(cadena(solicitud.getIndicacionClinica()))
                .append(",\"justificacion\":").append(cadena(solicitud.getJustificacion()))
                .append(",\"prioridad\":").append(cadena(solicitud.getPrioridad().name()))
                .append(",\"fecha\":").append(cadena(solicitud.getFecha().toString()))
                .append(",\"estado\":").append(cadena(solicitud.getEstado().name()))
                .append('}')
                .toString();
    }

    static String serializarLista(List<SolicitudPruebaDiagnostica> solicitudes) {
        StringBuilder json = new StringBuilder("[");
        for (int indice = 0; indice < solicitudes.size(); indice++) {
            if (indice > 0) {
                json.append(',');
            }
            json.append(serializar(solicitudes.get(indice)));
        }
        return json.append(']').toString();
    }

    static String error(String codigo, String mensaje) {
        return "{\"exito\":false,\"codigoError\":" + cadena(codigo)
                + ",\"mensaje\":" + cadena(mensaje) + "}";
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

    private static IllegalArgumentException formatoInvalido() {
        return new IllegalArgumentException("El cuerpo debe ser un objeto JSON con los campos requeridos");
    }

    record DatosCreacion(
            String pacienteId,
            String tipoPrueba,
            String indicacionClinica,
            String justificacion,
            String prioridad) {
    }

    private static final class Parser {
        private final String json;
        private int posicion;

        private Parser(String json) {
            this.json = json;
        }

        private Map<String, String> leerObjeto() {
            espacios();
            esperar('{');
            espacios();
            Map<String, String> campos = new HashMap<>();
            if (consumir('}')) {
                terminar();
                return campos;
            }
            do {
                espacios();
                String clave = leerCadena();
                espacios();
                esperar(':');
                espacios();
                String valor = leerCadena();
                if (campos.putIfAbsent(clave, valor) != null) {
                    throw formatoInvalido();
                }
                espacios();
                if (consumir('}')) {
                    terminar();
                    return campos;
                }
                esperar(',');
            } while (true);
        }

        private String leerCadena() {
            esperar('"');
            StringBuilder valor = new StringBuilder();
            while (posicion < json.length()) {
                char caracter = json.charAt(posicion++);
                if (caracter == '"') {
                    return valor.toString();
                }
                if (caracter < 0x20) {
                    throw formatoInvalido();
                }
                if (caracter != '\\') {
                    valor.append(caracter);
                    continue;
                }
                if (posicion >= json.length()) {
                    throw formatoInvalido();
                }
                char escape = json.charAt(posicion++);
                switch (escape) {
                    case '"', '\\', '/' -> valor.append(escape);
                    case 'b' -> valor.append('\b');
                    case 'f' -> valor.append('\f');
                    case 'n' -> valor.append('\n');
                    case 'r' -> valor.append('\r');
                    case 't' -> valor.append('\t');
                    case 'u' -> valor.append(leerCaracterUnicode());
                    default -> throw formatoInvalido();
                }
            }
            throw formatoInvalido();
        }

        private char leerCaracterUnicode() {
            if (json.length() - posicion < 4) {
                throw formatoInvalido();
            }
            int valor = 0;
            for (int indice = 0; indice < 4; indice++) {
                int digito = Character.digit(json.charAt(posicion++), 16);
                if (digito < 0) {
                    throw formatoInvalido();
                }
                valor = (valor << 4) | digito;
            }
            return (char) valor;
        }

        private void espacios() {
            while (posicion < json.length()) {
                char caracter = json.charAt(posicion);
                if (caracter != ' ' && caracter != '\t' && caracter != '\n' && caracter != '\r') {
                    return;
                }
                posicion++;
            }
        }

        private boolean consumir(char esperado) {
            if (posicion < json.length() && json.charAt(posicion) == esperado) {
                posicion++;
                return true;
            }
            return false;
        }

        private void esperar(char esperado) {
            if (!consumir(esperado)) {
                throw formatoInvalido();
            }
        }

        private void terminar() {
            espacios();
            if (posicion != json.length()) {
                throw formatoInvalido();
            }
        }
    }
}
