package es.uclm.esi.iso2.B101.servidor.seguridad;

import es.uclm.esi.iso2.B101.servidor.dominio.Rol;
import es.uclm.esi.iso2.B101.servidor.dominio.Sesion;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAutenticacion;
import java.util.Objects;
import java.util.Optional;

public final class AutenticadorSesion {
    private static final String PREFIJO_BEARER = "Bearer ";

    private final ServicioAutenticacion servicioAutenticacion;

    public AutenticadorSesion(ServicioAutenticacion servicioAutenticacion) {
        this.servicioAutenticacion =
                Objects.requireNonNull(servicioAutenticacion, "servicioAutenticacion no puede ser nulo");
    }

    public Optional<IdentidadAutenticada> autenticar(String cabeceraAutorizacion) {
        return extraerToken(cabeceraAutorizacion)
                .flatMap(servicioAutenticacion::validarSesion)
                .map(AutenticadorSesion::identidadDe);
    }

    private static Optional<String> extraerToken(String cabecera) {
        if (cabecera == null
                || cabecera.length() <= PREFIJO_BEARER.length()
                || !cabecera.regionMatches(true, 0, PREFIJO_BEARER, 0, PREFIJO_BEARER.length())) {
            return Optional.empty();
        }
        String token = cabecera.substring(PREFIJO_BEARER.length());
        if (token.isBlank() || token.chars().anyMatch(Character::isWhitespace) || token.indexOf(',') >= 0) {
            return Optional.empty();
        }
        return Optional.of(token);
    }

    private static IdentidadAutenticada identidadDe(Sesion sesion) {
        return new IdentidadAutenticada(sesion.getUsuarioId(), sesion.getRol());
    }

    public record IdentidadAutenticada(String usuarioId, Rol rol) {
        public IdentidadAutenticada {
            Objects.requireNonNull(usuarioId, "usuarioId no puede ser nulo");
            Objects.requireNonNull(rol, "rol no puede ser nulo");
        }
    }
}
