package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.SolicitudPruebaDiagnostica;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RepositorioPrescripcionesDiagnosticasMemoria
        implements RepositorioPrescripcionesDiagnosticas {
    private final ConcurrentHashMap<String, SolicitudPruebaDiagnostica> solicitudes = new ConcurrentHashMap<>();

    @Override
    public void guardar(SolicitudPruebaDiagnostica solicitud) {
        if (solicitud == null) {
            throw new IllegalArgumentException("La solicitud no puede ser nula");
        }
        if (solicitudes.putIfAbsent(solicitud.getId(), solicitud) != null) {
            throw new IllegalStateException("Ya existe una solicitud con ese identificador");
        }
    }

    @Override
    public Optional<SolicitudPruebaDiagnostica> buscarPorId(String id) {
        validarIdentificador(id, "id");
        return Optional.ofNullable(solicitudes.get(id));
    }

    @Override
    public List<SolicitudPruebaDiagnostica> buscarPorMedicoId(String medicoId) {
        validarIdentificador(medicoId, "medicoId");
        return solicitudes.values().stream()
                .filter(solicitud -> solicitud.getMedicoId().equals(medicoId))
                .toList();
    }

    private static void validarIdentificador(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
