package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.Incidencia;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RepositorioIncidenciasMemoria implements RepositorioIncidencias {
    private final ConcurrentHashMap<String, Incidencia> incidencias = new ConcurrentHashMap<>();

    @Override
    public void guardar(Incidencia incidencia) {
        if (incidencia == null) {
            throw new IllegalArgumentException("La incidencia no puede ser nula");
        }
        if (incidencias.putIfAbsent(incidencia.getId(), incidencia) != null) {
            throw new IllegalStateException("Ya existe una incidencia con ese identificador");
        }
    }

    @Override
    public Optional<Incidencia> buscarPorId(String id) {
        validarIdentificador(id, "id");
        return Optional.ofNullable(incidencias.get(id));
    }

    @Override
    public List<Incidencia> buscarPorPacienteId(String pacienteId) {
        validarIdentificador(pacienteId, "pacienteId");
        return incidencias.values().stream()
                .filter(incidencia -> incidencia.getPacienteId().equals(pacienteId))
                .toList();
    }

    private static void validarIdentificador(String identificador, String nombre) {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
