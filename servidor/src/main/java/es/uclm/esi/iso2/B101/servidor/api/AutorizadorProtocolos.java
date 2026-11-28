package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.seguridad.AutenticadorSesion;
import es.uclm.esi.iso2.B101.servidor.seguridad.AutenticadorSesion.IdentidadAutenticada;
import java.util.Objects;
import java.util.Optional;

@FunctionalInterface
public interface AutorizadorProtocolos {

    Optional<IdentidadAutenticada> autenticar(String cabeceraAutorizacion);

    static AutorizadorProtocolos porSesion(AutenticadorSesion autenticador) {
        Objects.requireNonNull(autenticador, "autenticador es obligatorio");
        return autenticador::autenticar;
    }
}
