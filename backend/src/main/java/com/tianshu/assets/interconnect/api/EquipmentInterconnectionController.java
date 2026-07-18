package com.tianshu.assets.interconnect.api;

import com.tianshu.assets.interconnect.application.EquipmentInterconnectionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment-interconnections")
public class EquipmentInterconnectionController {

    private final EquipmentInterconnectionService service;

    public EquipmentInterconnectionController(EquipmentInterconnectionService service) {
        this.service = service;
    }

    @GetMapping
    public List<EquipmentInterconnectionResponse> search(
            @RequestParam(required = false) String equipmentCode,
            @RequestParam(required = false) String base,
            @RequestParam(name = "production_line", required = false) String productionLine) {
        return service.search(equipmentCode, base, productionLine).stream()
                .map(EquipmentInterconnectionResponse::from)
                .toList();
    }
}
