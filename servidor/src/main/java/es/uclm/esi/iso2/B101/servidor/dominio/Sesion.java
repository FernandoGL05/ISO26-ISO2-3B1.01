package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * Sesión autenticada de un usuario en el sistema.
 */
public class Sesion {

    public static final String ORIGEN_LOCAL = "LOCAL";
    public static final String ORIGEN_SSO = "SSO_CORPORATIVO";

    private final String token;
    private final String usuarioId;
    private final String username;
    private final String nombreCompleto;
    private final Rol rol;
    private final Instant creadaEn;
    private final Instant expiraEn;
    private final String origenAutenticacion;
    private boolean activa;

    public Sesion(String token, String usuarioId, String username, String nombreCompleto,
                  Rol rol, Instant creadaEn, Instant expiraEn, String origenAutenticacion) {
        this.token = Objects.requireNonNull(token, "El token no puede ser nulo");
        this.usuarioId = Objects.requireNonNull(usuarioId, "El usuarioId no puede ser nulo");
        this.username = Objects.requireNonNull(username, "El username no puede ser nulo");
        this.nombreCompleto = Objects.requireNonNull(nombreCompleto, "El nombreCompleto no puede ser nulo");
        this.rol = Objects.requireNonNull(rol, "El rol no puede ser nulo");
        this.creadaEn = Objects.requireNonNull(creadaEn, "La fecha de creación no puede ser nula");
        this.expiraEn = Objects.requireNonNull(expiraEn, "La fecha de expiración no puede ser nula");
        this.origenAutenticacion = Objects.requireNonNull(origenAutenticacion, "El origen no puede ser nulo");
        this.activa = true;
    }

    public String getToken() {
        return token;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public String getUsername() {
        return username;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public Rol getRol() {
        return rol;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public String getOrigenAutenticacion() {
        return origenAutenticacion;
    }

    public boolean isActiva() {
        return activa;
    }

    public void invalidar() {
        this.activa = false;
    }

    public boolean estaVigente(Instant ahora) {
        return activa && ahora.isBefore(expiraEn);
    }
}
