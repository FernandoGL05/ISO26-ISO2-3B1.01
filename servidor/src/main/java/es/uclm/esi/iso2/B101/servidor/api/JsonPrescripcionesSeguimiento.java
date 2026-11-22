package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.NecesidadCitaSeguimiento;
import es.uclm.esi.iso2.B101.servidor.dominio.PrescripcionSeguimiento;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JsonPrescripcionesSeguimiento {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final Set<String> CAMPOS_CREACION = Set.of(
            "pacienteId",
            "tipoPrueba",
            "motivoClinico",
            "periodicidadMeses",
            "fechaPrimeraRevision",
            "prioridad");

    private JsonPrescripcionesSeguimiento() {
    }

    static DatosCreacion parsearCreacion(String json) {
        Map<String, Object> campos = new Parser(json).leerObjeto();
        if (!campos.keySet().equals(CAMPOS_CREACION)) {
            throw formatoInvalido();
        }
        Object periodicidad = campos.get("periodicidadMeses");
        if (!(periodicidad instanceof Integer meses)) {
            throw formatoInvalido();
        }
        return new DatosCreacion(
                texto(campos, "pacienteId"),
                texto(campos, "tipoPrueba"),
                texto(campos, "motivoClinico"),
                meses,
                texto(campos, "fechaPrimeraRevision"),
                texto(campos, "prioridad"));
    }

    static String prescripcion(PrescripcionSeguimiento prescripcion) {
        return new StringBuilder("{\"id\":")
                .append(cadena(prescripcion.getId()))
                .append(",\"medicoId\":").append(cadena(prescripcion.getMedicoId()))
                .append(",\"pacienteId\":").append(cadena(prescripcion.getPacienteId()))
                .append(",\"tipoPrueba\":").append(cadena(prescripcion.getTipoPrueba()))
                .append(",\"motivoClinico\":").append(cadena(prescripcion.getMotivoClinico()))
                .append(",\"periodicidadMeses\":").append(prescripcion.getPeriodicidadMeses())
                .append(",\"fechaPrimeraRevision\":").append(cadena(prescripcion.getFechaPrimeraRevision().toString()))
                .append(",\"prioridad\":").append(cadena(prescripcion.getPrioridad().name()))
                .append(",\"fechaCreacion\":").append(cadena(prescripcion.getFechaCreacion().toString()))
                .append(",\"estado\":").append(cadena(prescripcion.getEstado().name()))
                .append('}')
                .toString();
    }

    static String listaPrescripciones(List<PrescripcionSeguimiento> prescripciones) {
        StringBuilder json = new StringBuilder("[");
        for (int indice = 0; indice < prescripciones.size(); indice++) {
            if (indice > 0) {
                json.append(',');
            }
            json.append(prescripcion(prescripciones.get(indice)));
        }
        return json.append(']').toString();
    }

    static String listaNecesidades(List<NecesidadCitaSeguimiento> necesidades) {
        StringBuilder json = new StringBuilder("[");
        for (int indice = 0; indice < necesidades.size(); indice++) {
            if (indice > 0) {
                json.append(',');
            }
            NecesidadCitaSeguimiento necesidad = necesidades.get(indice);
            json.append("{\"id\":").append(cadena(necesidad.getId()))
                    .append(",\"prescripcionId\":").append(cadena(necesidad.getPrescripcionId()))
                    .append(",\"medicoId\":").append(cadena(necesidad.getMedicoId()))
                    .append(",\"pacienteId\":").append(cadena(necesidad.getPacienteId()))
                    .append(",\"tipoPrueba\":").append(cadena(necesidad.getTipoPrueba()))
                    .append(",\"fechaPrevista\":").append(cadena(necesidad.getFechaPrevista().toString()))
                    .append(",\"estado\":").append(cadena(necesidad.getEstado().name()))
                    .append(",\"citaId\":null}");
        }
        return json.append(']').toString();
    }

    static String error(String codigo, String mensaje) {
        return "{\"exito\":false,\"codigoError\":" + cadena(codigo)
                + ",\"mensaje\":" + cadena(mensaje) + "}";
    }

    private static String texto(Map<String, Object> campos, String nombre) {
        Object valor = campos.get(nombre);
        if (!(valor instanceof String texto)) {
            throw formatoInvalido();
        }
        return texto;
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
        return new IllegalArgumentException("El cuerpo JSON no contiene los campos requeridos");
    }

    record DatosCreacion(
            String pacienteId,
            String tipoPrueba,
            String motivoClinico,
            int periodicidadMeses,
            String fechaPrimeraRevision,
            String prioridad) {
    }

    private static final class Parser {
        private final String json;
        private int posicion;

        private Parser(String json) {
            this.json = json;
        }

        private Map<String, Object> leerObjeto() {
            espacios();
            esperar('{');
            espacios();
            Map<String, Object> campos = new HashMap<>();
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
                Object valor = posicion < json.length() && json.charAt(posicion) == '"'
                        ? leerCadena()
                        : leerEntero();
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
                    case 'u' -> valor.append(leerUnicode());
                    default -> throw formatoInvalido();
                }
            }
            throw formatoInvalido();
        }

        private Integer leerEntero() {
            int inicio = posicion;
            if (posicion < json.length() && json.charAt(posicion) == '-') {
                posicion++;
            }
            if (posicion >= json.length() || !esDigito(json.charAt(posicion))) {
                throw formatoInvalido();
            }
            if (json.charAt(posicion) == '0') {
                posicion++;
                if (posicion < json.length() && esDigito(json.charAt(posicion))) {
                    throw formatoInvalido();
                }
            } else {
                while (posicion < json.length() && esDigito(json.charAt(posicion))) {
                    posicion++;
                }
            }
            try {
                return Integer.valueOf(json.substring(inicio, posicion));
            } catch (NumberFormatException error) {
                throw formatoInvalido();
            }
        }

        private char leerUnicode() {
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
                if (caracter != ' ' && caracter != '\t' && caracter != '\n' && caracter != '\r') {
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

        private boolean esDigito(char caracter) {
            return caracter >= '0' && caracter <= '9';
        }
    }
}
