package es.uclm.esi.iso2.B101.servidor.api;

import java.util.Optional;

public interface AutorizadorPrescripcionesSeguimiento {
    Optional<IdentidadMedico> autenticar(String cabeceraAutorizacion);

    record IdentidadMedico(boolean tieneRolMedico, String medicoId) {
    }
}
