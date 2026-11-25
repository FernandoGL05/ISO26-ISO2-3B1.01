package es.uclm.esi.iso2.B101.servidor.persistencia;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import es.uclm.esi.iso2.B101.servidor.dominio.EntradaListaEspera;
import es.uclm.esi.iso2.B101.servidor.dominio.EstadoEntradaListaEspera;
import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadPrueba;

public final class RepositorioListaEsperaMemoria
        implements RepositorioListaEspera {

    private static final Comparator<EntradaListaEspera> ORDEN =
            Comparator.comparingInt(
                    (EntradaListaEspera entrada) ->
                            pesoPrioridad(entrada.getPrioridad()))
                    .thenComparing(EntradaListaEspera::getFechaEntrada)
                    .thenComparing(EntradaListaEspera::getId);

    private final Map<String, EntradaListaEspera> entradas = new HashMap<>();

    @Override
    public synchronized void guardar(EntradaListaEspera entrada) {
        if (entrada == null) {
            throw new IllegalArgumentException("entrada es obligatoria");
        }

        EntradaListaEspera anterior = entradas.get(entrada.getId());

        if (anterior != null
                && (!anterior.getPacienteId().equals(entrada.getPacienteId())
                || !anterior.getMedicoId().equals(entrada.getMedicoId())
                || !anterior.getTipoPrueba().equals(entrada.getTipoPrueba())
                || !anterior.getFechaEntrada().equals(entrada.getFechaEntrada()))) {
            throw new IllegalStateException(
                    "No se pueden cambiar los datos originales de la entrada");
        }

        if (anterior != null
                && anterior.getEstado() != EstadoEntradaListaEspera.PENDIENTE
                && anterior != entrada) {
            throw new IllegalStateException(
                    "No se puede modificar una entrada ya cerrada");
        }

        if (entrada.getEstado() == EstadoEntradaListaEspera.PENDIENTE) {
            boolean duplicada = entradas.values().stream()
                    .anyMatch(existente ->
                            !existente.getId().equals(entrada.getId())
                            && existente.getEstado()
                                    == EstadoEntradaListaEspera.PENDIENTE
                            && existente.getPacienteId()
                                    .equals(entrada.getPacienteId())
                            && existente.getMedicoId()
                                    .equals(entrada.getMedicoId())
                            && existente.getTipoPrueba()
                                    .equals(entrada.getTipoPrueba()));

            if (duplicada) {
                throw new IllegalStateException(
                        "Ya existe una entrada pendiente para esa prueba");
            }
        }

        entradas.put(entrada.getId(), entrada);
    }

    @Override
    public synchronized Optional<EntradaListaEspera> buscarPorId(String id) {
        validarTexto(id, "id");
        return Optional.ofNullable(entradas.get(id));
    }

    @Override
    public synchronized List<EntradaListaEspera> buscarPendientes() {
        return entradas.values().stream()
                .filter(entrada ->
                        entrada.getEstado() == EstadoEntradaListaEspera.PENDIENTE)
                .sorted(ORDEN)
                .toList();
    }

    @Override
    public synchronized List<EntradaListaEspera> buscarPorPacienteId(
            String pacienteId) {
        validarTexto(pacienteId, "pacienteId");

        return entradas.values().stream()
                .filter(entrada ->
                        entrada.getPacienteId().equals(pacienteId))
                .sorted(ORDEN)
                .toList();
    }

    private static int pesoPrioridad(PrioridadPrueba prioridad) {
        return switch (prioridad) {
            case URGENTE -> 0;
            case PREFERENTE -> 1;
            case ORDINARIA -> 2;
        };
    }

    private static void validarTexto(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    nombre + " no puede ser nulo ni estar vacio");
        }
    }
}