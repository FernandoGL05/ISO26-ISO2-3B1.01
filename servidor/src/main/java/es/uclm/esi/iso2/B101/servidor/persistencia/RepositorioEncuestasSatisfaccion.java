package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.EncuestaSatisfaccion;
import java.util.List;
import java.util.Optional;

public interface RepositorioEncuestasSatisfaccion {
    void guardar(EncuestaSatisfaccion encuesta);

    Optional<EncuestaSatisfaccion> buscarPorId(String id);

    List<EncuestaSatisfaccion> buscarDisponibles();
}
