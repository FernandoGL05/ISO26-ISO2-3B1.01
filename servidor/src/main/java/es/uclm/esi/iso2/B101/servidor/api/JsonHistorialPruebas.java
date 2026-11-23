package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.PruebaRealizada;
import java.util.List;

final class JsonHistorialPruebas {
    private static final char[] DIGITOS_HEXADECIMALES = "0123456789abcdef".toCharArray();

    private JsonHistorialPruebas() {
    }

    static String serializar(List<PruebaRealizada> pruebas) {
        StringBuilder json = new StringBuilder("[");
        for (int indice = 0; indice < pruebas.size(); indice++) {
            if (indice > 0) {
                json.append(',');
            }
            PruebaRealizada prueba = pruebas.get(indice);
            json.append("{\"id\":").append(cadena(prueba.getId()))
                    .append(",\"tipoPrueba\":").append(cadena(prueba.getTipoPrueba()))
                    .append(",\"fechaHoraRealizacion\":")
                    .append(cadena(prueba.getFechaHoraRealizacion().toString()))
                    .append(",\"citaId\":");
            if (prueba.getCitaId() == null) {
                json.append("null");
            } else {
                json.append(cadena(prueba.getCitaId()));
            }
            json.append(",\"solicitudId\":");
            if (prueba.getSolicitudId() == null) {
                json.append("null");
            } else {
                json.append(cadena(prueba.getSolicitudId()));
            }
            json.append('}');
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
}
