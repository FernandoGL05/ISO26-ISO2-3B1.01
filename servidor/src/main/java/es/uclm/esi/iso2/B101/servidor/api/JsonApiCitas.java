package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.SolicitudCita;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.List;

final class JsonApiCitas {
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private JsonApiCitas() {
    }

    static String citas(List<Cita> citas) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < citas.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            appendCita(json, citas.get(i));
        }
        return json.append(']').toString();
    }

    static String solicitudes(List<SolicitudCita> solicitudes) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < solicitudes.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            appendSolicitud(json, solicitudes.get(i));
        }
        return json.append(']').toString();
    }

    static String solicitud(SolicitudCita solicitud) {
        StringBuilder json = new StringBuilder();
        appendSolicitud(json, solicitud);
        return json.toString();
    }

    static String exito(String mensaje) {
        return "{\"exito\":true,\"mensaje\":" + cadena(mensaje) + "}";
    }

    static String error(String codigoError, String mensaje) {
        return "{\"exito\":false,\"codigoError\":" + cadena(codigoError)
                + ",\"mensaje\":" + cadena(mensaje) + "}";
    }

    static LocalDateTime parsearNuevaFechaHora(String json) {
        String fechaHora = new ParserJsonCambioFecha(json).leerFechaHora();
        if (fechaHora == null) {
            throw new IllegalArgumentException("nuevaFechaHora es obligatoria");
        }
        try {
            return LocalDateTime.parse(fechaHora);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("nuevaFechaHora debe tener formato ISO-8601", exception);
        }
    }

    private static void appendCita(StringBuilder json, Cita cita) {
        json.append("{\"id\":").append(cadena(cita.getId()))
                .append(",\"tipoPrueba\":").append(cadena(cita.getTipoPrueba()))
                .append(",\"fechaHora\":").append(cadena(cita.getFechaHora().toString()))
                .append(",\"estado\":").append(cadena(cita.getEstado().name()))
                .append('}');
    }

    private static void appendSolicitud(StringBuilder json, SolicitudCita solicitud) {
        json.append("{\"id\":").append(cadena(solicitud.getId()))
                .append(",\"citaId\":").append(cadena(solicitud.getCitaId()))
                .append(",\"pacienteId\":").append(cadena(solicitud.getPacienteId()))
                .append(",\"tipo\":").append(cadena(solicitud.getTipo().name()))
                .append(",\"nuevaFechaHora\":");
        if (solicitud.getNuevaFechaHora() == null) {
            json.append("null");
        } else {
            json.append(cadena(solicitud.getNuevaFechaHora().toString()));
        }
        json.append(",\"estado\":").append(cadena(solicitud.getEstado().name())).append('}');
    }

    private static String cadena(String valor) {
        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < valor.length(); i++) {
            char caracter = valor.charAt(i);
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
                                .append(HEX_DIGITS[(caracter >>> 12) & 0x0f])
                                .append(HEX_DIGITS[(caracter >>> 8) & 0x0f])
                                .append(HEX_DIGITS[(caracter >>> 4) & 0x0f])
                                .append(HEX_DIGITS[caracter & 0x0f]);
                    } else {
                        json.append(caracter);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    private static final class ParserJsonCambioFecha {
        private final String json;
        private int posicion;

        private ParserJsonCambioFecha(String json) {
            this.json = json;
        }

        private String leerFechaHora() {
            espacios();
            esperar('{');
            espacios();
            String nombre = leerCadena();
            espacios();
            esperar(':');
            espacios();

            String valor;
            if (json.startsWith("null", posicion)) {
                posicion += 4;
                valor = null;
            } else {
                valor = leerCadena();
            }

            espacios();
            esperar('}');
            espacios();
            if (posicion != json.length() || !"nuevaFechaHora".equals(nombre)) {
                throw formatoInvalido();
            }
            return valor;
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
            for (int i = 0; i < 4; i++) {
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
                    break;
                }
                posicion++;
            }
        }

        private void esperar(char esperado) {
            if (posicion >= json.length() || json.charAt(posicion) != esperado) {
                throw formatoInvalido();
            }
            posicion++;
        }

        private IllegalArgumentException formatoInvalido() {
            return new IllegalArgumentException("El cuerpo JSON debe contener nuevaFechaHora como cadena");
        }
    }
}
