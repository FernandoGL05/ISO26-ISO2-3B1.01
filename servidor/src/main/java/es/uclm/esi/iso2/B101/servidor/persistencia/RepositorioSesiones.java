package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.Sesion;
import java.util.Optional;

/**
 * Contrato de persistencia para sesiones de usuario.
 */
public interface RepositorioSesiones {

    void guardar(Sesion sesion);

    Optional<Sesion> buscarPorToken(String token);

    boolean invalidar(String token);
}
