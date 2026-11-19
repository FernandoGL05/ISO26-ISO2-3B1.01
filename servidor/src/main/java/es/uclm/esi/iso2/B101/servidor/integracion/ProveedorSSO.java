package es.uclm.esi.iso2.B101.servidor.integracion;

import java.util.Optional;

/**
 * Puerto de integración con el SSO corporativo del SESCAM (RT05).
 * Permite sustituir la implementación simulada por un adaptador real (OIDC/SAML/LDAP)
 * sin modificar la lógica de autenticación.
 */
public interface ProveedorSSO {

    /**
     * Valida un token emitido por el SSO corporativo y devuelve el nombre de usuario asociado.
     *
     * @param ssoToken token proporcionado por el sistema externo SSO
     * @return nombre de usuario si el token es válido, o vacío en caso contrario
     */
    Optional<String> validarTokenSSO(String ssoToken);
}
