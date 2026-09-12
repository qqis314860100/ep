package com.tianshu.assets.interconnect.domain;

import java.util.List;

public interface EquipmentInterconnectionRepository {

    List<EquipmentInterconnection> search(String equipmentCode, String base, String productionLine);
}
