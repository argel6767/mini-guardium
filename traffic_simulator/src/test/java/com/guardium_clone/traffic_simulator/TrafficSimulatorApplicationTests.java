package com.guardium_clone.traffic_simulator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "management.health.rabbit.enabled=false")
class TrafficSimulatorApplicationTests {

	@Test
	void contextLoads() {
	}

}
