package es.uclm.esi.iso2.B101.servidor.dominio;

import java.util.Optional;

/**
 * Resultado de un intento de inicio de sesión (CU01).
 */
public class ResultadoAutenticacion {

    public static final String ERROR_CREDENCIALES_VACIAS = "CREDENCIALES_VACIAS";
    public static final String ERROR_CREDENCIALES_INVALIDAS = "CREDENCIALES_INVALIDAS";
    public static final String ERROR_CUENTA_INACTIVA = "CUENTA_INACTIVA";
    public static final String ERROR_CUENTA_BLOQUEADA = "CUENTA_BLOQUEADA";
    public static final String ERROR_SSO_INVALIDO = "SSO_INVALIDO";

    private final boolean exito;
    private final String codigoError;
    private final String mensaje;
    private final Sesion sesion;

    private ResultadoAutenticacion(boolean exito, String codigoError, String mensaje, Sesion sesion) {
        this.exito = exito;
        this.codigoError = codigoError;
        this.mensaje = mensaje;
        this.sesion = sesion;
    }

    public static ResultadoAutenticacion exito(Sesion sesion) {
        return new ResultadoAutenticacion(true, null, "Inicio de sesión correcto.", sesion);
    }

    public static ResultadoAutenticacion error(String codigoError, String mensaje) {
        return new ResultadoAutenticacion(false, codigoError, mensaje, null);
    }

    public boolean isExito() {
        return exito;
    }

    public Optional<String> getCodigoError() {
        return Optional.ofNullable(codigoError);
    }

    public String getMensaje() {
        return mensaje;
    }

    public Optional<Sesion> getSesion() {
        return Optional.ofNullable(sesion);
    }
}
