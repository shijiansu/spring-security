
```shell
# generate key for HMACSHA256, more details refer to JwtAccessTokenConverter.java
openssl rand -base64 256

# the client id and secret using in Oauth is via Basic authentication in request header;
# 3 different formats of HTTP Basic authentication
curl -X POST -H "Authorization: Basic $(echo -n some_client_id:secret | base64)" http://localhost:8081/oauth/token  -d grant_type=password -d username=mario -d password=mario123
curl -X POST -u some_client_id:secret http://localhost:8081/oauth/token  -d grant_type=password -d username=mario -d password=mario123
curl -X POST some_client_id:secret@localhost:8081/oauth/token  -d grant_type=password -d username=mario -d password=mario123
```

## Test

> -d, --data <data>
>               (HTTP)  Sends  the  specified data in a POST request to the HTTP
>               server, in the same way that a browser  does  when  a  user  has
>               filled  in an HTML form and presses the submit button. This will
>               cause curl to pass the data to the server using the content-type
>               application/x-www-form-urlencoded.  Compare to -F, --form.

So below curl is using as form submit,

```shell
# for testing
curl -v -X POST some_client_id:secret@localhost:8081/api/v1/token -d grant_type=password -d username=mario -d password=mario123 | jq
```
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE1NjIzMTY4MDEsInVzZXJfbmFtZSI6Im1hcmlvIiwiYXV0aG9yaXRpZXMiOlsiZmFzdCIsImp1bXAiXSwianRpIjoiZjU4NjBjN2UtMmFjOC00MGQwLTlhYmMtMjZhODk2YjNhNDBkIiwiY2xpZW50X2lkIjoic29tZV9jbGllbnRfaWQiLCJzY29wZSI6WyJyZWFkIiwid3JpdGUiLCJ0cnVzdCJdfQ.v0XO42gxcQQFKYBYCQrGrfFf1Ig1TNn5r7mF16Hoxz8",
  "token_type": "bearer",
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOiJtYXJpbyIsInNjb3BlIjpbInJlYWQiLCJ3cml0ZSIsInRydXN0Il0sImF0aSI6ImY1ODYwYzdlLTJhYzgtNDBkMC05YWJjLTI2YTg5NmIzYTQwZCIsImV4cCI6MTU2MjMxNzI4MSwiYXV0aG9yaXRpZXMiOlsiZmFzdCIsImp1bXAiXSwianRpIjoiNmRkNjk5ZjUtYjY0Zi00Zjk3LWE5MjgtYTY1MDc4N2M3YjEyIiwiY2xpZW50X2lkIjoic29tZV9jbGllbnRfaWQifQ.KG4uNjUIrYa8yGJCNV0dqDd1WUeUTlevQAYyXiqD0uk",
  "expires_in": 119,
  "scope": "read write trust",
  "jti": "f5860c7e-2ac8-40d0-9abc-26a896b3a40d"
}
```

Go to jwt.io to verify the signature, `https://jwt.io`

## Analysis

### Spring Security - Oauth Framework
For construct Spring Security
- AuthorizationServerSecurityConfiguration - configure `HttpSecurity` with Oauth features;
- `OAuth2AuthorizationServerConfiguration` - default OAuth2 configuration if no implementation of `AuthorizationServerConfigurer` (`AuthorizationServerConfigurerAdapter`)
- SecurityAutoConfiguration - Spring Security auto configuration
- WebSecurity.performBuild() - place to add filters into `FilterChainProxy` (stores list of Filters)

For client authentication
- ClientDetailsService - handle get client details in header;

For OAuth2 token generation
- AuthorizationServerEndpointsConfiguration - configure default authorization beans
- AuthorizationServerEndpointsConfigurer - configure default authorization beans -> give default `TokenGranter`
- DefaultTokenServices - handle OAuth2 token related logic, e.g. create access token;
- TokenStore - the storage to store OAuth2 refresh token (if setReuseRefreshToken); Using for auto renew of refresh token;
- TokenEnhancer - wrapper for enhancement of access token process;

```java
// AuthorizationServerEndpointsConfigurer
// check the GRANT_TYPE in below class
class AuthorizationServerEndpointsConfigurer{
	private List<TokenGranter> getDefaultTokenGranters() {
		ClientDetailsService clientDetails = clientDetailsService();
		AuthorizationServerTokenServices tokenServices = tokenServices();
		AuthorizationCodeServices authorizationCodeServices = authorizationCodeServices();
		OAuth2RequestFactory requestFactory = requestFactory();

		List<TokenGranter> tokenGranters = new ArrayList<TokenGranter>();
		tokenGranters.add(new AuthorizationCodeTokenGranter(tokenServices, authorizationCodeServices, clientDetails,
				requestFactory));
		// refresh_token
		tokenGranters.add(new RefreshTokenGranter(tokenServices, clientDetails, requestFactory));
		ImplicitTokenGranter implicit = new ImplicitTokenGranter(tokenServices, clientDetails, requestFactory);
		tokenGranters.add(implicit);
		tokenGranters.add(new ClientCredentialsTokenGranter(tokenServices, clientDetails, requestFactory));
		if (authenticationManager != null) {
		  // password
			tokenGranters.add(new ResourceOwnerPasswordTokenGranter(authenticationManager, tokenServices,
					clientDetails, requestFactory));
		}
		return tokenGranters;
	}

	private TokenGranter tokenGranter() {
		if (tokenGranter == null) {
			tokenGranter = new TokenGranter() {
				private CompositeTokenGranter delegate;

				@Override
				public OAuth2AccessToken grant(String grantType, TokenRequest tokenRequest) {
					if (delegate == null) {
						delegate = new CompositeTokenGranter(getDefaultTokenGranters());
					}
					return delegate.grant(grantType, tokenRequest);
				}
			};
		}
		return tokenGranter;
	}
}
```

For authentication part
- AuthenticationManager - as in Spring Security;
- OAuth2Authentication - as in Spring Security;

### Request POJO processing

- TokenEndpoint - FrameworkEndpoint - as in Spring Security - handle HTTP request, real entry point;
  - `/oauth/token` - important keyword for the investigating of Spring Security - OAuth2;
- DefaultOAuth2RequestFactory - convert parameters to `TokenRequest`;
- TokenRequest
  - POJO of actual HTTP request for token;
  - Create `OAuth2Request`;
  - TokenRequest.createOAuth2Request <- DefaultOAuth2RequestFactory.createOAuth2Request <- AbstractTokenGranter.getOAuth2Authentication (to generate `OAuth2Authentication`)
- OAuth2Request - POJO after removed password and client_secret information of TokenRequest;
- TokenGranter - to get from `TokenRequest` the `OAuth2AccessToken`;
- OAuth2AccessToken - POJO of access token;

### Request entry points

@FrameworkEndpoint
- TokenEndpoint - for /oauth/token
- AuthorizationEndpoint
- WhitelabelErrorEndpoint
- TokenKeyEndpoint
- WhitelabelApprovalEndpoint
- CheckTokenEndpoint

### Token generation

- AbstractTokenGranter
- AuthorizationCodeTokenGranter
- ClientCredentialsTokenGranter
- CompositeTokenGranter
- ImplicitTokenGranter
- RefreshTokenGranter
- ResourceOwnerPasswordTokenGranter

So the Spring Security only unwrap the request to be token POJO (plus Basic Auth), the rest of logic is in TokenEndpoint to generate the OAuth2 token.

### How does the filter work

OrRequestMatcher [requestMatchers=[Ant [pattern='/api/v1/token'], 
Ant [pattern='/oauth/token_key'], 
Ant [pattern='/oauth/check_token']]], 
[
org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter@5eed2d86, 
org.springframework.security.web.context.SecurityContextPersistenceFilter@7acfb656, 
org.springframework.security.web.header.HeaderWriterFilter@3bd3d05e, 
org.springframework.security.web.authentication.logout.LogoutFilter@3c46dcbe, 
org.springframework.security.web.authentication.www.BasicAuthenticationFilter@173797f0, 
org.springframework.security.web.savedrequest.RequestCacheAwareFilter@55a609dd, 
org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter@4d27d9d, 
org.springframework.security.web.authentication.AnonymousAuthenticationFilter@33d53216, 
org.springframework.security.web.session.SessionManagementFilter@2c6aed22, 
org.springframework.security.web.access.ExceptionTranslationFilter@4eb45fec, 
org.springframework.security.web.access.intercept.FilterSecurityInterceptor@5c73f672
]

This is a key point to authenticate in `BasicAuthenticationFilter` as "client / secret" in Basic authentication header,
so the request will pass Spring Security filters then pass to `TokenEndpoint` to handle `/oauth/token`

`BasicAuthenticationFilter -> SecurityContextHolder.getContext().setAuthentication(authResult)`

How to authenticate the Basic Auth header information 
configure by `ClientDetailsServiceConfigurer`
- BasicAuthenticationFilter -> ProviderManager.getProviders -> DaoAuthenticationProvider -> AbstractUserDetailsAuthenticationProvider.authenticate -> DaoAuthenticationProvide.retrieveUser -> ClientDetailsUserDetailsService -> InMemoryClientDetailsService

## Analysis - deep in Spring Security Framework

After enable debug log for org.springframework

**For more information, search keyword of "AutoConfiguration" in Spring Boot startup log**
