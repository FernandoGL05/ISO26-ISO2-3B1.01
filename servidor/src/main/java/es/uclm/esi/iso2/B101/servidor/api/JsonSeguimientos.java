package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.SeguimientoPaciente;
import java.util.List;

final class JsonSeguimientos {
    private static final char[] DIGITOS_HEXADECIMALES = "0123456789abcdef".toCharArray();

    private JsonSeguimientos() {
    }

    static String serializarLista(List<SeguimientoPaciente> seguimientos) {
        StringBuilder json = new StringBuilder("[");
        for (int indice = 0; indice < seguimientos.size(); indice++) {
            if (indice > 0) {
                json.append(',');
            }
            appendSeguimiento(json, seguimientos.get(indice));
        }
        return json.append(']').toString();
    }

    static String serializar(SeguimientoPaciente seguimiento) {
        StringBuilder json = new StringBuilder();
        appendSeguimiento(json, seguimiento);
        return json.toString();
    }

    static String error(String codigo, String mensaje) {
        return "{\"exito\":false,\"codigoError\":" + cadena(codigo)
                + ",\"mensaje\":" + cadena(mensaje) + "}";
    }

    private static void appendSeguimiento(StringBuilder json, SeguimientoPaciente seguimiento) {
        json.append("{\"id\":").append(cadena(seguimiento.getId()))
                .append(",\"tipoPrueba\":").append(cadena(seguimiento.getTipoPrueba()))
                .append(",\"descripcion\":").append(cadena(seguimiento.getDescripcion()))
                .append(",\"periodicidadMeses\":");
        if (seguimiento.getPeriodicidadMeses() == null) {
            json.append("null");
        } else {
            json.append(seguimiento.getPeriodicidadMeses());
        }
        json.append(",\"fechaUltimaPrueba\":");
        if (seguimiento.getFechaUltimaPrueba() == null) {
            json.append("null");
        } else {
            json.append(cadena(seguimiento.getFechaUltimaPrueba().toString()));
        }
        json.append(",\"fechaProximaRevision\":");
        if (seguimiento.getFechaProximaRevision() == null) {
            json.append("null");
        } else {
            json.append(cadena(seguimiento.getFechaProximaRevision().toString()));
        }
        json.append(",\"observacionesPreparacion\":");
        if (seguimiento.getObservacionesPreparacion() == null) {
            json.append("null");
        } else {
            json.append(cadena(seguimiento.getObservacionesPreparacion()));
        }
        json.append('}');
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
