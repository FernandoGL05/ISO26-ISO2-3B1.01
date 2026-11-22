package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RepositorioCitasMemoria implements RepositorioCitas {
    private final ConcurrentHashMap<String, Cita> citas = new ConcurrentHashMap<>();

    @Override
    public void guardar(Cita cita) {
        if (cita == null) {
            throw new IllegalArgumentException("La cita no puede ser nula");
        }
        citas.put(cita.getId(), cita);
    }

    @Override
    public Optional<Cita> buscarPorId(String id) {
        validarIdentificador(id, "id");
        return Optional.ofNullable(citas.get(id));
    }

    @Override
    public List<Cita> buscarPorPacienteId(String pacienteId) {
        validarIdentificador(pacienteId, "pacienteId");

        List<Cita> citasDelPaciente = new ArrayList<>();
        for (Cita cita : citas.values()) {
            if (cita.getPacienteId().equals(pacienteId)) {
                citasDelPaciente.add(cita);
            }
        }
        return citasDelPaciente;
    }

    @Override
    public List<Cita> buscarPorMedicoId(String medicoId) {
        validarIdentificador(medicoId, "medicoId");

        List<Cita> citasDelMedico = new ArrayList<>();
        for (Cita cita : citas.values()) {
            if (medicoId.equals(cita.getMedicoId())) {
                citasDelMedico.add(cita);
            }
        }
        return citasDelMedico;
    }

    private void validarIdentificador(String identificador, String nombre) {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
