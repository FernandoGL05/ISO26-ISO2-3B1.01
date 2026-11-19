package es.uclm.esi.iso2.B101.servidor.seguridad;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Utilidad criptográfica para generación de salt y hash seguro de contraseñas mediante PBKDF2-HMAC-SHA256.
 */
public class GestorContrasenas {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final int ITERACIONES = 65_536;
    private static final int LONGITUD_CLAVE_BITS = 256;
    private static final int LONGITUD_SALT_BYTES = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generarSalt() {
        byte[] salt = new byte[LONGITUD_SALT_BYTES];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public String calcularHash(String contrasena, String saltBase64) {
        if (contrasena == null || saltBase64 == null) {
            throw new IllegalArgumentException("La contraseña y el salt no pueden ser nulos");
        }
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        PBEKeySpec spec = new PBEKeySpec(contrasena.toCharArray(), salt, ITERACIONES, LONGITUD_CLAVE_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITMO);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Error al calcular el hash de la contraseña", e);
        } finally {
            spec.clearPassword();
        }
    }

    public boolean verificar(String contrasenaPlana, String saltBase64, String hashEsperadoBase64) {
        if (contrasenaPlana == null || saltBase64 == null || hashEsperadoBase64 == null) {
            return false;
        }
        String hashCalculado = calcularHash(contrasenaPlana, saltBase64);
        byte[] calculadoBytes = Base64.getDecoder().decode(hashCalculado);
        byte[] esperadoBytes = Base64.getDecoder().decode(hashEsperadoBase64);
        return MessageDigest.isEqual(calculadoBytes, esperadoBytes);
    }
}
