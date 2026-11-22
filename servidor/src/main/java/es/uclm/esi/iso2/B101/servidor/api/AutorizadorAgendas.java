package es.uclm.esi.iso2.B101.servidor.api;

import java.util.Optional;

@FunctionalInterface
public interface AutorizadorAgendas {

    Optional<IdentidadAutorizada> autenticar(
            String cabeceraAutorizacion);

    record IdentidadAutorizada(
            String usuarioId,
            boolean tieneRolAdmision) {

        public IdentidadAutorizada {
            if (usuarioId == null || usuarioId.isBlank()) {
                throw new IllegalArgumentException(
                        "usuarioId no puede ser nulo ni estar vacío");
            }
        }
    }
}