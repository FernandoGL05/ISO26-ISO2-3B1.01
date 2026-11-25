package es.uclm.esi.iso2.B101.servidor.persistencia;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import es.uclm.esi.iso2.B101.servidor.dominio.Cita;
import es.uclm.esi.iso2.B101.servidor.dominio.EstadoCita;

public class RepositorioCitasMemoria implements RepositorioCitas {
    private final ConcurrentHashMap<String, Cita> citas =
            new ConcurrentHashMap<>();

    @Override
    public synchronized void guardar(Cita cita) {
        if (cita == null) {
            throw new IllegalArgumentException("La cita no puede ser nula");
        }
        citas.put(cita.getId(), cita);
    }

    @Override
    public synchronized Optional<Cita> buscarPorId(String id) {
        validarIdentificador(id, "id");
        return Optional.ofNullable(citas.get(id));
    }

    @Override
    public synchronized List<Cita> buscarPorPacienteId(String pacienteId) {
        validarIdentificador(pacienteId, "pacienteId");

        List<Cita> resultado = new ArrayList<>();
        for (Cita cita : citas.values()) {
            if (cita.getPacienteId().equals(pacienteId)) {
                resultado.add(cita);
            }
        }
        return resultado;
    }

    @Override
    public synchronized List<Cita> buscarPorMedicoId(String medicoId) {
        validarIdentificador(medicoId, "medicoId");

        List<Cita> resultado = new ArrayList<>();
        for (Cita cita : citas.values()) {
            if (medicoId.equals(cita.getMedicoId())) {
                resultado.add(cita);
            }
        }
        return resultado;
    }

    @Override
    public synchronized void reservar(Cita nueva) {
        validarNuevaReserva(nueva);

        if (citas.containsKey(nueva.getId())) {
            throw new IllegalStateException(
                    "Ya existe una cita con ese identificador");
        }

        LocalDateTime inicio = nueva.getFechaHora();
        LocalDateTime fin = inicio.plusMinutes(
                nueva.getDuracionMinutos().orElseThrow());

        for (Cita existente : citas.values()) {
            if (existente.getEstado() == EstadoCita.CANCELADA) {
                continue;
            }

            boolean mismoMedico =
                    nueva.getMedicoId().equals(existente.getMedicoId());
            boolean mismoPaciente =
                    nueva.getPacienteId().equals(existente.getPacienteId());

            if (!mismoMedico && !mismoPaciente) {
                continue;
            }

            // Si empieza al terminar la nueva cita o despues,
            // los intervalos no se solapan.
            if (!existente.getFechaHora().isBefore(fin)) {
                continue;
            }

            if (existente.getDuracionMinutos().isEmpty()) {
                throw new IllegalStateException(
                        "Hay una cita del medico o paciente sin duracion. "
                        + "Debe completarse antes de comprobar este hueco");
            }

            LocalDateTime finExistente = existente.getFechaHora()
                    .plusMinutes(
                            existente.getDuracionMinutos().orElseThrow());

            if (inicio.isBefore(finExistente)) {
                throw new IllegalStateException(
                        "El medico o el paciente ya tiene una cita "
                        + "en ese intervalo");
            }
        }

        citas.put(nueva.getId(), nueva);
    }

    @Override
    public synchronized void deshacerReserva(Cita cita) {
        if (cita == null) {
            throw new IllegalArgumentException("La cita no puede ser nula");
        }

        if (cita.getEstado() != EstadoCita.PROGRAMADA) {
            throw new IllegalStateException(
                    "No se puede deshacer una reserva que ha cambiado de estado");
        }

        // Solo elimina exactamente el objeto que se acaba de reservar.
        if (!citas.remove(cita.getId(), cita)) {
            throw new IllegalStateException(
                    "No se encuentra la reserva que se quiere deshacer");
        }
    }

    private void validarNuevaReserva(Cita cita) {
        if (cita == null) {
            throw new IllegalArgumentException("La cita no puede ser nula");
        }

        validarIdentificador(cita.getMedicoId(), "medicoId");

        if (cita.getDuracionMinutos().isEmpty()) {
            throw new IllegalArgumentException(
                    "La reserva debe indicar su duracion");
        }

        if (cita.getEstado() != EstadoCita.PROGRAMADA) {
            throw new IllegalArgumentException(
                    "La nueva reserva debe estar en estado PROGRAMADA");
        }
    }

    private void validarIdentificador(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    nombre + " no puede ser nulo ni estar vacio");
        }
    }
}
