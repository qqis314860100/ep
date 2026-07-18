package com.tianshu.assets.interconnect.application;

import com.tianshu.assets.interconnect.domain.EquipmentInterconnection;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class EquipmentInterconnectionService {

    private final List<EquipmentInterconnection> links = List.of(
            new EquipmentInterconnection(1, "EQ-ND-A-001", "焊接工位总成", "宁德基地", "A 拉线", "焊接段", "/line-data/EQ-ND-A-001", "ACTIVE"),
            new EquipmentInterconnection(2, "EQ-ND-A-002", "定位工装设备", "宁德基地", "A 拉线", "焊接段", "/line-data/EQ-ND-A-002", "ACTIVE"),
            new EquipmentInterconnection(3, "EQ-ND-A-003", "输送模块设备", "宁德基地", "A 拉线", "焊接段", "/line-data/EQ-ND-A-003", "ACTIVE"),
            new EquipmentInterconnection(4, "EQ-LY-B-012", "PACK 接口设备", "溧阳基地", "B 拉线", "PACK 段", "/line-data/EQ-LY-B-012", "ACTIVE"));

    public List<EquipmentInterconnection> search(String equipmentCode, String base, String productionLine) {
        return links.stream().filter(link -> matches(link.equipmentCode(), equipmentCode)
                && matches(link.base(), base)
                && matches(link.productionLine(), productionLine)).toList();
    }

    private boolean matches(String actual, String expected) {
        return expected == null || expected.isBlank()
                || actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }
}
