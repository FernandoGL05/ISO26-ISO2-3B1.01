package es.uclm.esi.iso2.B101.servidor.persistencia;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import es.uclm.esi.iso2.B101.servidor.dominio.AgendaDiaria;

public final class RepositorioAgendasMemoria
        implements RepositorioAgendas {

    private final Map<String, AgendaDiaria> agendas = new HashMap<>();

    @Override
    public synchronized void guardar(AgendaDiaria agenda) {
        if (agenda == null) {
            throw new IllegalArgumentException(
                    "La agenda no puede ser nula");
        }

        AgendaDiaria existente = agendas.get(agenda.getId());
        if (existente != null
                && (!existente.getMedicoId().equals(agenda.getMedicoId())
                || !existente.getFecha().equals(agenda.getFecha()))) {
            throw new IllegalStateException(
                    "No se puede cambiar el médico o la fecha de una agenda existente");
        }

        boolean duplicada = agendas.values().stream()
                .anyMatch(otra ->
                        !otra.getId().equals(agenda.getId())
                        && otra.getMedicoId().equals(agenda.getMedicoId())
                        && otra.getFecha().equals(agenda.getFecha()));

        if (duplicada) {
            throw new IllegalStateException(
                    "Ya existe una agenda para ese médico y esa fecha");
        }

        agendas.put(agenda.getId(), agenda);
    }

    @Override
    public synchronized Optional<AgendaDiaria> buscarPorId(String id) {
        validarIdentificador(id, "id");
        return Optional.ofNullable(agendas.get(id));
    }

    @Override
    public synchronized Optional<AgendaDiaria> buscarPorMedicoYFecha(
            String medicoId, LocalDate fecha) {

        validarIdentificador(medicoId, "medicoId");
        validarFecha(fecha);

        return agendas.values().stream()
                .filter(agenda ->
                        agenda.getMedicoId().equals(medicoId)
                        && agenda.getFecha().equals(fecha))
                .findFirst();
    }

    @Override
    public synchronized List<AgendaDiaria> buscarPorFecha(LocalDate fecha) {
        validarFecha(fecha);

        return agendas.values().stream()
                .filter(agenda -> agenda.getFecha().equals(fecha))
                .sorted(Comparator.comparing(AgendaDiaria::getMedicoId))
                .toList();
    }

    private static void validarIdentificador(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    nombre + " no puede ser nulo ni estar vacío");
        }
    }

    private static void validarFecha(LocalDate fecha) {
        if (fecha == null) {
            throw new IllegalArgumentException(
                    "La fecha no puede ser nula");
        }
    }
}
