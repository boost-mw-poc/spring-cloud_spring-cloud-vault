/*
 * Copyright 2017-2020 the original author or authors.
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.vault.authentication.AuthenticationStepsFactory;
import org.springframework.vault.authentication.AuthenticationStepsOperator;
import org.springframework.vault.authentication.CachingVaultTokenSupplier;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.ReactiveLifecycleAwareSessionManager;
import org.springframework.vault.authentication.ReactiveSessionManager;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.authentication.VaultTokenSupplier;
import org.springframework.vault.client.ClientHttpConnectorFactory;
import org.springframework.vault.client.SimpleVaultEndpointProvider;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.WebClientBuilder;
import org.springframework.vault.client.WebClientCustomizer;
import org.springframework.vault.client.WebClientFactory;
import org.springframework.vault.core.ReactiveVaultOperations;
import org.springframework.vault.core.ReactiveVaultTemplate;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.vault.config.VaultAutoConfiguration.TaskSchedulerWrapper;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for reactive Spring Vault support.
 * <p>
 * This auto-configuration only supports static endpoints without
 * {@link org.springframework.vault.client.VaultEndpointProvider} support as endpoint
 * providers could be potentially blocking implementations.
 *
 * @author Mark Paluch
 * @since 2.0.0
 */
@ConditionalOnProperty(name = "spring.cloud.vault.enabled", matchIfMissing = true)
@ConditionalOnExpression("${spring.cloud.vault.reactive.enabled:true}")
@ConditionalOnClass({ Flux.class, WebClient.class, ReactiveVaultOperations.class, HttpClient.class })
@EnableConfigurationProperties({ VaultProperties.class })
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class VaultReactiveBootstrapConfiguration implements InitializingBean {

	// TODO: Expose as AutoConfiguration
	private final VaultProperties vaultProperties;

	private final VaultEndpointProvider endpointProvider;

	private final List<WebClientCustomizer> customizers;

	private ClientHttpConnector clientHttpConnector;

	/**
	 * Used for Vault communication.
	 */
	private WebClientBuilder webClientBuilder;

	/**
	 * Used for Vault communication.
	 */
	private WebClientFactory webClientFactory;

	public VaultReactiveBootstrapConfiguration(VaultProperties vaultProperties,
			ObjectProvider<VaultEndpointProvider> endpointProvider,
			ObjectProvider<List<WebClientCustomizer>> webClientCustomizers) {

		this.vaultProperties = vaultProperties;

		this.endpointProvider = endpointProvider.getIfAvailable(
				() -> SimpleVaultEndpointProvider.of(VaultConfigurationUtil.createVaultEndpoint(vaultProperties)));
		this.customizers = new ArrayList<>(webClientCustomizers.getIfAvailable(Collections::emptyList));
		AnnotationAwareOrderComparator.sort(this.customizers);
	}

	@Override
	public void afterPropertiesSet() {

		this.clientHttpConnector = createConnector(this.vaultProperties);

		this.webClientBuilder = webClientBuilder(this.clientHttpConnector);

		this.webClientFactory = new DefaultWebClientFactory(this.clientHttpConnector, this::webClientBuilder);
	}

	protected WebClientBuilder webClientBuilder(ClientHttpConnector connector) {

		WebClientBuilder builder = WebClientBuilder.builder().httpConnector(connector)
				.endpointProvider(this.endpointProvider);

		this.customizers.forEach(builder::customizers);

		if (StringUtils.hasText(this.vaultProperties.getNamespace())) {
			builder.defaultHeader(VaultHttpHeaders.VAULT_NAMESPACE, this.vaultProperties.getNamespace());
		}

		return builder;
	}

	/**
	 * Creates a {@link ClientHttpConnector} configured with {@link ClientOptions} and
	 * {@link SslConfiguration} which are not necessarily applicable for the whole
	 * application.
	 * @param vaultProperties the Vault properties.
	 * @return the {@link ClientHttpConnector}.
	 */
	protected ClientHttpConnector createConnector(VaultProperties vaultProperties) {

		ClientOptions clientOptions = new ClientOptions(Duration.ofMillis(vaultProperties.getConnectionTimeout()),
				Duration.ofMillis(vaultProperties.getReadTimeout()));

		SslConfiguration sslConfiguration = VaultConfigurationUtil.createSslConfiguration(vaultProperties.getSsl());

		return ClientHttpConnectorFactory.create(clientOptions, sslConfiguration);
	}

	/**
	 * Create a {@link WebClientFactory} bean that is used to produce {@link WebClient}.
	 * @return the {@link WebClientFactory}.
	 * @since 3.0
	 */
	@Bean
	public WebClientFactory vaultWebClientFactory() {
		return this.webClientFactory;
	}

	/**
	 * Creates a {@link ReactiveVaultTemplate}.
	 * @return the {@link ReactiveVaultTemplate} bean.
	 * @see #reactiveVaultSessionManager(BeanFactory, ObjectFactory)
	 */
	@Bean
	@ConditionalOnMissingBean(ReactiveVaultOperations.class)
	public ReactiveVaultTemplate reactiveVaultTemplate(ObjectProvider<ReactiveSessionManager> sessionManager) {

		if (this.vaultProperties.getAuthentication() == VaultProperties.AuthenticationMethod.NONE) {
			return new ReactiveVaultTemplate(this.webClientBuilder);
		}

		return new ReactiveVaultTemplate(this.webClientBuilder, sessionManager.getObject());
	}

	/**
	 * @param beanFactory the {@link BeanFactory}.
	 * @param asyncTaskExecutorFactory the {@link ObjectFactory} for
	 * {@link TaskSchedulerWrapper}.
	 * @return {@link ReactiveSessionManager} for reactive session use.
	 * @see ReactiveSessionManager
	 * @see ReactiveLifecycleAwareSessionManager
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnAuthentication
	public ReactiveSessionManager reactiveVaultSessionManager(BeanFactory beanFactory,
			ObjectFactory<TaskSchedulerWrapper> asyncTaskExecutorFactory) {

		VaultTokenSupplier vaultTokenSupplier = beanFactory.getBean("vaultTokenSupplier", VaultTokenSupplier.class);

		VaultProperties.SessionLifecycle lifecycle = this.vaultProperties.getSession().getLifecycle();
		if (lifecycle.isEnabled()) {
			WebClient webClient = this.webClientFactory.create();
			ReactiveLifecycleAwareSessionManager.RefreshTrigger trigger = new ReactiveLifecycleAwareSessionManager.FixedTimeoutRefreshTrigger(
					lifecycle.getRefreshBeforeExpiry(), lifecycle.getExpiryThreshold());
			return new ReactiveLifecycleAwareSessionManager(vaultTokenSupplier,
					asyncTaskExecutorFactory.getObject().getTaskScheduler(), webClient, trigger);
		}

		return CachingVaultTokenSupplier.of(vaultTokenSupplier);
	}

	/**
	 * @param sessionManager the {@link ReactiveSessionManager}.
	 * @return {@link SessionManager} adapter wrapping {@link ReactiveSessionManager}.
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnAuthentication
	public SessionManager vaultSessionManager(ReactiveSessionManager sessionManager) {
		return () -> {

			VaultToken token = sessionManager.getSessionToken().block();
			Assert.state(token != null, "ReactiveSessionManager returned a null VaultToken");
			return token;
		};
	}

	/**
	 * @param beanFactory the {@link BeanFactory}.
	 * @return the {@link VaultTokenSupplier} for reactive Vault session management
	 * adapting {@link ClientAuthentication} that also implement
	 * {@link AuthenticationStepsFactory}.
	 * @see AuthenticationStepsFactory
	 */
	@Bean
	@ConditionalOnMissingBean(name = "vaultTokenSupplier")
	@ConditionalOnAuthentication
	public VaultTokenSupplier vaultTokenSupplier(ListableBeanFactory beanFactory) {

		Assert.notNull(beanFactory, "BeanFactory must not be null");

		String[] authStepsFactories = beanFactory.getBeanNamesForType(AuthenticationStepsFactory.class);

		if (!ObjectUtils.isEmpty(authStepsFactories)) {

			AuthenticationStepsFactory factory = beanFactory.getBean(AuthenticationStepsFactory.class);
			return createAuthenticationStepsOperator(factory);
		}

		String[] clientAuthentications = beanFactory.getBeanNamesForType(ClientAuthentication.class);

		if (!ObjectUtils.isEmpty(clientAuthentications)) {

			ClientAuthentication clientAuthentication = beanFactory.getBean(ClientAuthentication.class);

			if (clientAuthentication instanceof TokenAuthentication) {

				TokenAuthentication authentication = (TokenAuthentication) clientAuthentication;
				return () -> Mono.just(authentication.login());
			}

			if (clientAuthentication instanceof AuthenticationStepsFactory) {
				return createAuthenticationStepsOperator((AuthenticationStepsFactory) clientAuthentication);
			}

			throw new IllegalStateException(String.format("Cannot construct VaultTokenSupplier from %s. "
					+ "ClientAuthentication must implement AuthenticationStepsFactory or be TokenAuthentication",
					clientAuthentication));
		}

		throw new IllegalStateException(
				"Cannot construct VaultTokenSupplier. Please configure VaultTokenSupplier bean named vaultTokenSupplier.");
	}

	private VaultTokenSupplier createAuthenticationStepsOperator(AuthenticationStepsFactory factory) {
		WebClient webClient = this.webClientFactory.create();
		return new AuthenticationStepsOperator(factory.getAuthenticationSteps(), webClient);
	}

}
