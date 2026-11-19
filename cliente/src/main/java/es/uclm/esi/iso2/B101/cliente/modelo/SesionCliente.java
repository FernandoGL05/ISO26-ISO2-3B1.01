package es.uclm.esi.iso2.B101.cliente.modelo;

/**
 * Representación en el cliente de la sesión activa recibida del servidor.
 */
public record SesionCliente(
        String token,
        String usuarioId,
        String username,
        String nombreCompleto,
        String rol,
        String expiraEn,
        String origenAutenticacion
) {
}
