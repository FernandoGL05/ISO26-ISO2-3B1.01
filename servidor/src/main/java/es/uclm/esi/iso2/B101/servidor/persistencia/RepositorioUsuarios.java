package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.Usuario;
import java.util.Optional;

/**
 * Contrato de persistencia para los usuarios del sistema.
 */
public interface RepositorioUsuarios {

    Optional<Usuario> buscarPorUsername(String username);

    void guardar(Usuario usuario);
}
