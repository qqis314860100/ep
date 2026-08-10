package com.tianshu.assets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SimulationAssetApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimulationAssetApplication.class, args);
    }
}
