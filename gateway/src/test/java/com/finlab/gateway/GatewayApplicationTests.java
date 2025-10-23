package com.finlab.gateway;

import com.finlab.gateway.config.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayApplicationTests extends BaseIntegrationTest {

	@Test
	void contextLoads() {
		// Verifies that Spring application context loads successfully
		assertTrue(true, "Application context should load without errors");
	}

}
