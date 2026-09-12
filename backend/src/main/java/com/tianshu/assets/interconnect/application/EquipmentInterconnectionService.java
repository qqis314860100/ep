package com.tianshu.assets.interconnect.application;

import com.tianshu.assets.interconnect.domain.EquipmentInterconnection;
import com.tianshu.assets.interconnect.domain.EquipmentInterconnectionRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EquipmentInterconnectionService {

    private final EquipmentInterconnectionRepository repository;

    public EquipmentInterconnectionService(EquipmentInterconnectionRepository repository) {
        this.repository = repository;
    }

    public List<EquipmentInterconnection> search(String equipmentCode, String base, String productionLine) {
        return repository.search(equipmentCode, base, productionLine);
    }
}
