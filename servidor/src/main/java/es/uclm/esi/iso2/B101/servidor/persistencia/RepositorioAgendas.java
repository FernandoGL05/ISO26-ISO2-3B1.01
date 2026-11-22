package es.uclm.esi.iso2.B101.servidor.persistencia;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import es.uclm.esi.iso2.B101.servidor.dominio.AgendaDiaria;

public interface RepositorioAgendas {

    void guardar(AgendaDiaria agenda);

    Optional<AgendaDiaria> buscarPorId(String id);

    Optional<AgendaDiaria> buscarPorMedicoYFecha(
            String medicoId, LocalDate fecha);

    List<AgendaDiaria> buscarPorFecha(LocalDate fecha);
}