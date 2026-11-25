package es.uclm.esi.iso2.B101.servidor.servicio;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

import es.uclm.esi.iso2.B101.servidor.dominio.EntradaListaEspera;
import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadPrueba;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioListaEspera;

public final class ServicioListaEspera {
    private final RepositorioListaEspera repositorio;
    private final Clock reloj;

    public ServicioListaEspera(RepositorioListaEspera repositorio) {
        this(repositorio, Clock.systemDefaultZone());
    }

    public ServicioListaEspera(
            RepositorioListaEspera repositorio,
            Clock reloj) {
        this.repositorio = Objects.requireNonNull(
                repositorio, "repositorio es obligatorio");
        this.reloj = Objects.requireNonNull(
                reloj, "reloj es obligatorio");
    }

    public EntradaListaEspera inscribir(
            String pacienteId,
            String medicoId,
            String tipoPrueba,
            PrioridadPrueba prioridad) {
        EntradaListaEspera entrada = new EntradaListaEspera(
                UUID.randomUUID().toString(),
                pacienteId,
                medicoId,
                tipoPrueba,
                prioridad,
                LocalDateTime.now(reloj));

        synchronized (repositorio) {
            repositorio.guardar(entrada);
        }

        return entrada;
    }

    public EntradaListaEspera consultarEntrada(String entradaId) {
        validarTexto(entradaId, "entradaId");

        synchronized (repositorio) {
            return buscarEntrada(entradaId);
        }
    }

    public List<EntradaListaEspera> consultarPendientes() {
        synchronized (repositorio) {
            return repositorio.buscarPendientes();
        }
    }

    public List<EntradaListaEspera> consultarPendientes(
            String medicoId,
            String tipoPrueba) {
        validarTexto(medicoId, "medicoId");
        validarTexto(tipoPrueba, "tipoPrueba");

        synchronized (repositorio) {
            return repositorio.buscarPendientes().stream()
                    .filter(entrada ->
                            entrada.getMedicoId().equals(medicoId)
                            && entrada.getTipoPrueba().equals(tipoPrueba))
                    .toList();
        }
    }

    public List<EntradaListaEspera> consultarPorPaciente(
            String pacienteId) {
        validarTexto(pacienteId, "pacienteId");

        synchronized (repositorio) {
            return repositorio.buscarPorPacienteId(pacienteId);
        }
    }

    public EntradaListaEspera cambiarPrioridad(
            String entradaId,
            PrioridadPrueba nuevaPrioridad) {
        validarTexto(entradaId, "entradaId");

        if (nuevaPrioridad == null) {
            throw new IllegalArgumentException(
                    "nuevaPrioridad es obligatoria");
        }

        synchronized (repositorio) {
            EntradaListaEspera actualizada = buscarEntrada(entradaId)
                    .cambiarPrioridad(nuevaPrioridad);

            repositorio.guardar(actualizada);
            return actualizada;
        }
    }

    public EntradaListaEspera cancelar(String entradaId) {
        validarTexto(entradaId, "entradaId");

        synchronized (repositorio) {
            EntradaListaEspera cancelada = buscarEntrada(entradaId)
                    .cancelar();

            repositorio.guardar(cancelada);
            return cancelada;
        }
    }

    private EntradaListaEspera buscarEntrada(String entradaId) {
        return repositorio.buscarPorId(entradaId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe la entrada de lista de espera"));
    }

    private static void validarTexto(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    nombre + " no puede ser nulo ni estar vacio");
        }
    }
}