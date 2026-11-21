package es.uclm.esi.iso2.B101.servidor.api;

import java.util.Optional;

public interface AutorizadorCitas {
    /**
     * Valida la cabecera con la autenticación real y devuelve la identidad asociada a la sesión.
     * Un Optional vacío representa una sesión inexistente o inválida.
     */
    Optional<IdentidadAutorizada> autenticar(String cabeceraAutorizacion);

    /**
     * pacienteId debe ser el vínculo de dominio obtenido de la identidad autenticada, no del token.
     */
    record IdentidadAutorizada(boolean tieneRolPaciente, String pacienteId) {
    }
}
