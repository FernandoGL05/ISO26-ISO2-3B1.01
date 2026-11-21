package es.uclm.esi.iso2.B101.servidor.api;

import java.util.Optional;

public interface AutorizadorNotificaciones {
    Optional<IdentidadPaciente> autenticar(String cabeceraAutorizacion);

    record IdentidadPaciente(boolean tieneRolPaciente, String pacienteId) {
    }
}
