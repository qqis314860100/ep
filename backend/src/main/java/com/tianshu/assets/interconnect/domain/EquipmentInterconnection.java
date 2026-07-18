package com.tianshu.assets.interconnect.domain;

public record EquipmentInterconnection(
        long id,
        String equipmentCode,
        String equipmentName,
        String base,
        String productionLine,
        String processSection,
        String dataReference,
        String status) {
}
