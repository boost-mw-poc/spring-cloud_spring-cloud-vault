/*
 * Copyright 2016-2020 the original author or authors.
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

package org.springframework.cloud.vault.config;

import javax.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Vault using the generic backend.
 *
 * @author Mark Paluch
 */
@ConfigurationProperties("spring.cloud.vault.generic")
@Validated
public class VaultGenericBackendProperties
		implements EnvironmentAware, VaultKeyValueBackendPropertiesSupport {

	/**
	 * Enable the generic backend.
	 */
	private boolean enabled = true;

	/**
	 * Name of the default backend.
	 */
	@NotEmpty
	private String backend = "secret";

	/**
	 * Name of the default context.
	 */
	@NotEmpty
	private String defaultContext = "application";

	/**
	 * Profile-separator to combine application name and profile.
	 */
	@NotEmpty
	private String profileSeparator = "/";

	/**
	 * Application name to be used for the context.
	 */
	private String applicationName = "application";

	public VaultGenericBackendProperties() {
	}

	@Override
	public void setEnvironment(Environment environment) {

		String springCloudVaultAppName = environment
				.getProperty("spring.cloud.vault.application-name");

		if (StringUtils.hasText(springCloudVaultAppName)) {
			this.applicationName = springCloudVaultAppName;
		}
		else {
			String springAppName = environment.getProperty("spring.application.name");

			if (StringUtils.hasText(springAppName)) {
				this.applicationName = springAppName;
			}
		}
	}

	@Deprecated
	@DeprecatedConfigurationProperty(
			reason = "spring.cloud.vault.generic.* is deprecated in favor of spring.cloud.vault.kv",
			replacement = "spring.cloud.vault.kv.enabled")
	public boolean isEnabled() {
		return this.enabled;
	}

	@Deprecated
	@DeprecatedConfigurationProperty(
			reason = "spring.cloud.vault.generic.* is deprecated in favor of spring.cloud.vault.kv",
			replacement = "spring.cloud.vault.kv.backend")
	public String getBackend() {
		return this.backend;
	}

	@Deprecated
	@DeprecatedConfigurationProperty(
			reason = "spring.cloud.vault.generic.* is deprecated in favor of spring.cloud.vault.kv",
			replacement = "spring.cloud.vault.kv.default-context")
	public String getDefaultContext() {
		return this.defaultContext;
	}

	@Deprecated
	@DeprecatedConfigurationProperty(
			reason = "spring.cloud.vault.generic.* is deprecated in favor of spring.cloud.vault.kv",
			replacement = "spring.cloud.vault.kv.profile-separator")
	public String getProfileSeparator() {
		return this.profileSeparator;
	}

	@Deprecated
	@DeprecatedConfigurationProperty(
			reason = "spring.cloud.vault.generic.* is deprecated in favor of spring.cloud.vault.kv",
			replacement = "spring.cloud.vault.kv.application-name")
	public String getApplicationName() {
		return this.applicationName;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setBackend(String backend) {
		this.backend = backend;
	}

	public void setDefaultContext(String defaultContext) {
		this.defaultContext = defaultContext;
	}

	public void setProfileSeparator(String profileSeparator) {
		this.profileSeparator = profileSeparator;
	}

	public void setApplicationName(String applicationName) {
		this.applicationName = applicationName;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(getClass().getSimpleName());
		sb.append(" [enabled=").append(this.enabled);
		sb.append(", backend='").append(this.backend).append('\'');
		sb.append(", defaultContext='").append(this.defaultContext).append('\'');
		sb.append(", profileSeparator='").append(this.profileSeparator).append('\'');
		sb.append(", applicationName='").append(this.applicationName).append('\'');
		sb.append(']');
		return sb.toString();
	}

}
