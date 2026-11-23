package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.RegistroAuditoriaAcceso;
import java.util.List;

/**
 * Contrato para el almacenamiento de registros de auditoría de accesos.
 */
public interface RepositorioAuditoriaAcceso {

    void registrar(RegistroAuditoriaAcceso registro);

    List<RegistroAuditoriaAcceso> obtenerTodos();
}
