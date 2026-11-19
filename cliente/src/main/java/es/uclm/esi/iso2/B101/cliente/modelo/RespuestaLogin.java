package es.uclm.esi.iso2.B101.cliente.modelo;

import java.util.Optional;

/**
 * Respuesta devuelta al cliente tras solicitar un inicio de sesión (CU01).
 */
public record RespuestaLogin(
        boolean exito,
        String codigoError,
        String mensaje,
        SesionCliente sesion
) {
    public static RespuestaLogin correcta(String mensaje, SesionCliente sesion) {
        return new RespuestaLogin(true, null, mensaje, sesion);
    }

    public static RespuestaLogin error(String codigoError, String mensaje) {
        return new RespuestaLogin(false, codigoError, mensaje, null);
    }

    public Optional<SesionCliente> obtenerSesion() {
        return Optional.ofNullable(sesion);
    }
}
