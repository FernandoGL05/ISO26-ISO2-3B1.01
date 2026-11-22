package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.EncuestaSatisfaccion;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RepositorioEncuestasSatisfaccionMemoria implements RepositorioEncuestasSatisfaccion {
    private final ConcurrentHashMap<String, EncuestaSatisfaccion> encuestas = new ConcurrentHashMap<>();

    @Override
    public void guardar(EncuestaSatisfaccion encuesta) {
        if (encuesta == null) {
            throw new IllegalArgumentException("La encuesta no puede ser nula");
        }
        if (encuestas.putIfAbsent(encuesta.getId(), encuesta) != null) {
            throw new IllegalStateException("Ya existe una encuesta con ese identificador");
        }
    }

    @Override
    public Optional<EncuestaSatisfaccion> buscarPorId(String id) {
        validarIdentificador(id);
        return Optional.ofNullable(encuestas.get(id));
    }

    @Override
    public List<EncuestaSatisfaccion> buscarDisponibles() {
        return encuestas.values().stream()
                .filter(EncuestaSatisfaccion::estaDisponible)
                .sorted(Comparator.comparing(EncuestaSatisfaccion::getId))
                .toList();
    }

    private static void validarIdentificador(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id no puede ser nulo ni estar vacío");
        }
    }
}
