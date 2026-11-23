package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.PruebaRealizada;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class JsonRealizacionPruebas {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private JsonRealizacionPruebas() {
    }

    static DatosRegistro parsear(String json) {
        Map<String, String> campos = new Parser(json).leerObjeto();
        if (!Set.of("citaId", "resultado", "descripcion").containsAll(campos.keySet())
                || !campos.keySet().containsAll(Set.of("citaId", "resultado"))) {
            throw formatoInvalido();
        }
        return new DatosRegistro(campos.get("citaId"), campos.get("resultado"), campos.get("descripcion"));
    }

    static String prueba(PruebaRealizada prueba) {
        return new StringBuilder("{\"tipoResultado\":\"PRUEBA_REALIZADA\",\"id\":")
                .append(cadena(prueba.getId()))
                .append(",\"citaId\":").append(cadena(prueba.getCitaId()))
                .append(",\"pacienteId\":").append(cadena(prueba.getPacienteId()))
                .append(",\"tipoPrueba\":").append(cadena(prueba.getTipoPrueba()))
                .append(",\"fechaHoraRealizacion\":").append(cadena(prueba.getFechaHoraRealizacion().toString()))
                .append('}')
                .toString();
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
                                .append(HEX[(caracter >>> 12) & 0x0f])
                                .append(HEX[(caracter >>> 8) & 0x0f])
                                .append(HEX[(caracter >>> 4) & 0x0f])
                                .append(HEX[caracter & 0x0f]);
                    } else {
                        json.append(caracter);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    private static IllegalArgumentException formatoInvalido() {
        return new IllegalArgumentException("El cuerpo debe incluir citaId y resultado");
    }

    record DatosRegistro(String citaId, String resultado, String descripcion) {
    }

    private static final class Parser {
        private final String json;
        private int posicion;

        private Parser(String json) {
            if (json == null) {
                throw formatoInvalido();
            }
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
            while (true) {
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
            }
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
                if (caracter != ' ' && caracter != '\t' && caracter != '\r' && caracter != '\n') {
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
