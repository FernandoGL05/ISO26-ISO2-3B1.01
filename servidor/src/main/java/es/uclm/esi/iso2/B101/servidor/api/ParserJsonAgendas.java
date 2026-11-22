package es.uclm.esi.iso2.B101.servidor.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class ParserJsonAgendas {
    private final String json;
    private int posicion;

    private ParserJsonAgendas(String json) {
        if (json == null) {
            throw new IllegalArgumentException(
                    "El cuerpo JSON no puede ser nulo");
        }
        this.json = json;
    }

    static Map<String, String> leer(String json, String... campos) {
        Map<String, String> datos =
                new ParserJsonAgendas(json).leerObjeto();

        Set<String> esperados = Set.of(campos);
        if (!datos.keySet().equals(esperados)) {
            throw new IllegalArgumentException(
                    "El cuerpo JSON debe contener exactamente: "
                            + String.join(", ", campos));
        }

        if (datos.values().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(
                    "Los campos no pueden estar vacíos");
        }

        return Map.copyOf(datos);
    }

    private Map<String, String> leerObjeto() {
        Map<String, String> datos = new HashMap<>();

        espacios();
        esperar('{');
        espacios();

        if (!consumir('}')) {
            while (true) {
                espacios();
                String nombre = leerCadena();
                espacios();
                esperar(':');
                espacios();
                String valor = leerCadena();

                if (datos.putIfAbsent(nombre, valor) != null) {
                    throw new IllegalArgumentException(
                            "El cuerpo JSON contiene campos duplicados");
                }

                espacios();
                if (consumir('}')) {
                    break;
                }
                esperar(',');
            }
        }

        espacios();
        if (posicion != json.length()) {
            throw formatoInvalido();
        }

        return datos;
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
                case 'u' -> valor.append(leerUnicode());
                default -> throw formatoInvalido();
            }
        }

        throw formatoInvalido();
    }

    private char leerUnicode() {
        if (json.length() - posicion < 4) {
            throw formatoInvalido();
        }

        int valor = 0;
        for (int i = 0; i < 4; i++) {
            char caracter = json.charAt(posicion++);
            int digito = "0123456789abcdef".indexOf(
                    Character.toLowerCase(caracter));

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
            if (caracter != ' ' && caracter != '\t'
                    && caracter != '\n' && caracter != '\r') {
                break;
            }
            posicion++;
        }
    }

    private boolean consumir(char esperado) {
        if (posicion < json.length()
                && json.charAt(posicion) == esperado) {
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

    private IllegalArgumentException formatoInvalido() {
        return new IllegalArgumentException(
                "Se requiere un objeto JSON con valores de texto");
    }
}