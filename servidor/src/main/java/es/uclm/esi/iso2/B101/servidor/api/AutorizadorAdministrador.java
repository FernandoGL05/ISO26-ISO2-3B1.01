package es.uclm.esi.iso2.B101.servidor.api;

import java.util.Optional;

/**
 * Contrato de autorización para operaciones administrativas (CU20, CU21, CU22).
 */
public interface AutorizadorAdministrador {

    Optional<IdentidadAdministrador> autenticar(String cabeceraAutorizacion);

    record IdentidadAdministrador(boolean tieneRolAdministrador, String administradorId) {
    }
}
