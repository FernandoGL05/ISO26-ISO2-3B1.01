package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.InformePrueba;
import java.util.List;
import java.util.Optional;

public interface RepositorioInformesPrueba {
    void guardar(InformePrueba informe);

    Optional<InformePrueba> buscarPorId(String id);

    List<InformePrueba> buscarPorPacienteId(String pacienteId);

    List<InformePrueba> buscarPorMedicoId(String medicoId);

    void adjuntarImagen(String informeId, String tipoImagen, byte[] imagen);
}
