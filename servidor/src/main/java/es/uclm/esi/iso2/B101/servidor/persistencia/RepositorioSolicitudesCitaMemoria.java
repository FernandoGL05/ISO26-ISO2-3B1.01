package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.SolicitudCita;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RepositorioSolicitudesCitaMemoria implements RepositorioSolicitudesCita {
    private final ConcurrentHashMap<String, SolicitudCita> solicitudes = new ConcurrentHashMap<>();

    @Override
    public void guardar(SolicitudCita solicitud) {
        if (solicitud == null) {
            throw new IllegalArgumentException("La solicitud no puede ser nula");
        }
        solicitudes.put(solicitud.getId(), solicitud);
    }

    @Override
    public Optional<SolicitudCita> buscarPorId(String id) {
        validarIdentificador(id, "id");
        return Optional.ofNullable(solicitudes.get(id));
    }

    @Override
    public List<SolicitudCita> buscarPorPacienteId(String pacienteId) {
        validarIdentificador(pacienteId, "pacienteId");

        List<SolicitudCita> solicitudesDelPaciente = new ArrayList<>();
        for (SolicitudCita solicitud : solicitudes.values()) {
            if (solicitud.getPacienteId().equals(pacienteId)) {
                solicitudesDelPaciente.add(solicitud);
            }
        }
        return solicitudesDelPaciente;
    }

    @Override
    public List<SolicitudCita> buscarPorCitaId(String citaId) {
        validarIdentificador(citaId, "citaId");

        List<SolicitudCita> solicitudesDeLaCita = new ArrayList<>();
        for (SolicitudCita solicitud : solicitudes.values()) {
            if (solicitud.getCitaId().equals(citaId)) {
                solicitudesDeLaCita.add(solicitud);
            }
        }
        return solicitudesDeLaCita;
    }

    private void validarIdentificador(String identificador, String nombre) {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
