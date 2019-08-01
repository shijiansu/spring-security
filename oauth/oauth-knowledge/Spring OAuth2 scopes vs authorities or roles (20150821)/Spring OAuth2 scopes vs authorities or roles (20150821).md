
- <https://stackoverflow.com/questions/32092749/spring-oauth2-scope-vs-authoritiesroles>

> 201850821
>
> The client only has scope, but we can consider/use it as an authority(roles). This is because OAuth2 spec doesn't explain specific usage of scope.
>
> Consider this, a user authorizes Twitter to post a user's tweet to Facebook. In this case, Twitter will have a scope write_facebook_status. Although user has authority to change it's own profile but this doesn't mean that Twitter can also change user's profile. In other words, scope are client authorities/roles and it's not the User's authorities/roles.

- OAuth2 scope是针对应用程序(client application)的, 例如, client A可被OAuth授权操作用户资料, client B则不可
- Spring Security role (例如`GrantedAuthority`)是针对用户(user)的, 例如, C端用户, VIP, 管理员享用不同的权限
- 两个不同维度的东西
