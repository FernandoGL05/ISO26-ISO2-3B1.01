package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.PruebaRealizada;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RepositorioHistorialPruebasMemoria implements RepositorioHistorialPruebas {
    private final ConcurrentHashMap<String, PruebaRealizada> pruebas = new ConcurrentHashMap<>();

    @Override
    public synchronized void registrar(PruebaRealizada prueba) {
        if (prueba == null) {
            throw new IllegalArgumentException("La prueba no puede ser nula");
        }
        if (prueba.getCitaId() != null && buscarPorCitaId(prueba.getCitaId()).isPresent()) {
            throw new IllegalStateException("Ya existe una prueba realizada para esa cita");
        }
        if (pruebas.putIfAbsent(prueba.getId(), prueba) != null) {
            throw new IllegalStateException("Ya existe una prueba con ese identificador");
        }
    }

    @Override
    public Optional<PruebaRealizada> buscarPorId(String id) {
        validarIdentificador(id, "id");
        return Optional.ofNullable(pruebas.get(id));
    }

    @Override
    public List<PruebaRealizada> buscarPorPacienteId(String pacienteId) {
        validarIdentificador(pacienteId, "pacienteId");
        return pruebas.values().stream()
                .filter(prueba -> prueba.getPacienteId().equals(pacienteId))
                .toList();
    }

    @Override
    public Optional<PruebaRealizada> buscarPorCitaId(String citaId) {
        validarIdentificador(citaId, "citaId");
        return pruebas.values().stream()
                .filter(prueba -> citaId.equals(prueba.getCitaId()))
                .findFirst();
    }

    private static void validarIdentificador(String identificador, String nombre) {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
