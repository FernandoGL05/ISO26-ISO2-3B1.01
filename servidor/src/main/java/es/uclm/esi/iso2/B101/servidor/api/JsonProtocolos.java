package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.Protocolo;
import java.util.List;
import java.util.Map;

/**
 * Serialización y deserialización JSON para protocolos clínicos/técnicos (CU21).
 */
final class JsonProtocolos {

    private JsonProtocolos() {
    }

    static DatosProtocolo parsear(String cuerpo) {
        Map<String, Object> campos = HttpAdminSupport.parsearObjetoJson(cuerpo);
        return new DatosProtocolo(
                HttpAdminSupport.requerirCadena(campos, "codigo"),
                HttpAdminSupport.requerirCadena(campos, "nombre"),
                HttpAdminSupport.requerirCadena(campos, "tipoPruebaId"),
                HttpAdminSupport.requerirCadena(campos, "descripcion"),
                HttpAdminSupport.requerirCadena(campos, "pasosPreparacion"),
                HttpAdminSupport.requerirCadena(campos, "requisitosSeguridad"),
                HttpAdminSupport.requerirBooleano(campos, "usoContraste"));
    }

    static boolean parsearEstado(String cuerpo) {
        Map<String, Object> campos = HttpAdminSupport.parsearObjetoJson(cuerpo);
        return HttpAdminSupport.requerirBooleano(campos, "activo");
    }

    static String serializar(Protocolo protocolo) {
        return "{\"id\":" + HttpAdminSupport.cadenaJson(protocolo.getId())
                + ",\"codigo\":" + HttpAdminSupport.cadenaJson(protocolo.getCodigo())
                + ",\"nombre\":" + HttpAdminSupport.cadenaJson(protocolo.getNombre())
                + ",\"tipoPruebaId\":" + HttpAdminSupport.cadenaJson(protocolo.getTipoPruebaId())
                + ",\"descripcion\":" + HttpAdminSupport.cadenaJson(protocolo.getDescripcion())
                + ",\"pasosPreparacion\":" + HttpAdminSupport.cadenaJson(protocolo.getPasosPreparacion())
                + ",\"requisitosSeguridad\":" + HttpAdminSupport.cadenaJson(protocolo.getRequisitosSeguridad())
                + ",\"usoContraste\":" + protocolo.isUsoContraste()
                + ",\"version\":" + protocolo.getVersion()
                + ",\"activo\":" + protocolo.isActivo()
                + '}';
    }

    static String serializarLista(List<Protocolo> protocolos) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < protocolos.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(serializar(protocolos.get(i)));
        }
        return sb.append(']').toString();
    }

    record DatosProtocolo(
            String codigo,
            String nombre,
            String tipoPruebaId,
            String descripcion,
            String pasosPreparacion,
            String requisitosSeguridad,
            boolean usoContraste) {
    }
}
