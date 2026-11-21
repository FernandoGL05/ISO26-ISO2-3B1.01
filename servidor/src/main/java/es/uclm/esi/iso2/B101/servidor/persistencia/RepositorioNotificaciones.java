package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.Notificacion;
import java.util.List;
import java.util.Optional;

public interface RepositorioNotificaciones {
    void guardar(Notificacion notificacion);

    boolean guardarSiNoExiste(Notificacion notificacion);

    Optional<Notificacion> buscarPorId(String id);

    List<Notificacion> buscarPorPacienteId(String pacienteId);

    void marcarComoLeida(String id);
}
