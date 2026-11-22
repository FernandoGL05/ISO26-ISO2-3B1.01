package es.uclm.esi.iso2.B101.servidor.persistencia;

import java.util.List;
import java.util.Optional;

import es.uclm.esi.iso2.B101.servidor.dominio.HuecoAgenda;

public interface RepositorioHuecosAgenda {

    void guardar(HuecoAgenda hueco);

    Optional<HuecoAgenda> buscarPorId(String id);

    List<HuecoAgenda> buscarPorAgendaId(String agendaId);

    void eliminar(String id);
}