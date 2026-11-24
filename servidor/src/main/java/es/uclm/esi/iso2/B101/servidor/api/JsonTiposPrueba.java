package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.PrioridadPrueba;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoPrueba;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Serialización y deserialización JSON para tipos de prueba (CU20).
 */
final class JsonTiposPrueba {

    private JsonTiposPrueba() {
    }

    static DatosTipoPrueba parsear(String cuerpo) {
        Map<String, Object> campos = HttpAdminSupport.parsearObjetoJson(cuerpo);
        String prioridadTexto = HttpAdminSupport.requerirCadena(campos, "prioridad")
                .trim()
                .toUpperCase(Locale.ROOT);
        return new DatosTipoPrueba(
                HttpAdminSupport.requerirCadena(campos, "nombre"),
                HttpAdminSupport.requerirCadena(campos, "modalidad"),
                HttpAdminSupport.requerirCadena(campos, "equipoRequerido"),
                HttpAdminSupport.requerirCadena(campos, "salaRequerida"),
                HttpAdminSupport.requerirEntero(campos, "duracionEstimadaMinutos"),
                HttpAdminSupport.requerirCadena(campos, "preparacionPrevia"),
                HttpAdminSupport.requerirBooleano(campos, "requiereContraste"),
                HttpAdminSupport.requerirBooleano(campos, "requiereConsentimientoInformado"),
                HttpAdminSupport.requerirCadena(campos, "requisitosSeguridad"),
                PrioridadPrueba.valueOf(prioridadTexto),
                HttpAdminSupport.requerirBooleano(campos, "admiteSeguimientoPeriodico"));
    }

    static boolean parsearEstado(String cuerpo) {
        Map<String, Object> campos = HttpAdminSupport.parsearObjetoJson(cuerpo);
        return HttpAdminSupport.requerirBooleano(campos, "activo");
    }

    static String serializar(TipoPrueba tipo) {
        return "{\"id\":" + HttpAdminSupport.cadenaJson(tipo.getId())
                + ",\"nombre\":" + HttpAdminSupport.cadenaJson(tipo.getNombre())
                + ",\"modalidad\":" + HttpAdminSupport.cadenaJson(tipo.getModalidad())
                + ",\"equipoRequerido\":" + HttpAdminSupport.cadenaJson(tipo.getEquipoRequerido())
                + ",\"salaRequerida\":" + HttpAdminSupport.cadenaJson(tipo.getSalaRequerida())
                + ",\"duracionEstimadaMinutos\":" + tipo.getDuracionEstimadaMinutos()
                + ",\"preparacionPrevia\":" + HttpAdminSupport.cadenaJson(tipo.getPreparacionPrevia())
                + ",\"requiereContraste\":" + tipo.isRequiereContraste()
                + ",\"requiereConsentimientoInformado\":" + tipo.isRequiereConsentimientoInformado()
                + ",\"requisitosSeguridad\":" + HttpAdminSupport.cadenaJson(tipo.getRequisitosSeguridad())
                + ",\"prioridad\":" + HttpAdminSupport.cadenaJson(tipo.getPrioridad().name())
                + ",\"admiteSeguimientoPeriodico\":" + tipo.isAdmiteSeguimientoPeriodico()
                + ",\"activo\":" + tipo.isActivo()
                + '}';
    }

    static String serializarLista(List<TipoPrueba> tipos) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tipos.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(serializar(tipos.get(i)));
        }
        return sb.append(']').toString();
    }

    record DatosTipoPrueba(
            String nombre,
            String modalidad,
            String equipoRequerido,
            String salaRequerida,
            int duracionEstimadaMinutos,
            String preparacionPrevia,
            boolean requiereContraste,
            boolean requiereConsentimientoInformado,
            String requisitosSeguridad,
            PrioridadPrueba prioridad,
            boolean admiteSeguimientoPeriodico) {
    }
}
