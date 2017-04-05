package org.springframework.cloud.vault.config.databases;

import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.vault.config.VaultSecretBackendDescriptor;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Vault using the MySQL integration.
 *
 * @author Mark Paluch
 */
@ConfigurationProperties("spring.cloud.vault.mysql")
@Data
@Validated
public class VaultMySqlProperties
		implements DatabaseSecretProperties, VaultSecretBackendDescriptor {

	/**
	 * Enable mysql backend usage.
	 */
	private boolean enabled = false;

	/**
	 * Role name for credentials.
	 */
	private String role;

	/**
	 * mysql backend path.
	 */
	@NotEmpty
	private String backend = "mysql";

	/**
	 * Target property for the obtained username.
	 */
	@NotEmpty
	private String usernameProperty = "spring.datasource.username";

	/**
	 * Target property for the obtained username.
	 */
	@NotEmpty
	private String passwordProperty = "spring.datasource.password";
}
