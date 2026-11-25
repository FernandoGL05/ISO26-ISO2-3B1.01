package es.uclm.esi.iso2.B101.servidor.api;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.EntradaListaEspera;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAsignacionListaEspera.ResultadoAsignacion;

final class JsonListaEspera {
    private static final char[] HEX =
            "0123456789abcdef".toCharArray();

    private JsonListaEspera() {
    }

    static String entrada(EntradaListaEspera entrada) {
        return "{\"id\":" + cadena(entrada.getId())
                + ",\"pacienteId\":" + cadena(entrada.getPacienteId())
                + ",\"medicoId\":" + cadena(entrada.getMedicoId())
                + ",\"tipoPrueba\":" + cadena(entrada.getTipoPrueba())
                + ",\"prioridad\":" + cadena(entrada.getPrioridad().name())
                + ",\"fechaEntrada\":"
                + cadena(entrada.getFechaEntrada().toString())
                + ",\"estado\":" + cadena(entrada.getEstado().name())
                + ",\"citaId\":" + cadena(entrada.getCitaId())
                + "}";
    }

    static String entradas(List<EntradaListaEspera> entradas) {
        Objects.requireNonNull(entradas, "entradas es obligatorio");

        return entradas.stream()
                .map(JsonListaEspera::entrada)
                .collect(Collectors.joining(",", "[", "]"));
    }

    static String asignacion(ResultadoAsignacion resultado) {
        return "{\"entrada\":" + entrada(resultado.entrada())
                + ",\"cita\":" + cita(resultado.cita())
                + "}";
    }

    static String error(String codigo, String mensaje) {
        return "{\"exito\":false,\"codigoError\":" + cadena(codigo)
                + ",\"mensaje\":" + cadena(mensaje)
                + "}";
    }

    private static String cita(Cita cita) {
        String duracion = cita.getDuracionMinutos().isPresent()
                ? Integer.toString(cita.getDuracionMinutos().getAsInt())
                : "null";

        return "{\"id\":" + cadena(cita.getId())
                + ",\"pacienteId\":" + cadena(cita.getPacienteId())
                + ",\"medicoId\":" + cadena(cita.getMedicoId())
                + ",\"tipoPrueba\":" + cadena(cita.getTipoPrueba())
                + ",\"fechaHora\":" + cadena(cita.getFechaHora().toString())
                + ",\"duracionMinutos\":" + duracion
                + ",\"estado\":" + cadena(cita.getEstado().name())
                + "}";
    }

    static String cadena(String valor) {
        if (valor == null) {
            return "null";
        }

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
                    if (caracter < 0x20
                            || Character.isSurrogate(caracter)) {
                        json.append("\\u")
                                .append(HEX[(caracter >>> 12) & 0xf])
                                .append(HEX[(caracter >>> 8) & 0xf])
                                .append(HEX[(caracter >>> 4) & 0xf])
                                .append(HEX[caracter & 0xf]);
                    } else {
                        json.append(caracter);
                    }
                }
            }
        }

        return json.append('"').toString();
    }
}