package es.uclm.esi.iso2.B101.servidor.api;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import es.uclm.esi.iso2.B101.servidor.dominio.AgendaDiaria;
import es.uclm.esi.iso2.B101.servidor.dominio.HuecoAgenda;

public final class JsonAgendas {

    private JsonAgendas() {
    }

    public static String agenda(AgendaDiaria agenda) {
        Objects.requireNonNull(agenda, "La agenda no puede ser nula");

        return "{"
                + "\"id\":" + texto(agenda.getId()) + ","
                + "\"medicoId\":" + texto(agenda.getMedicoId()) + ","
                + "\"fecha\":" + texto(agenda.getFecha().toString())
                + "}";
    }

    public static String agendas(List<AgendaDiaria> agendas) {
        Objects.requireNonNull(agendas, "La lista no puede ser nula");

        return agendas.stream()
                .map(JsonAgendas::agenda)
                .collect(Collectors.joining(",", "[", "]"));
    }

    public static String hueco(HuecoAgenda hueco) {
        Objects.requireNonNull(hueco, "El hueco no puede ser nulo");

        return "{"
                + "\"id\":" + texto(hueco.getId()) + ","
                + "\"agendaId\":" + texto(hueco.getAgendaId()) + ","
                + "\"horaInicio\":"
                + texto(hueco.getHoraInicio().toString()) + ","
                + "\"horaFin\":"
                + texto(hueco.getHoraFin().toString())
                + "}";
    }

    public static String huecos(List<HuecoAgenda> huecos) {
        Objects.requireNonNull(huecos, "La lista no puede ser nula");

        return huecos.stream()
                .map(JsonAgendas::hueco)
                .collect(Collectors.joining(",", "[", "]"));
    }

    public static String exito(String mensaje) {
        return "{\"exito\":true,\"mensaje\":" + texto(mensaje) + "}";
    }

    public static String error(String codigo, String mensaje) {
        return "{\"exito\":false,"
                + "\"codigoError\":" + texto(codigo) + ","
                + "\"mensaje\":" + texto(mensaje)
                + "}";
    }

    private static String texto(String valor) {
        Objects.requireNonNull(valor, "El texto no puede ser nulo");

        StringBuilder resultado = new StringBuilder("\"");

        for (int i = 0; i < valor.length(); i++) {
            char caracter = valor.charAt(i);

            switch (caracter) {
                case '"' -> resultado.append("\\\"");
                case '\\' -> resultado.append("\\\\");
                case '\b' -> resultado.append("\\b");
                case '\f' -> resultado.append("\\f");
                case '\n' -> resultado.append("\\n");
                case '\r' -> resultado.append("\\r");
                case '\t' -> resultado.append("\\t");
                default -> {
                    if (caracter < 0x20) {
                        resultado.append(String.format(
                                "\\u%04x", (int) caracter));
                    } else {
                        resultado.append(caracter);
                    }
                }
            }
        }

        return resultado.append('"').toString();
    }
}