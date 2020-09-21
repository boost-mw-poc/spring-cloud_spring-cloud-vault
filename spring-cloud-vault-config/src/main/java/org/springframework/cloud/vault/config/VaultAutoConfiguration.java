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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.StringUtils;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.LifecycleAwareSessionManager;
import org.springframework.vault.authentication.LifecycleAwareSessionManagerSupport;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.SimpleSessionManager;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.RestTemplateCustomizer;
import org.springframework.vault.client.RestTemplateFactory;
import org.springframework.vault.client.RestTemplateRequestCustomizer;
import org.springframework.vault.client.SimpleVaultEndpointProvider;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.config.AbstractVaultConfiguration.ClientFactoryWrapper;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.client.RestTemplate;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Spring Vault support.
 *
 * @author Spencer Gibb
 * @author Mark Paluch
 */
@ConditionalOnProperty(name = "spring.cloud.vault.enabled", matchIfMissing = true)
@EnableConfigurationProperties(VaultProperties.class)
@Order(Ordered.LOWEST_PRECEDENCE - 5)
public class VaultAutoConfiguration implements InitializingBean {

	private final ConfigurableApplicationContext applicationContext;

	private final VaultProperties vaultProperties;

	private final VaultEndpointProvider endpointProvider;

	private final List<RestTemplateCustomizer> customizers;

	private final List<RestTemplateRequestCustomizer<?>> requestCustomizers;

	private ClientFactoryWrapper clientFactoryWrapper;

	/**
	 * Used for Vault communication.
	 */
	private RestTemplateBuilder restTemplateBuilder;

	/**
	 * Used for Vault communication.
	 */
	private RestTemplateFactory restTemplateFactory;

	/**
	 * Used for external (AWS, GCP) communication.
	 */
	private RestTemplate externalRestOperations;

	public VaultAutoConfiguration(ConfigurableApplicationContext applicationContext, VaultProperties vaultProperties,
			ObjectProvider<VaultEndpointProvider> endpointProvider,
			ObjectProvider<List<RestTemplateCustomizer>> customizers,
			ObjectProvider<List<RestTemplateRequestCustomizer<?>>> requestCustomizers) {

		this.applicationContext = applicationContext;
		this.vaultProperties = vaultProperties;

		VaultEndpointProvider provider = endpointProvider.getIfAvailable();

		if (provider == null) {
			provider = SimpleVaultEndpointProvider.of(VaultConfigurationUtil.createVaultEndpoint(vaultProperties));
		}

		this.endpointProvider = provider;
		this.customizers = new ArrayList<>(customizers.getIfAvailable(Collections::emptyList));
		AnnotationAwareOrderComparator.sort(this.customizers);

		this.requestCustomizers = new ArrayList<>(requestCustomizers.getIfAvailable(Collections::emptyList));
		AnnotationAwareOrderComparator.sort(this.requestCustomizers);
	}

	@Override
	public void afterPropertiesSet() {

		this.clientFactoryWrapper = createClientFactoryWrapper();

		this.restTemplateBuilder = restTemplateBuilder(this.clientFactoryWrapper.getClientHttpRequestFactory());

		this.externalRestOperations = new RestTemplate(this.clientFactoryWrapper.getClientHttpRequestFactory());

		this.restTemplateFactory = new DefaultRestTemplateFactory(
				this.clientFactoryWrapper.getClientHttpRequestFactory(), this::restTemplateBuilder);

		this.customizers.forEach(customizer -> customizer.customize(this.externalRestOperations));
	}

	/**
	 * Create a {@link RestTemplateBuilder} initialized with {@link VaultEndpointProvider}
	 * and {@link ClientHttpRequestFactory}. May be overridden by subclasses.
	 * @return the {@link RestTemplateBuilder}.
	 * @since 2.3
	 * @see #clientHttpRequestFactoryWrapper()
	 */
	protected RestTemplateBuilder restTemplateBuilder(ClientHttpRequestFactory requestFactory) {

		RestTemplateBuilder builder = RestTemplateBuilder.builder().requestFactory(requestFactory)
				.endpointProvider(this.endpointProvider);

		this.customizers.forEach(builder::customizers);
		this.requestCustomizers.forEach(builder::requestCustomizers);

		if (StringUtils.hasText(this.vaultProperties.getNamespace())) {
			builder.defaultHeader(VaultHttpHeaders.VAULT_NAMESPACE, this.vaultProperties.getNamespace());
		}

		return builder;
	}

	/**
	 * Creates a {@link ClientFactoryWrapper} containing a
	 * {@link ClientHttpRequestFactory}. {@link ClientHttpRequestFactory} is not exposed
	 * as root bean because {@link ClientHttpRequestFactory} is configured with
	 * {@link ClientOptions} and {@link SslConfiguration} which are not necessarily
	 * applicable for the whole application.
	 * @return the {@link ClientFactoryWrapper} to wrap a {@link ClientHttpRequestFactory}
	 * instance.
	 */
	@Bean
	@ConditionalOnMissingBean
	public ClientFactoryWrapper clientHttpRequestFactoryWrapper() {
		return this.clientFactoryWrapper;
	}

	/**
	 * Create a {@link RestTemplateFactory} bean that is used to produce
	 * {@link RestTemplate}.
	 * @return the {@link RestTemplateFactory}.
	 * @see #clientHttpRequestFactoryWrapper()
	 * @since 3.0
	 */
	@Bean
	public RestTemplateFactory vaultRestTemplateFactory() {
		return this.restTemplateFactory;
	}

	/**
	 * Creates a {@link VaultTemplate}.
	 * @return the {@link VaultTemplate} bean.
	 * @see VaultAutoConfiguration#clientHttpRequestFactoryWrapper()
	 */
	@Bean
	@ConditionalOnMissingBean(VaultOperations.class)
	public VaultTemplate vaultTemplate() {

		VaultProperties.AuthenticationMethod authentication = this.vaultProperties.getAuthentication();

		if (authentication == VaultProperties.AuthenticationMethod.NONE) {
			return new VaultTemplate(this.restTemplateBuilder);
		}

		return new VaultTemplate(this.restTemplateBuilder, this.applicationContext.getBean(SessionManager.class));
	}

	/**
	 * Creates a new {@link TaskSchedulerWrapper} that encapsulates a bean implementing
	 * {@link TaskScheduler} and {@link AsyncTaskExecutor}.
	 * @return the {@link TaskSchedulerWrapper} bean.
	 * @see ThreadPoolTaskScheduler
	 */
	@Bean
	@Lazy
	@ConditionalOnMissingBean(TaskSchedulerWrapper.class)
	public TaskSchedulerWrapper vaultTaskScheduler() {

		ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
		threadPoolTaskScheduler.setPoolSize(2);
		threadPoolTaskScheduler.setDaemon(true);
		threadPoolTaskScheduler.setThreadNamePrefix("Spring-Cloud-Vault-");

		// This is to destroy bootstrap resources
		// otherwise, the bootstrap context is not shut down cleanly
		this.applicationContext.registerShutdownHook();

		return new TaskSchedulerWrapper(threadPoolTaskScheduler);
	}

	/**
	 * @return the {@link SessionManager} for Vault session management.
	 * @param clientAuthentication the {@link ClientAuthentication}.
	 * @param asyncTaskExecutorFactory the {@link ObjectFactory} for
	 * {@link TaskSchedulerWrapper}.
	 * @see SessionManager
	 * @see LifecycleAwareSessionManager
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnAuthentication
	public SessionManager vaultSessionManager(ClientAuthentication clientAuthentication,
			ObjectFactory<TaskSchedulerWrapper> asyncTaskExecutorFactory) {

		VaultProperties.SessionLifecycle lifecycle = this.vaultProperties.getSession().getLifecycle();

		if (lifecycle.isEnabled()) {
			RestTemplate restTemplate = this.restTemplateFactory.create();
			LifecycleAwareSessionManagerSupport.RefreshTrigger trigger = new LifecycleAwareSessionManagerSupport.FixedTimeoutRefreshTrigger(
					lifecycle.getRefreshBeforeExpiry(), lifecycle.getExpiryThreshold());
			return new LifecycleAwareSessionManager(clientAuthentication,
					asyncTaskExecutorFactory.getObject().getTaskScheduler(), restTemplate, trigger);
		}

		return new SimpleSessionManager(clientAuthentication);
	}

	/**
	 * @return the {@link ClientAuthentication} to obtain a
	 * {@link org.springframework.vault.support.VaultToken}.
	 * @see SessionManager
	 * @see LifecycleAwareSessionManager
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnAuthentication
	public ClientAuthentication clientAuthentication() {

		RestTemplate restTemplate = this.restTemplateFactory.create();
		ClientAuthenticationFactory factory = new ClientAuthenticationFactory(this.vaultProperties, restTemplate,
				this.externalRestOperations);

		return factory.createClientAuthentication();
	}

	protected ClientFactoryWrapper createClientFactoryWrapper() {
		return new ClientFactoryWrapper(VaultConfigurationUtil.createClientHttpRequestFactory(this.vaultProperties));
	}

	/**
	 * Wrapper to keep {@link TaskScheduler} local to Spring Cloud Vault.
	 */
	public static class TaskSchedulerWrapper implements InitializingBean, DisposableBean {

		private final ThreadPoolTaskScheduler taskScheduler;

		public TaskSchedulerWrapper(ThreadPoolTaskScheduler taskScheduler) {
			this.taskScheduler = taskScheduler;
		}

		ThreadPoolTaskScheduler getTaskScheduler() {
			return this.taskScheduler;
		}

		@Override
		public void destroy() throws Exception {
			this.taskScheduler.destroy();
		}

		@Override
		public void afterPropertiesSet() throws Exception {
			this.taskScheduler.afterPropertiesSet();
		}

	}

}
