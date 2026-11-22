package es.uclm.esi.iso2.B101.servidor.persistencia;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import es.uclm.esi.iso2.B101.servidor.dominio.HuecoAgenda;

public final class RepositorioHuecosAgendaMemoria
        implements RepositorioHuecosAgenda {

    private final Map<String, HuecoAgenda> huecos = new HashMap<>();

    @Override
    public synchronized void guardar(HuecoAgenda hueco) {
        if (hueco == null) {
            throw new IllegalArgumentException(
                    "El hueco no puede ser nulo");
        }

        HuecoAgenda existente = huecos.get(hueco.getId());
        if (existente != null
                && !existente.getAgendaId().equals(hueco.getAgendaId())) {
            throw new IllegalStateException(
                    "No se puede trasladar un hueco a otra agenda");
        }

        boolean solapado = huecos.values().stream()
                .anyMatch(otro ->
                        !otro.getId().equals(hueco.getId())
                        && hueco.seSolapaCon(otro));

        if (solapado) {
            throw new IllegalStateException(
                    "El hueco se solapa con otro de la misma agenda");
        }

        huecos.put(hueco.getId(), hueco);
    }

    @Override
    public synchronized Optional<HuecoAgenda> buscarPorId(String id) {
        validarIdentificador(id, "id");
        return Optional.ofNullable(huecos.get(id));
    }

    @Override
    public synchronized List<HuecoAgenda> buscarPorAgendaId(
            String agendaId) {

        validarIdentificador(agendaId, "agendaId");

        return huecos.values().stream()
                .filter(hueco -> hueco.getAgendaId().equals(agendaId))
                .sorted(Comparator.comparing(HuecoAgenda::getHoraInicio))
                .toList();
    }

    @Override
    public synchronized void eliminar(String id) {
        validarIdentificador(id, "id");

        if (huecos.remove(id) == null) {
            throw new NoSuchElementException(
                    "Hueco de agenda no encontrado");
        }
    }

    private static void validarIdentificador(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    nombre + " no puede ser nulo ni estar vacío");
        }
    }
}