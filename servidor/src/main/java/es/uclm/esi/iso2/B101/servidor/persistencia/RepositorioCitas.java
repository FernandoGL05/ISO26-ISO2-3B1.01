package es.uclm.esi.iso2.B101.servidor.persistencia;

import java.util.List;
import java.util.Optional;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;

public interface RepositorioCitas {
    void guardar(Cita cita);

    Optional<Cita> buscarPorId(String id);

    List<Cita> buscarPorPacienteId(String pacienteId);

    List<Cita> buscarPorMedicoId(String medicoId);

    default void reservar(Cita cita) {
        throw new UnsupportedOperationException(
                "Este repositorio no implementa la reserva de citas");
    }

    default void deshacerReserva(Cita cita) {
        throw new UnsupportedOperationException(
                "Este repositorio no permite deshacer reservas");
    }
}