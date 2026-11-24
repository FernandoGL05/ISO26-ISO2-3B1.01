package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.Rol;
import es.uclm.esi.iso2.B101.servidor.persistencia.AsociacionIdentidadesClinicas;
import es.uclm.esi.iso2.B101.servidor.seguridad.AutenticadorSesion;
import java.util.Objects;
import java.util.Optional;

public final class AutorizadorHistorialPruebasPorSesion implements AutorizadorHistorialPruebas {
    private final AutenticadorSesion autenticadorSesion;
    private final AsociacionIdentidadesClinicas asociaciones;

    public AutorizadorHistorialPruebasPorSesion(
            AutenticadorSesion autenticadorSesion,
            AsociacionIdentidadesClinicas asociaciones) {
        this.autenticadorSesion = Objects.requireNonNull(autenticadorSesion, "autenticadorSesion no puede ser nulo");
        this.asociaciones = Objects.requireNonNull(asociaciones, "asociaciones no pueden ser nulas");
    }

    @Override
    public Optional<IdentidadPaciente> autenticar(String cabeceraAutorizacion) {
        return autenticadorSesion.autenticar(cabeceraAutorizacion).map(identidad -> {
            if (identidad.rol() != Rol.PACIENTE) {
                return new IdentidadPaciente(false, null);
            }
            String pacienteId = asociaciones.pacienteIdDe(identidad.usuarioId()).orElse(null);
            return new IdentidadPaciente(pacienteId != null, pacienteId);
        });
    }
}
