package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAgendas.HuecoDisponible;
import java.util.List;

final class JsonAgendas {
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private JsonAgendas() {
    }

    static String disponibilidad(List<HuecoDisponible> huecos) {
        StringBuilder json = new StringBuilder("[");
        for (int indice = 0; indice < huecos.size(); indice++) {
            if (indice > 0) {
                json.append(',');
            }
            HuecoDisponible hueco = huecos.get(indice);
            json.append("{\"tipoPrueba\":").append(cadena(hueco.tipoPrueba()))
                    .append(",\"desde\":").append(cadena(hueco.desde().toString()))
                    .append(",\"hasta\":").append(cadena(hueco.hasta().toString()))
                    .append('}');
        }
        return json.append(']').toString();
    }

    static String error(String codigo, String mensaje) {
        return "{\"exito\":false,\"codigoError\":" + cadena(codigo) + ",\"mensaje\":" + cadena(mensaje) + "}";
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
