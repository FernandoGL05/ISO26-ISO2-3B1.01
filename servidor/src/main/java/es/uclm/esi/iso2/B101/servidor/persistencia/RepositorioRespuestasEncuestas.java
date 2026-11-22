package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.RespuestaEncuestaSatisfaccion;
import java.util.List;
import java.util.Optional;

public interface RepositorioRespuestasEncuestas {
    boolean guardarSiNoExiste(RespuestaEncuestaSatisfaccion respuesta);

    Optional<RespuestaEncuestaSatisfaccion> buscarPorEncuestaYPaciente(String encuestaId, String pacienteId);

    List<RespuestaEncuestaSatisfaccion> buscarPorPaciente(String pacienteId);
}
