package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.RegistroAuditoriaAcceso;
import es.uclm.esi.iso2.B101.servidor.dominio.ResultadoAutenticacion;
import es.uclm.esi.iso2.B101.servidor.dominio.Sesion;
import es.uclm.esi.iso2.B101.servidor.dominio.Usuario;
import es.uclm.esi.iso2.B101.servidor.integracion.ProveedorSSO;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioAuditoriaAcceso;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSesiones;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioUsuarios;
import es.uclm.esi.iso2.B101.servidor.seguridad.GeneradorTokens;
import es.uclm.esi.iso2.B101.servidor.seguridad.GestorContrasenas;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Servicio de aplicación que implementa el caso de uso CU01 — Iniciar sesión (RF01).
 * Soporta autenticación local con credenciales y federada mediante SSO corporativo,
 * control de intentos fallidos, bloqueo temporal, gestión de sesiones y auditoría.
 */
public class ServicioAutenticacion {

    public static final int MAX_INTENTOS_FALLIDOS_POR_DEFECTO = 5;
    public static final Duration DURACION_BLOQUEO_POR_DEFECTO = Duration.ofMinutes(15);
    public static final Duration DURACION_SESION_POR_DEFECTO = Duration.ofHours(8);

    private final RepositorioUsuarios repositorioUsuarios;
    private final RepositorioSesiones repositorioSesiones;
    private final RepositorioAuditoriaAcceso repositorioAuditoria;
    private final GestorContrasenas gestorContrasenas;
    private final GeneradorTokens generadorTokens;
    private final ProveedorSSO proveedorSSO;
    private final Clock clock;
    private final int maxIntentosFallidos;
    private final Duration duracionBloqueo;
    private final Duration duracionSesion;

    public ServicioAutenticacion(RepositorioUsuarios repositorioUsuarios,
                                 RepositorioSesiones repositorioSesiones,
                                 RepositorioAuditoriaAcceso repositorioAuditoria,
                                 GestorContrasenas gestorContrasenas,
                                 GeneradorTokens generadorTokens,
                                 ProveedorSSO proveedorSSO) {
        this(repositorioUsuarios, repositorioSesiones, repositorioAuditoria,
                gestorContrasenas, generadorTokens, proveedorSSO,
                Clock.systemUTC(),
                MAX_INTENTOS_FALLIDOS_POR_DEFECTO,
                DURACION_BLOQUEO_POR_DEFECTO,
                DURACION_SESION_POR_DEFECTO);
    }

    public ServicioAutenticacion(RepositorioUsuarios repositorioUsuarios,
                                 RepositorioSesiones repositorioSesiones,
                                 RepositorioAuditoriaAcceso repositorioAuditoria,
                                 GestorContrasenas gestorContrasenas,
                                 GeneradorTokens generadorTokens,
                                 ProveedorSSO proveedorSSO,
                                 Clock clock,
                                 int maxIntentosFallidos,
                                 Duration duracionBloqueo,
                                 Duration duracionSesion) {
        this.repositorioUsuarios = Objects.requireNonNull(repositorioUsuarios);
        this.repositorioSesiones = Objects.requireNonNull(repositorioSesiones);
        this.repositorioAuditoria = Objects.requireNonNull(repositorioAuditoria);
        this.gestorContrasenas = Objects.requireNonNull(gestorContrasenas);
        this.generadorTokens = Objects.requireNonNull(generadorTokens);
        this.proveedorSSO = Objects.requireNonNull(proveedorSSO);
        this.clock = Objects.requireNonNull(clock);
        this.maxIntentosFallidos = maxIntentosFallidos;
        this.duracionBloqueo = Objects.requireNonNull(duracionBloqueo);
        this.duracionSesion = Objects.requireNonNull(duracionSesion);
    }

    /**
     * Inicia sesión mediante usuario y contraseña (flujo principal de CU01).
     */
    public ResultadoAutenticacion iniciarSesion(String username, String password) {
        Instant ahora = clock.instant();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            registrarAuditoria(ahora, username == null ? "" : username.trim(), false,
                    ResultadoAutenticacion.ERROR_CREDENCIALES_VACIAS, Sesion.ORIGEN_LOCAL);
            return ResultadoAutenticacion.error(
                    ResultadoAutenticacion.ERROR_CREDENCIALES_VACIAS,
                    "Debe introducir usuario y contraseña.");
        }

        String usuarioNormalizado = username.trim();
        Optional<Usuario> usuarioOpt = repositorioUsuarios.buscarPorUsername(usuarioNormalizado);
        if (usuarioOpt.isEmpty()) {
            registrarAuditoria(ahora, usuarioNormalizado, false,
                    ResultadoAutenticacion.ERROR_CREDENCIALES_INVALIDAS, Sesion.ORIGEN_LOCAL);
            return ResultadoAutenticacion.error(
                    ResultadoAutenticacion.ERROR_CREDENCIALES_INVALIDAS,
                    "Usuario o contraseña incorrectos.");
        }

        Usuario usuario = usuarioOpt.get();
        if (!usuario.isActivo()) {
            registrarAuditoria(ahora, usuario.getUsername(), false,
                    ResultadoAutenticacion.ERROR_CUENTA_INACTIVA, Sesion.ORIGEN_LOCAL);
            return ResultadoAutenticacion.error(
                    ResultadoAutenticacion.ERROR_CUENTA_INACTIVA,
                    "La cuenta de usuario está desactivada.");
        }

        if (usuario.estaBloqueado(ahora)) {
            registrarAuditoria(ahora, usuario.getUsername(), false,
                    ResultadoAutenticacion.ERROR_CUENTA_BLOQUEADA, Sesion.ORIGEN_LOCAL);
            return ResultadoAutenticacion.error(
                    ResultadoAutenticacion.ERROR_CUENTA_BLOQUEADA,
                    "La cuenta está temporalmente bloqueada por demasiados intentos fallidos.");
        }

        boolean valida = gestorContrasenas.verificar(password, usuario.getSalt(), usuario.getPasswordHash());
        if (!valida) {
            usuario.registrarIntentoFallido(maxIntentosFallidos, ahora, duracionBloqueo.toSeconds());
            repositorioUsuarios.guardar(usuario);
            String motivo = usuario.estaBloqueado(ahora)
                    ? ResultadoAutenticacion.ERROR_CUENTA_BLOQUEADA
                    : ResultadoAutenticacion.ERROR_CREDENCIALES_INVALIDAS;
            registrarAuditoria(ahora, usuario.getUsername(), false, motivo, Sesion.ORIGEN_LOCAL);
            if (usuario.estaBloqueado(ahora)) {
                return ResultadoAutenticacion.error(
                        ResultadoAutenticacion.ERROR_CUENTA_BLOQUEADA,
                        "Cuenta bloqueada temporalmente tras superar el máximo de intentos fallidos.");
            }
            return ResultadoAutenticacion.error(
                    ResultadoAutenticacion.ERROR_CREDENCIALES_INVALIDAS,
                    "Usuario o contraseña incorrectos.");
        }

        usuario.reiniciarIntentosFallidos();
        repositorioUsuarios.guardar(usuario);

        Sesion sesion = crearYGuardarSesion(usuario, ahora, Sesion.ORIGEN_LOCAL);
        registrarAuditoria(ahora, usuario.getUsername(), true, "OK", Sesion.ORIGEN_LOCAL);
        return ResultadoAutenticacion.exito(sesion);
    }

    /**
     * Inicia sesión delegando la validación al SSO corporativo del SESCAM (RT05).
     */
    public ResultadoAutenticacion iniciarSesionSSO(String ssoToken) {
        Instant ahora = clock.instant();
        if (ssoToken == null || ssoToken.isBlank()) {
            registrarAuditoria(ahora, "", false,
                    ResultadoAutenticacion.ERROR_SSO_INVALIDO, Sesion.ORIGEN_SSO);
            return ResultadoAutenticacion.error(
                    ResultadoAutenticacion.ERROR_SSO_INVALIDO,
                    "El token SSO corporativo es obligatorio.");
        }

        Optional<String> usernameOpt = proveedorSSO.validarTokenSSO(ssoToken);
        if (usernameOpt.isEmpty()) {
            registrarAuditoria(ahora, "", false,
                    ResultadoAutenticacion.ERROR_SSO_INVALIDO, Sesion.ORIGEN_SSO);
            return ResultadoAutenticacion.error(
                    ResultadoAutenticacion.ERROR_SSO_INVALIDO,
                    "Token SSO corporativo inválido o expirado.");
        }

        Optional<Usuario> usuarioOpt = repositorioUsuarios.buscarPorUsername(usernameOpt.get());
        if (usuarioOpt.isEmpty()) {
            registrarAuditoria(ahora, usernameOpt.get(), false,
                    ResultadoAutenticacion.ERROR_CREDENCIALES_INVALIDAS, Sesion.ORIGEN_SSO);
            return ResultadoAutenticacion.error(
                    ResultadoAutenticacion.ERROR_CREDENCIALES_INVALIDAS,
                    "El usuario autenticado en SSO no tiene perfil asignado en el sistema.");
        }

        Usuario usuario = usuarioOpt.get();
        if (!usuario.isActivo()) {
            registrarAuditoria(ahora, usuario.getUsername(), false,
                    ResultadoAutenticacion.ERROR_CUENTA_INACTIVA, Sesion.ORIGEN_SSO);
            return ResultadoAutenticacion.error(
                    ResultadoAutenticacion.ERROR_CUENTA_INACTIVA,
                    "La cuenta de usuario está desactivada.");
        }

        Sesion sesion = crearYGuardarSesion(usuario, ahora, Sesion.ORIGEN_SSO);
        registrarAuditoria(ahora, usuario.getUsername(), true, "OK", Sesion.ORIGEN_SSO);
        return ResultadoAutenticacion.exito(sesion);
    }

    /**
     * Comprueba si un token corresponde a una sesión activa y no expirada.
     */
    public Optional<Sesion> validarSesion(String token) {
        Instant ahora = clock.instant();
        return repositorioSesiones.buscarPorToken(token)
                .filter(sesion -> sesion.estaVigente(ahora));
    }

    /**
     * Cierra (invalida) la sesión asociada al token.
     */
    public boolean cerrarSesion(String token) {
        return repositorioSesiones.invalidar(token);
    }

    private Sesion crearYGuardarSesion(Usuario usuario, Instant ahora, String origen) {
        String token = generadorTokens.generarToken();
        Sesion sesion = new Sesion(
                token,
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombreCompleto(),
                usuario.getRol(),
                ahora,
                ahora.plus(duracionSesion),
                origen
        );
        repositorioSesiones.guardar(sesion);
        return sesion;
    }

    private void registrarAuditoria(Instant ahora, String username, boolean exito, String motivo, String origen) {
        repositorioAuditoria.registrar(new RegistroAuditoriaAcceso(ahora, username, exito, motivo, origen));
    }
}
