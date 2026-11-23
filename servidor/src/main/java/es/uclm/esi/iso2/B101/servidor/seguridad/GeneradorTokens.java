package es.uclm.esi.iso2.B101.servidor.seguridad;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generador criptográficamente seguro de tokens de sesión.
 */
public class GeneradorTokens {

    private static final int LONGITUD_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generarToken() {
        byte[] bytes = new byte[LONGITUD_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
