package es.uclm.esi.iso2.B101.servidor.api;

import java.util.Optional;

public interface AutorizadorInformesPrueba {
    Optional<Identidad> autenticar(String cabeceraAutorizacion);

    record Identidad(boolean tieneRolPaciente, String pacienteId, boolean tieneRolMedico, String medicoId) {
    }
}
