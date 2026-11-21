package es.uclm.esi.iso2.B101.servidor.api;

import java.util.Optional;

public interface AutorizadorIncidencias {
    Optional<IdentidadPaciente> autenticar(String cabeceraAutorizacion);

    record IdentidadPaciente(boolean tieneRolPaciente, String pacienteId) {
    }
}
