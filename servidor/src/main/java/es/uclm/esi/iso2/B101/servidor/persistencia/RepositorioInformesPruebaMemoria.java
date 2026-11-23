package es.uclm.esi.iso2.B101.servidor.persistencia;

import es.uclm.esi.iso2.B101.servidor.dominio.InformePrueba;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RepositorioInformesPruebaMemoria implements RepositorioInformesPrueba {
    private final ConcurrentHashMap<String, InformePrueba> informes = new ConcurrentHashMap<>();

    @Override
    public void guardar(InformePrueba informe) {
        if (informe == null) {
            throw new IllegalArgumentException("El informe no puede ser nulo");
        }
        if (informes.putIfAbsent(informe.getId(), informe) != null) {
            throw new IllegalStateException("Ya existe un informe con ese identificador");
        }
    }

    @Override
    public Optional<InformePrueba> buscarPorId(String id) {
        validarIdentificador(id, "id");
        return Optional.ofNullable(informes.get(id));
    }

    @Override
    public List<InformePrueba> buscarPorPacienteId(String pacienteId) {
        validarIdentificador(pacienteId, "pacienteId");
        return informes.values().stream()
                .filter(informe -> informe.getPacienteId().equals(pacienteId))
                .toList();
    }

    @Override
    public List<InformePrueba> buscarPorMedicoId(String medicoId) {
        validarIdentificador(medicoId, "medicoId");
        return informes.values().stream()
                .filter(informe -> informe.getMedicoId().equals(medicoId))
                .toList();
    }

    @Override
    public void adjuntarImagen(String informeId, String tipoImagen, byte[] imagen) {
        validarIdentificador(informeId, "informeId");
        if (tipoImagen == null || tipoImagen.isBlank() || imagen == null) {
            throw new IllegalArgumentException("La imagen y su tipo son obligatorios");
        }
        informes.compute(informeId, (id, informe) -> {
            if (informe == null) {
                throw new java.util.NoSuchElementException("Informe no encontrado");
            }
            return informe.conImagen(tipoImagen, imagen);
        });
    }

    private static void validarIdentificador(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
