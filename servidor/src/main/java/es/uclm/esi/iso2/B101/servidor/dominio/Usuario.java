package es.uclm.esi.iso2.B101.servidor.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * Representa a un usuario registrado en el sistema con credenciales y rol asignado.
 */
public class Usuario {

    private final String id;
    private final String username;
    private final String nombreCompleto;
    private final String passwordHash;
    private final String salt;
    private final Rol rol;
    private boolean activo;
    private int intentosFallidos;
    private Instant bloqueadoHasta;

    public Usuario(String id, String username, String nombreCompleto,
                   String passwordHash, String salt, Rol rol, boolean activo) {
        this.id = Objects.requireNonNull(id, "El id no puede ser nulo");
        this.username = Objects.requireNonNull(username, "El nombre de usuario no puede ser nulo");
        this.nombreCompleto = Objects.requireNonNull(nombreCompleto, "El nombre completo no puede ser nulo");
        this.passwordHash = Objects.requireNonNull(passwordHash, "El hash de contraseña no puede ser nulo");
        this.salt = Objects.requireNonNull(salt, "El salt no puede ser nulo");
        this.rol = Objects.requireNonNull(rol, "El rol no puede ser nulo");
        this.activo = activo;
        this.intentosFallidos = 0;
        this.bloqueadoHasta = null;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public Rol getRol() {
        return rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public Instant getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    public boolean estaBloqueado(Instant ahora) {
        return bloqueadoHasta != null && ahora.isBefore(bloqueadoHasta);
    }

    public void registrarIntentoFallido(int maxIntentos, Instant ahora, long segundosBloqueo) {
        this.intentosFallidos++;
        if (this.intentosFallidos >= maxIntentos) {
            this.bloqueadoHasta = ahora.plusSeconds(segundosBloqueo);
        }
    }

    public void reiniciarIntentosFallidos() {
        this.intentosFallidos = 0;
        this.bloqueadoHasta = null;
    }
}
