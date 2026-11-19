package es.uclm.esi.iso2.B101.servidor.integracion;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación simulada del proveedor SSO corporativo para desarrollo y pruebas.
 */
public class ProveedorSSOSimulado implements ProveedorSSO {

    private final Map<String, String> tokensRegistrados = new ConcurrentHashMap<>();

    public ProveedorSSOSimulado() {
        registrarToken("sso-sescam-paciente1", "paciente1");
        registrarToken("sso-sescam-medico1", "medico1");
        registrarToken("sso-sescam-admision1", "admision1");
        registrarToken("sso-sescam-admin1", "admin1");
    }

    public void registrarToken(String ssoToken, String username) {
        tokensRegistrados.put(ssoToken, username);
    }

    @Override
    public Optional<String> validarTokenSSO(String ssoToken) {
        if (ssoToken == null || ssoToken.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tokensRegistrados.get(ssoToken.trim()));
    }
}
