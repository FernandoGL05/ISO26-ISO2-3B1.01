package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.Rol;
import es.uclm.esi.iso2.B101.servidor.dominio.Usuario;
import es.uclm.esi.iso2.B101.servidor.seguridad.GestorContrasenas;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositorio en memoria de usuarios, con datos iniciales para todos los actores del sistema.
 */
public class RepositorioUsuariosMemoria implements RepositorioUsuarios {

    private final Map<String, Usuario> usuariosPorUsername = new ConcurrentHashMap<>();

    public RepositorioUsuariosMemoria() {
    }

    public static RepositorioUsuariosMemoria conUsuariosPorDefecto(GestorContrasenas gestorContrasenas) {
        RepositorioUsuariosMemoria repo = new RepositorioUsuariosMemoria();
        repo.registrarUsuario("USR-001", "paciente1", "Ana García López", "paciente123", Rol.PACIENTE, true, gestorContrasenas);
        repo.registrarUsuario("USR-002", "medico1", "Dr. Carlos Ruiz Martín", "medico123", Rol.MEDICO, true, gestorContrasenas);
        repo.registrarUsuario("USR-003", "admision1", "Laura Sánchez Díaz", "admision123", Rol.ADMISION, true, gestorContrasenas);
        repo.registrarUsuario("USR-004", "admin1", "Administrador SESCAM", "admin123", Rol.ADMINISTRADOR, true, gestorContrasenas);
        return repo;
    }

    public Usuario registrarUsuario(String id, String username, String nombreCompleto,
                                    String contrasenaPlana, Rol rol, boolean activo,
                                    GestorContrasenas gestorContrasenas) {
        String salt = gestorContrasenas.generarSalt();
        String hash = gestorContrasenas.calcularHash(contrasenaPlana, salt);
        Usuario usuario = new Usuario(id, username, nombreCompleto, hash, salt, rol, activo);
        guardar(usuario);
        return usuario;
    }

    @Override
    public Optional<Usuario> buscarPorUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(usuariosPorUsername.get(normalizar(username)));
    }

    @Override
    public void guardar(Usuario usuario) {
        usuariosPorUsername.put(normalizar(usuario.getUsername()), usuario);
    }

    private String normalizar(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
