package es.uclm.esi.iso2.B101.cliente.presentacion;

import es.uclm.esi.iso2.B101.cliente.comunicacion.ClienteAutenticacion;
import es.uclm.esi.iso2.B101.cliente.modelo.RespuestaLogin;
import es.uclm.esi.iso2.B101.cliente.modelo.SesionCliente;
import java.util.Objects;
import java.util.Optional;

/**
 * Controlador del lado cliente para el caso de uso CU01 — Iniciar sesión.
 * Valida la entrada local, delega la autenticación en el servidor y mantiene el estado de la sesión.
 */
public class ControladorInicioSesion {

    private final ClienteAutenticacion clienteAutenticacion;
    private SesionCliente sesionActual;

    public ControladorInicioSesion(ClienteAutenticacion clienteAutenticacion) {
        this.clienteAutenticacion = Objects.requireNonNull(clienteAutenticacion);
    }

    public RespuestaLogin iniciarSesion(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return RespuestaLogin.error("CREDENCIALES_VACIAS", "Debe introducir usuario y contraseña.");
        }
        RespuestaLogin respuesta = clienteAutenticacion.iniciarSesion(username.trim(), password);
        if (respuesta.exito() && respuesta.sesion() != null) {
            this.sesionActual = respuesta.sesion();
        }
        return respuesta;
    }

    public RespuestaLogin iniciarSesionSSO(String ssoToken) {
        if (ssoToken == null || ssoToken.isBlank()) {
            return RespuestaLogin.error("SSO_INVALIDO", "Debe proporcionar un token SSO corporativo.");
        }
        RespuestaLogin respuesta = clienteAutenticacion.iniciarSesionSSO(ssoToken.trim());
        if (respuesta.exito() && respuesta.sesion() != null) {
            this.sesionActual = respuesta.sesion();
        }
        return respuesta;
    }

    public Optional<SesionCliente> obtenerSesionActual() {
        return Optional.ofNullable(sesionActual);
    }

    public boolean verificarSesionActivaEnServidor() {
        if (sesionActual == null) {
            return false;
        }
        Optional<SesionCliente> validada = clienteAutenticacion.validarSesion(sesionActual.token());
        if (validada.isEmpty()) {
            sesionActual = null;
            return false;
        }
        sesionActual = validada.get();
        return true;
    }

    public boolean cerrarSesion() {
        if (sesionActual == null) {
            return false;
        }
        boolean cerrada = clienteAutenticacion.cerrarSesion(sesionActual.token());
        sesionActual = null;
        return cerrada;
    }
}
