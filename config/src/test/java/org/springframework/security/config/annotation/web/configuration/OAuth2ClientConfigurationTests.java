/*
 * Copyright 2002-2019 the original author or authors.
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
package org.springframework.security.config.annotation.web.configuration;

import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.test.SpringTestRule;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.security.oauth2.client.registration.TestClientRegistrations.clientCredentials;
import static org.springframework.security.oauth2.client.registration.TestClientRegistrations.clientRegistration;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link OAuth2ClientConfiguration}.
 *
 * @author Joe Grandja
 */
public class OAuth2ClientConfigurationTests {
	@Rule
	public final SpringTestRule spring = new SpringTestRule();

	@Autowired
	private MockMvc mockMvc;

	@Test
	public void requestWhenAuthorizedClientFoundThenMethodArgumentResolved() throws Exception {
		String clientRegistrationId = "client1";
		String principalName = "user1";
		TestingAuthenticationToken authentication = new TestingAuthenticationToken(principalName, "password");

		ClientRegistrationRepository clientRegistrationRepository = mock(ClientRegistrationRepository.class);
		ClientRegistration clientRegistration = clientRegistration().registrationId(clientRegistrationId).build();
		when(clientRegistrationRepository.findByRegistrationId(eq(clientRegistrationId))).thenReturn(clientRegistration);

		OAuth2AuthorizedClientRepository authorizedClientRepository = mock(OAuth2AuthorizedClientRepository.class);
		OAuth2AuthorizedClient authorizedClient = mock(OAuth2AuthorizedClient.class);
		when(authorizedClient.getClientRegistration()).thenReturn(clientRegistration);
		when(authorizedClientRepository.loadAuthorizedClient(
				eq(clientRegistrationId), eq(authentication), any(HttpServletRequest.class)))
				.thenReturn(authorizedClient);

		OAuth2AccessToken accessToken = mock(OAuth2AccessToken.class);
		when(authorizedClient.getAccessToken()).thenReturn(accessToken);

		OAuth2AccessTokenResponseClient accessTokenResponseClient = mock(OAuth2AccessTokenResponseClient.class);

		OAuth2AuthorizedClientArgumentResolverConfig.CLIENT_REGISTRATION_REPOSITORY = clientRegistrationRepository;
		OAuth2AuthorizedClientArgumentResolverConfig.AUTHORIZED_CLIENT_REPOSITORY = authorizedClientRepository;
		OAuth2AuthorizedClientArgumentResolverConfig.ACCESS_TOKEN_RESPONSE_CLIENT = accessTokenResponseClient;
		this.spring.register(OAuth2AuthorizedClientArgumentResolverConfig.class).autowire();

		this.mockMvc.perform(get("/authorized-client").with(authentication(authentication)))
				.andExpect(status().isOk())
				.andExpect(content().string("resolved"));
		verifyZeroInteractions(accessTokenResponseClient);
	}

	@Test
	public void requestWhenAuthorizedClientNotFoundAndClientCredentialsThenTokenResponseClientIsUsed() throws Exception {
		String clientRegistrationId = "client1";
		String principalName = "user1";
		TestingAuthenticationToken authentication = new TestingAuthenticationToken(principalName, "password");

		ClientRegistrationRepository clientRegistrationRepository = mock(ClientRegistrationRepository.class);
		OAuth2AuthorizedClientRepository authorizedClientRepository = mock(OAuth2AuthorizedClientRepository.class);
		OAuth2AccessTokenResponseClient accessTokenResponseClient = mock(OAuth2AccessTokenResponseClient.class);

		ClientRegistration clientRegistration = clientCredentials().registrationId(clientRegistrationId).build();
		when(clientRegistrationRepository.findByRegistrationId(clientRegistrationId)).thenReturn(clientRegistration);

		OAuth2AccessTokenResponse accessTokenResponse = OAuth2AccessTokenResponse.withToken("access-token-1234")
				.tokenType(OAuth2AccessToken.TokenType.BEARER)
				.expiresIn(300)
				.build();
		when(accessTokenResponseClient.getTokenResponse(any(OAuth2ClientCredentialsGrantRequest.class)))
				.thenReturn(accessTokenResponse);

		OAuth2AuthorizedClientArgumentResolverConfig.CLIENT_REGISTRATION_REPOSITORY = clientRegistrationRepository;
		OAuth2AuthorizedClientArgumentResolverConfig.AUTHORIZED_CLIENT_REPOSITORY = authorizedClientRepository;
		OAuth2AuthorizedClientArgumentResolverConfig.ACCESS_TOKEN_RESPONSE_CLIENT = accessTokenResponseClient;
		this.spring.register(OAuth2AuthorizedClientArgumentResolverConfig.class).autowire();

		this.mockMvc.perform(get("/authorized-client").with(authentication(authentication)))
				.andExpect(status().isOk())
				.andExpect(content().string("resolved"));
		verify(accessTokenResponseClient, times(1)).getTokenResponse(any(OAuth2ClientCredentialsGrantRequest.class));
	}

	@EnableWebMvc
	@EnableWebSecurity
	static class OAuth2AuthorizedClientArgumentResolverConfig extends WebSecurityConfigurerAdapter {
		static ClientRegistrationRepository CLIENT_REGISTRATION_REPOSITORY;
		static OAuth2AuthorizedClientRepository AUTHORIZED_CLIENT_REPOSITORY;
		static OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> ACCESS_TOKEN_RESPONSE_CLIENT;

		@Override
		protected void configure(HttpSecurity http) {
		}

		@RestController
		public class Controller {

			@GetMapping("/authorized-client")
			public String authorizedClient(@RegisteredOAuth2AuthorizedClient("client1") OAuth2AuthorizedClient authorizedClient) {
				return authorizedClient != null ? "resolved" : "not-resolved";
			}
		}

		@Bean
		public ClientRegistrationRepository clientRegistrationRepository() {
			return CLIENT_REGISTRATION_REPOSITORY;
		}

		@Bean
		public OAuth2AuthorizedClientRepository authorizedClientRepository() {
			return AUTHORIZED_CLIENT_REPOSITORY;
		}

		@Bean
		public OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> accessTokenResponseClient() {
			return ACCESS_TOKEN_RESPONSE_CLIENT;
		}
	}

	// gh-5321
	@Test
	public void loadContextWhenOAuth2AuthorizedClientRepositoryRegisteredTwiceThenThrowNoUniqueBeanDefinitionException() {
		assertThatThrownBy(() -> this.spring.register(OAuth2AuthorizedClientRepositoryRegisteredTwiceConfig.class).autowire())
				.hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class)
				.hasMessageContaining("Expected single matching bean of type '" + OAuth2AuthorizedClientRepository.class.getName() +
					"' but found 2: authorizedClientRepository1,authorizedClientRepository2");
	}

	@EnableWebMvc
	@EnableWebSecurity
	static class OAuth2AuthorizedClientRepositoryRegisteredTwiceConfig extends WebSecurityConfigurerAdapter {

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.authorizeRequests()
					.anyRequest().authenticated()
					.and()
				.oauth2Login();
			// @formatter:on
		}

		@Bean
		public ClientRegistrationRepository clientRegistrationRepository() {
			return mock(ClientRegistrationRepository.class);
		}

		@Bean
		public OAuth2AuthorizedClientRepository authorizedClientRepository1() {
			return mock(OAuth2AuthorizedClientRepository.class);
		}

		@Bean
		public OAuth2AuthorizedClientRepository authorizedClientRepository2() {
			return mock(OAuth2AuthorizedClientRepository.class);
		}

		@Bean
		public OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> accessTokenResponseClient() {
			return mock(OAuth2AccessTokenResponseClient.class);
		}
	}

	@Test
	public void loadContextWhenClientRegistrationRepositoryNotRegisteredThenThrowNoSuchBeanDefinitionException() {
		assertThatThrownBy(() -> this.spring.register(ClientRegistrationRepositoryNotRegisteredConfig.class).autowire())
				.hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class)
				.hasMessageContaining("No qualifying bean of type '" + ClientRegistrationRepository.class.getName() + "' available");
	}

	@EnableWebMvc
	@EnableWebSecurity
	static class ClientRegistrationRepositoryNotRegisteredConfig extends WebSecurityConfigurerAdapter {

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.authorizeRequests()
					.anyRequest().authenticated()
					.and()
				.oauth2Login();
			// @formatter:on
		}
	}

	@Test
	public void loadContextWhenClientRegistrationRepositoryRegisteredTwiceThenThrowNoUniqueBeanDefinitionException() {
		assertThatThrownBy(() -> this.spring.register(ClientRegistrationRepositoryRegisteredTwiceConfig.class).autowire())
				.hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class)
				.hasMessageContaining("expected single matching bean but found 2: clientRegistrationRepository1,clientRegistrationRepository2");
	}

	@EnableWebMvc
	@EnableWebSecurity
	static class ClientRegistrationRepositoryRegisteredTwiceConfig extends WebSecurityConfigurerAdapter {

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.authorizeRequests()
					.anyRequest().authenticated()
					.and()
				.oauth2Login();
			// @formatter:on
		}

		@Bean
		public ClientRegistrationRepository clientRegistrationRepository1() {
			return mock(ClientRegistrationRepository.class);
		}

		@Bean
		public ClientRegistrationRepository clientRegistrationRepository2() {
			return mock(ClientRegistrationRepository.class);
		}

		@Bean
		public OAuth2AuthorizedClientRepository authorizedClientRepository() {
			return mock(OAuth2AuthorizedClientRepository.class);
		}

		@Bean
		public OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> accessTokenResponseClient() {
			return mock(OAuth2AccessTokenResponseClient.class);
		}
	}

	@Test
	public void loadContextWhenAccessTokenResponseClientRegisteredTwiceThenThrowNoUniqueBeanDefinitionException() {
		assertThatThrownBy(() -> this.spring.register(AccessTokenResponseClientRegisteredTwiceConfig.class).autowire())
				.hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class)
				.hasMessageContaining("expected single matching bean but found 2: accessTokenResponseClient1,accessTokenResponseClient2");
	}

	@EnableWebMvc
	@EnableWebSecurity
	static class AccessTokenResponseClientRegisteredTwiceConfig extends WebSecurityConfigurerAdapter {

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.authorizeRequests()
					.anyRequest().authenticated()
					.and()
				.oauth2Login();
			// @formatter:on
		}

		@Bean
		public ClientRegistrationRepository clientRegistrationRepository() {
			return mock(ClientRegistrationRepository.class);
		}

		@Bean
		public OAuth2AuthorizedClientRepository authorizedClientRepository() {
			return mock(OAuth2AuthorizedClientRepository.class);
		}

		@Bean
		public OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> accessTokenResponseClient1() {
			return mock(OAuth2AccessTokenResponseClient.class);
		}

		@Bean
		public OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> accessTokenResponseClient2() {
			return mock(OAuth2AccessTokenResponseClient.class);
		}
	}
}
