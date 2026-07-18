package com.tianshu.assets.interconnect.api;

import com.tianshu.assets.interconnect.domain.EquipmentInterconnection;

public record EquipmentInterconnectionResponse(
        long id,
        String equipmentCode,
        String equipmentName,
        String base,
        String productionLine,
        String processSection,
        String dataReference,
        String status) {

    public static EquipmentInterconnectionResponse from(EquipmentInterconnection link) {
        return new EquipmentInterconnectionResponse(link.id(), link.equipmentCode(), link.equipmentName(), link.base(),
                link.productionLine(), link.processSection(), link.dataReference(), link.status());
    }
}
