package spring.boot._2.security.oauth._2.by.shijiansu.authorization_server;

import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;
import org.springframework.security.oauth2.provider.token.TokenEnhancerChain;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;

@Configuration
public class OAuth2Configuration {

  private String jwtSigningKey = "unasklmconoxasdfsjfbsjdfbadsfdsafku";

  @Bean
  public JwtAccessTokenConverter accessTokenConverter() {
    JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
    converter.setSigningKey(jwtSigningKey);
    return converter;
  }

  @Bean
  public TokenStore tokenStore() {
    return new JwtTokenStore(accessTokenConverter());
  }

  @Bean
  @Primary
  // TODO - this one may not be in used
  public DefaultTokenServices tokenServices() {
    DefaultTokenServices defaultTokenServices = new DefaultTokenServices();
    defaultTokenServices.setTokenStore(tokenStore());
    defaultTokenServices.setSupportRefreshToken(true); // to accept refresh token in request
    return defaultTokenServices;
  }

  @Bean
  public AuthenticationManager customizedAuthManager() {
    return new CustomizedAuthManager(customizedUserDetailsService());
  }

  @Bean
  public UserDetailsService customizedUserDetailsService() {
    return new CustomizedUserDetailsService();
  }

  @Bean
  public TokenEnhancer tokenEnhancer() {
    return new CustomizedTokenEnhancer();
  }

  // If enable @EnableAuthorizationServer, some of default authentication beans are initiated,
  // else it will apply the "HttpSecurity" default setting in Spring Security - csrf() will block the request if no additional settings;
  @EnableAuthorizationServer
  // import: AuthorizationServerEndpointsConfiguration (important), AuthorizationServerSecurityConfiguration
  // default implementor - OAuth2AuthorizationServerConfiguration.java
  @Configuration
  class AuthorizationServerWithJwtConfiguration extends AuthorizationServerConfigurerAdapter {

    @Override
    public void configure(ClientDetailsServiceConfigurer clients)
        throws Exception {
      clients
          .inMemory()
          .withClient("some_client_id")
          // "refresh_token" value here to enable refresh token generation - by DefaultTokenService.isSupportRefreshToken()
          .authorizedGrantTypes("password", "refresh_token")
          .secret("{noop}secret") // no encode for the secret
          .scopes("read", "write", "trust")
          .accessTokenValiditySeconds(120) // Access token is only valid for 2 minutes
          .refreshTokenValiditySeconds(600); // Refresh token is only valid for 10 minutes
    }

    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
      // customize token content - delegate multiple enhancers
      final TokenEnhancerChain tokenEnhancerChain = new TokenEnhancerChain();
      tokenEnhancerChain.setTokenEnhancers(Arrays.asList(tokenEnhancer(), accessTokenConverter()));

      endpoints
          .tokenEnhancer(tokenEnhancerChain)
          .authenticationManager(customizedAuthManager())
          // customized /oauth/token URI path. Refers to "AuthorizationServerSecurityConfiguration"
          .pathMapping("/oauth/token", "/api/v1/token")
          // only accept POST
          .allowedTokenEndpointRequestMethods(HttpMethod.POST)
          .tokenStore(tokenStore())
          // inside TokenEndpoint, this accessTokenConverter will become default granter enhancer if tokenEnhancer does not set
          .accessTokenConverter(accessTokenConverter());
    }
  }

  // "AuthorizationServerSecurityConfiguration" this one implement from "WebSecurityConfigurerAdapter";
  // The souce code of "AuthorizationServerSecurityConfiguration" is good to help you to understand the feature;
  // "WebSecurityConfigurerAdapter" is the normal Spring Security which also implement from "WebSecurityConfigurerAdapter";

// To disable csrf(); for the OAuth authorization server, it makes it disable already.
//  @Configuration
//  class CustomizedConfig extends WebSecurityConfigurerAdapter {
//
//    @Override
//    protected void configure(HttpSecurity http) throws Exception {
//      http
//          .authorizeRequests()
//          .anyRequest().authenticated()
//          .and()
//          .httpBasic()
//          .and()
//          .csrf().disable();
//    }
//  }
}
