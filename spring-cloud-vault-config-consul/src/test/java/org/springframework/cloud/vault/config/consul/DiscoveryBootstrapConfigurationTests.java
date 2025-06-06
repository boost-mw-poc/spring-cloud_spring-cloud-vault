/*
 * Copyright 2017-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.vault.config.consul;

import java.net.InetSocketAddress;
import java.util.List;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.agent.model.NewService;
import com.ecwid.consul.v1.catalog.model.CatalogService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.vault.util.CanConnect;
import org.springframework.cloud.vault.util.IntegrationTestSupport;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultHealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * Tests for {@link DiscoveryBootstrapConfigurationTests}.
 *
 * @author Mark Paluch
 */

@SpringBootTest(properties = { "spring.cloud.vault.discovery.enabled=true" })
@Disabled("Consul discovery client is set up in the main context, no longer in the bootstrap context")
public class DiscoveryBootstrapConfigurationTests extends IntegrationTestSupport {

	private static final String CONSUL_HOST = "localhost";

	private static final int CONSUL_PORT = 8500;

	@Autowired
	VaultOperations vaultOperations;

	@BeforeAll
	public static void beforeClass() {

		assumeTrue(CanConnect.to(new InetSocketAddress(CONSUL_HOST, CONSUL_PORT)));

		ConsulClient client = new ConsulClient();

		Response<List<CatalogService>> response = client.getCatalogService("vault", QueryParams.DEFAULT);

		if (response.getValue().isEmpty()) {

			NewService service = new NewService();
			service.setAddress("localhost");
			service.setPort(8200);
			service.setId("vault");
			service.setName("vault");

			client.agentServiceRegister(service);
		}
	}

	@Test
	public void shouldDiscoverThroughConsul() {

		VaultHealth health = this.vaultOperations.opsForSys().health();

		assertThat(health).isNotNull();
	}

}
