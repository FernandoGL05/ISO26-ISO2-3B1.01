package es.uclm.esi.iso2.B101.servidor.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class ParserJsonListaEspera {
    private final String json;
    private int posicion;

    private ParserJsonListaEspera(String json) {
        if (json == null) {
            throw new IllegalArgumentException(
                    "El cuerpo JSON es obligatorio");
        }
        this.json = json;
    }

    static Map<String, Object> leer(String json, String... campos) {
        Map<String, Object> resultado =
                new ParserJsonListaEspera(json).leerObjeto();

        if (!resultado.keySet().equals(Set.of(campos))) {
            throw new IllegalArgumentException(
                    "El JSON no contiene exactamente los campos esperados");
        }

        return Map.copyOf(resultado);
    }

    static String texto(Map<String, Object> campos, String nombre) {
        Object valor = campos.get(nombre);

        if (!(valor instanceof String cadena) || cadena.isBlank()) {
            throw new IllegalArgumentException(
                    nombre + " debe ser una cadena no vacia");
        }

        return cadena;
    }

    static int entero(Map<String, Object> campos, String nombre) {
        Object valor = campos.get(nombre);

        if (!(valor instanceof Integer numero)) {
            throw new IllegalArgumentException(
                    nombre + " debe ser un numero entero");
        }

        return numero;
    }

    private Map<String, Object> leerObjeto() {
        Map<String, Object> campos = new LinkedHashMap<>();

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

                Object valor = actualEs('"')
                        ? leerCadena()
                        : leerEntero();

                if (campos.putIfAbsent(nombre, valor) != null) {
                    throw new IllegalArgumentException(
                            "El JSON contiene un campo repetido");
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

        return campos;
    }

    private String leerCadena() {
        esperar('"');
        StringBuilder valor = new StringBuilder();

        while (posicion < json.length()) {
            char caracter = json.charAt(posicion++);

            if (caracter == '"') {
                String resultado = valor.toString();
                validarUnicode(resultado);
                return resultado;
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

    private void validarUnicode(String valor) {
        for (int i = 0; i < valor.length(); i++) {
            char caracter = valor.charAt(i);

            if (Character.isHighSurrogate(caracter)) {
                if (i + 1 >= valor.length()
                        || !Character.isLowSurrogate(valor.charAt(i + 1))) {
                    throw formatoInvalido();
                }
                i++;
            } else if (Character.isLowSurrogate(caracter)) {
                throw formatoInvalido();
            }
        }
    }

    private int leerEntero() {
        int inicio = posicion;
        consumir('-');

        if (consumir('0')) {
            if (esDigitoActual()) {
                throw formatoInvalido();
            }
        } else {
            if (!esDigitoActual()) {
                throw formatoInvalido();
            }

            while (esDigitoActual()) {
                posicion++;
            }
        }

        try {
            return Integer.parseInt(json.substring(inicio, posicion));
        } catch (NumberFormatException error) {
            throw formatoInvalido();
        }
    }

    private boolean esDigitoActual() {
        return posicion < json.length()
                && json.charAt(posicion) >= '0'
                && json.charAt(posicion) <= '9';
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

    private boolean actualEs(char caracter) {
        return posicion < json.length()
                && json.charAt(posicion) == caracter;
    }

    private boolean consumir(char caracter) {
        if (!actualEs(caracter)) {
            return false;
        }

        posicion++;
        return true;
    }

    private void esperar(char caracter) {
        if (!consumir(caracter)) {
            throw formatoInvalido();
        }
    }

    private IllegalArgumentException formatoInvalido() {
        return new IllegalArgumentException(
                "JSON invalido: se espera un objeto con cadenas o enteros");
    }
}