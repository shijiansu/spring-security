# Spring Security Reactive获取Security context (20180318)

- <https://juejin.im/post/5aae06945188252c321974cd>

## 序

本文主要研究下reactive模式下的spring security context的获取。

## ReactiveSecurityContextHolder

springboot2支持了webflux的异步模式，那么传统的基于threadlocal的SecurityContextHolder就不管用了。spring security5.x也支持了reactive方式，这里就需要使用reactive版本的SecurityContextHolder

`spring-security-core-5.0.3.RELEASE-sources.jar!/org/springframework/security/core/context/ReactiveSecurityContextHolder.java`

```java
public class ReactiveSecurityContextHolder {
    private static final Class<?> SECURITY_CONTEXT_KEY = SecurityContext.class;

    /**
    * Gets the {@code Mono<SecurityContext>} from Reactor {@link Context}
    * @return the {@code Mono<SecurityContext>}
    */
    public static Mono<SecurityContext> getContext() {
        return Mono.subscriberContext()
            .filter( c -> c.hasKey(SECURITY_CONTEXT_KEY))
            .flatMap( c-> c.<Mono<SecurityContext>>get(SECURITY_CONTEXT_KEY));
    }

    /**
    * Clears the {@code Mono<SecurityContext>} from Reactor {@link Context}
    * @return Return a {@code Mono<Void>} which only replays complete and error signals
    * from clearing the context.
    */
    public static Function<Context, Context> clearContext() {
        return context -> context.delete(SECURITY_CONTEXT_KEY);
    }

    /**
    * Creates a Reactor {@link Context} that contains the {@code Mono<SecurityContext>}
    * that can be merged into another {@link Context}
    * @param securityContext the {@code Mono<SecurityContext>} to set in the returned
    * Reactor {@link Context}
    * @return a Reactor {@link Context} that contains the {@code Mono<SecurityContext>}
    */
    public static Context withSecurityContext(Mono<? extends SecurityContext> securityContext) {
        return Context.of(SECURITY_CONTEXT_KEY, securityContext);
    }

    /**
    * A shortcut for {@link #withSecurityContext(Mono)}
    * @param authentication the {@link Authentication} to be used
    * @return a Reactor {@link Context} that contains the {@code Mono<SecurityContext>}
    */
    public static Context withAuthentication(Authentication authentication) {
        return withSecurityContext(Mono.just(new SecurityContextImpl(authentication)));
    }
}
```

可以看到，这里利用了reactor提供的context机制来进行异步线程的变量传递。

```java
// 实例
public Mono<User> getCurrentUser() {
    return ReactiveSecurityContextHolder.getContext()
            .switchIfEmpty(Mono.error(new IllegalStateException("ReactiveSecurityContext is empty")))
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getPrincipal)
            .cast(User.class);
}
```

## ServerHttpSecurity

`spring-security-config-5.0.3.RELEASE-sources.jar!/org/springframework/security/config/web/server/ServerHttpSecurity.java`这个有
- import reactor.core.publisher.Mono;
- import reactor.util.context.Context;
的依赖. 对应于non-reactive的`HttpSecurity`

```java
public SecurityWebFilterChain build() {
    if(this.built != null) {
        throw new IllegalStateException("This has already been built with the following stacktrace. " + buildToString());
    }
    this.built = new RuntimeException("First Build Invocation").fillInStackTrace();
    if(this.headers != null) {
        this.headers.configure(this);
    }
    // 这里的build方法创建了securityContextRepositoryWebFilter并添加到webFilters里头
    WebFilter securityContextRepositoryWebFilter = securityContextRepositoryWebFilter();
    if(securityContextRepositoryWebFilter != null) {
        this.webFilters.add(securityContextRepositoryWebFilter);
    }
    if(this.csrf != null) {
        this.csrf.configure(this);
    }
    if(this.httpBasic != null) {
        this.httpBasic.authenticationManager(this.authenticationManager);
        this.httpBasic.configure(this);
    }
    if(this.formLogin != null) {
        this.formLogin.authenticationManager(this.authenticationManager);
        if(this.securityContextRepository != null) {
            this.formLogin.securityContextRepository(this.securityContextRepository);
        }
        if(this.formLogin.authenticationEntryPoint == null) {
            // 根据配置而定义的默认行为
            this.webFilters.add(new OrderedWebFilter(new LoginPageGeneratingWebFilter(), SecurityWebFiltersOrder.LOGIN_PAGE_GENERATING.getOrder()));
            this.webFilters.add(new OrderedWebFilter(new LogoutPageGeneratingWebFilter(), SecurityWebFiltersOrder.LOGOUT_PAGE_GENERATING.getOrder()));
        }
        this.formLogin.configure(this);
    }
    if(this.logout != null) {
        this.logout.configure(this);
    }
    this.requestCache.configure(this);
    this.addFilterAt(new SecurityContextServerWebExchangeWebFilter(), SecurityWebFiltersOrder.SECURITY_CONTEXT_SERVER_WEB_EXCHANGE);
    if(this.authorizeExchange != null) {
        ServerAuthenticationEntryPoint authenticationEntryPoint = getAuthenticationEntryPoint();
        ExceptionTranslationWebFilter exceptionTranslationWebFilter = new ExceptionTranslationWebFilter();
        if(authenticationEntryPoint != null) {
            exceptionTranslationWebFilter.setAuthenticationEntryPoint(
                authenticationEntryPoint);
        }
        this.addFilterAt(exceptionTranslationWebFilter, SecurityWebFiltersOrder.EXCEPTION_TRANSLATION);
        this.authorizeExchange.configure(this);
    }
    // 排序
    AnnotationAwareOrderComparator.sort(this.webFilters);
    List<WebFilter> sortedWebFilters = new ArrayList<>();
    this.webFilters.forEach( f -> {
        if(f instanceof OrderedWebFilter) {
            f = ((OrderedWebFilter) f).webFilter;
        }
        sortedWebFilters.add(f);
    });
    return new MatcherSecurityWebFilterChain(getSecurityMatcher(), sortedWebFilters);
}
```

```java
// securityContextRepositoryWebFilter
private WebFilter securityContextRepositoryWebFilter() {
    ServerSecurityContextRepository repository = this.securityContextRepository;
    if(repository == null) {
        return null;
    }
    // 这里创建了ReactorContextWebFilter
    // 实际上, 自定义filter就是自定义WebFilter
    WebFilter result = new ReactorContextWebFilter(repository); // 这里类似reactive的方式associated一起了
    // 加个wrapper, 具备order功能
    return new OrderedWebFilter(result, SecurityWebFiltersOrder.REACTOR_CONTEXT.getOrder());
}
```

## ReactorContextWebFilter

`spring-security-web-5.0.3.RELEASE-sources.jar!/org/springframework/security/web/server/context/ReactorContextWebFilter.java`

```java
// 源码解释
public class ReactorContextWebFilter implements WebFilter {
    private final ServerSecurityContextRepository repository;

    public ReactorContextWebFilter(ServerSecurityContextRepository repository) {
        Assert.notNull(repository, "repository cannot be null");
        this.repository = repository;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
        // 这里使用Reactor的subscriberContext将SecurityContext注入进去。
            .subscriberContext(c -> c.hasKey(SecurityContext.class) ? c :
                withSecurityContext(c, exchange)
            );
    }

    private Context withSecurityContext(Context mainContext, ServerWebExchange exchange) {
        return mainContext.putAll(this.repository.load(exchange)
            .as(ReactiveSecurityContextHolder::withSecurityContext));
    }
}
```

## 小结

基于Reactor提供的context机制，spring security也相应提供了ReactiveSecurityContextHolder用来获取当前用户，非常便利。

## 参考

- 聊聊reactor异步线程的变量传递 - <https://link.juejin.im/?target=https%3A%2F%2Fsegmentfault.com%2Fa%2F1190000013792541>
- 5.10.3 EnableReactiveMethodSecurity - <https://link.juejin.im/?target=https%3A%2F%2Fdocs.spring.io%2Fspring-security%2Fsite%2Fdocs%2Fcurrent%2Freference%2Fhtmlsingle%2F%23jc-erms>
