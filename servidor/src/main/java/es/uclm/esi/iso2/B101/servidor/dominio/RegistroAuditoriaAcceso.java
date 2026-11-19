package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.Instant;

/**
 * Registro de auditoría de intentos de acceso al sistema (base para RF25).
 */
public record RegistroAuditoriaAcceso(
        Instant fechaHora,
        String username,
        boolean exito,
        String motivo,
        String origen
) {
}
