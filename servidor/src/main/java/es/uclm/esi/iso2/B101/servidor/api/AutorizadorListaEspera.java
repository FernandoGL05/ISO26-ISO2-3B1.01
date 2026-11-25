package es.uclm.esi.iso2.B101.servidor.api;

import java.util.Objects;
import java.util.Optional;

import es.uclm.esi.iso2.B101.servidor.seguridad.AutenticadorSesion;
import es.uclm.esi.iso2.B101.servidor.seguridad.AutenticadorSesion.IdentidadAutenticada;

@FunctionalInterface
public interface AutorizadorListaEspera {
    Optional<IdentidadAutenticada> autenticar(String cabeceraAutorizacion);

    static AutorizadorListaEspera porSesion(
            AutenticadorSesion autenticador) {
        Objects.requireNonNull(
                autenticador, "autenticador es obligatorio");

        return autenticador::autenticar;
    }
}