package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.Protocolo;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RepositorioProtocolosMemoria implements RepositorioProtocolos {

    private final Map<String, Protocolo> almacenamiento = new ConcurrentHashMap<>();

    @Override
    public void guardar(Protocolo protocolo) {
        Objects.requireNonNull(protocolo, "protocolo no puede ser nulo");
        almacenamiento.put(protocolo.getId(), protocolo);
    }

    @Override
    public Optional<Protocolo> buscarPorId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(almacenamiento.get(id.trim()));
    }

    @Override
    public Optional<Protocolo> buscarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        String buscado = codigo.trim().toLowerCase(Locale.ROOT);
        return almacenamiento.values().stream()
                .filter(p -> p.getCodigo().toLowerCase(Locale.ROOT).equals(buscado))
                .findFirst();
    }

    @Override
    public List<Protocolo> buscarPorTipoPruebaId(String tipoPruebaId) {
        if (tipoPruebaId == null || tipoPruebaId.isBlank()) {
            return List.of();
        }
        String buscado = tipoPruebaId.trim();
        return almacenamiento.values().stream()
                .filter(p -> p.getTipoPruebaId().equals(buscado))
                .sorted(Comparator.comparing(Protocolo::getId))
                .toList();
    }

    @Override
    public List<Protocolo> listarTodos() {
        return almacenamiento.values().stream()
                .sorted(Comparator.comparing(Protocolo::getId))
                .toList();
    }
}
