package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.Notificacion;
import java.util.List;

final class JsonNotificaciones {
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private JsonNotificaciones() {
    }

    static String lista(List<Notificacion> notificaciones) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < notificaciones.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            appendNotificacion(json, notificaciones.get(i));
        }
        return json.append(']').toString();
    }

    static String notificacion(Notificacion notificacion) {
        StringBuilder json = new StringBuilder();
        appendNotificacion(json, notificacion);
        return json.toString();
    }

    static String error(String codigo, String mensaje) {
        return "{\"exito\":false,\"codigoError\":" + cadena(codigo)
                + ",\"mensaje\":" + cadena(mensaje) + "}";
    }

    private static void appendNotificacion(StringBuilder json, Notificacion notificacion) {
        json.append("{\"id\":").append(cadena(notificacion.getId()))
                .append(",\"pacienteId\":").append(cadena(notificacion.getPacienteId()))
                .append(",\"tipo\":").append(cadena(notificacion.getTipo().name()))
                .append(",\"mensaje\":").append(cadena(notificacion.getMensaje()))
                .append(",\"referenciaId\":").append(cadena(notificacion.getReferenciaId()))
                .append(",\"fechaHoraCreacion\":").append(cadena(notificacion.getFechaHoraCreacion().toString()))
                .append(",\"momentoAviso\":");
        if (notificacion.getMomentoAviso() == null) {
            json.append("null");
        } else {
            json.append(cadena(notificacion.getMomentoAviso().toString()));
        }
        json.append(",\"leida\":").append(notificacion.estaLeida()).append('}');
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
}
