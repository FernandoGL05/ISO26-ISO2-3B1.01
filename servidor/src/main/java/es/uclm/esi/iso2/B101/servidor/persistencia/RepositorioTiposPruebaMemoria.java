package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.TipoPrueba;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RepositorioTiposPruebaMemoria implements RepositorioTiposPrueba {

    private final Map<String, TipoPrueba> almacenamiento = new ConcurrentHashMap<>();

    @Override
    public void guardar(TipoPrueba tipoPrueba) {
        Objects.requireNonNull(tipoPrueba, "tipoPrueba no puede ser nulo");
        almacenamiento.put(tipoPrueba.getId(), tipoPrueba);
    }

    @Override
    public Optional<TipoPrueba> buscarPorId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(almacenamiento.get(id.trim()));
    }

    @Override
    public Optional<TipoPrueba> buscarPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Optional.empty();
        }
        String buscado = nombre.trim().toLowerCase(Locale.ROOT);
        return almacenamiento.values().stream()
                .filter(tipo -> tipo.getNombre().toLowerCase(Locale.ROOT).equals(buscado))
                .findFirst();
    }

    @Override
    public List<TipoPrueba> listarTodos() {
        return almacenamiento.values().stream()
                .sorted(Comparator.comparing(TipoPrueba::getId))
                .toList();
    }

    @Override
    public List<TipoPrueba> listarActivos() {
        return almacenamiento.values().stream()
                .filter(TipoPrueba::isActivo)
                .sorted(Comparator.comparing(TipoPrueba::getId))
                .toList();
    }
}
