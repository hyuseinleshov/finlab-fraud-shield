package com.finlab.accounts;

import com.finlab.accounts.config.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountsApplicationTests extends BaseIntegrationTest {

	@Test
	void contextLoads() {
		// Verifies that Spring application context loads successfully
		assertTrue(true, "Application context should load without errors");
	}

}
