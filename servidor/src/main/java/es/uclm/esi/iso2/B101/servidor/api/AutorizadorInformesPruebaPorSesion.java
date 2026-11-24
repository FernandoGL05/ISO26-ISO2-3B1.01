package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.Rol;
import es.uclm.esi.iso2.B101.servidor.persistencia.AsociacionIdentidadesClinicas;
import es.uclm.esi.iso2.B101.servidor.seguridad.AutenticadorSesion;
import java.util.Objects;
import java.util.Optional;

public final class AutorizadorInformesPruebaPorSesion implements AutorizadorInformesPrueba {
    private final AutenticadorSesion autenticadorSesion;
    private final AsociacionIdentidadesClinicas asociaciones;

    public AutorizadorInformesPruebaPorSesion(
            AutenticadorSesion autenticadorSesion,
            AsociacionIdentidadesClinicas asociaciones) {
        this.autenticadorSesion = Objects.requireNonNull(autenticadorSesion, "autenticadorSesion no puede ser nulo");
        this.asociaciones = Objects.requireNonNull(asociaciones, "asociaciones no pueden ser nulas");
    }

    @Override
    public Optional<Identidad> autenticar(String cabeceraAutorizacion) {
        return autenticadorSesion.autenticar(cabeceraAutorizacion).map(identidad -> {
            boolean tieneRolPaciente = identidad.rol() == Rol.PACIENTE;
            boolean tieneRolMedico = identidad.rol() == Rol.MEDICO;
            String pacienteId = tieneRolPaciente
                    ? asociaciones.pacienteIdDe(identidad.usuarioId()).orElse(null)
                    : null;
            String medicoId = tieneRolMedico
                    ? asociaciones.medicoIdDe(identidad.usuarioId()).orElse(null)
                    : null;
            return new Identidad(
                    pacienteId != null,
                    pacienteId,
                    medicoId != null,
                    medicoId);
        });
    }
}
