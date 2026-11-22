package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.RespuestaEncuestaSatisfaccion;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RepositorioRespuestasEncuestasMemoria implements RepositorioRespuestasEncuestas {
    private final ConcurrentHashMap<String, RespuestaEncuestaSatisfaccion> respuestasPorId =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ClaveEncuestaPaciente, RespuestaEncuestaSatisfaccion> respuestasPorPaciente =
            new ConcurrentHashMap<>();

    @Override
    public synchronized boolean guardarSiNoExiste(RespuestaEncuestaSatisfaccion respuesta) {
        if (respuesta == null) {
            throw new IllegalArgumentException("La respuesta no puede ser nula");
        }
        ClaveEncuestaPaciente clave = new ClaveEncuestaPaciente(respuesta.getEncuestaId(), respuesta.getPacienteId());
        if (respuestasPorPaciente.containsKey(clave) || respuestasPorId.containsKey(respuesta.getId())) {
            return false;
        }
        respuestasPorId.put(respuesta.getId(), respuesta);
        respuestasPorPaciente.put(clave, respuesta);
        return true;
    }

    @Override
    public Optional<RespuestaEncuestaSatisfaccion> buscarPorEncuestaYPaciente(String encuestaId, String pacienteId) {
        validarIdentificador(encuestaId, "encuestaId");
        validarIdentificador(pacienteId, "pacienteId");
        return Optional.ofNullable(respuestasPorPaciente.get(new ClaveEncuestaPaciente(encuestaId, pacienteId)));
    }

    @Override
    public List<RespuestaEncuestaSatisfaccion> buscarPorPaciente(String pacienteId) {
        validarIdentificador(pacienteId, "pacienteId");
        return respuestasPorPaciente.values().stream()
                .filter(respuesta -> respuesta.getPacienteId().equals(pacienteId))
                .sorted(Comparator.comparing(RespuestaEncuestaSatisfaccion::getFechaRespuesta)
                        .thenComparing(RespuestaEncuestaSatisfaccion::getId))
                .toList();
    }

    private static void validarIdentificador(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }

    private record ClaveEncuestaPaciente(String encuestaId, String pacienteId) {
    }
}
