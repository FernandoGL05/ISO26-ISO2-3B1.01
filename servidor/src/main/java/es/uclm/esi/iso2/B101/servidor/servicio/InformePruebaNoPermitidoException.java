package es.uclm.esi.iso2.B101.servidor.servicio;

public final class InformePruebaNoPermitidoException extends RuntimeException {
    public InformePruebaNoPermitidoException() {
        super("El médico no está autorizado para crear un informe de esta prueba");
    }
}
