package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.SolicitudCita;
import java.util.List;
import java.util.Optional;

public interface RepositorioSolicitudesCita {
    void guardar(SolicitudCita solicitud);

    Optional<SolicitudCita> buscarPorId(String id);

    List<SolicitudCita> buscarPorPacienteId(String pacienteId);

    List<SolicitudCita> buscarPorCitaId(String citaId);
}
