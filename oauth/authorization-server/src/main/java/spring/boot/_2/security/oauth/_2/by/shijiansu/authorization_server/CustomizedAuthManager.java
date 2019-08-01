package spring.boot._2.security.oauth._2.by.shijiansu.authorization_server;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

public class CustomizedAuthManager implements AuthenticationManager {

  public CustomizedAuthManager(UserDetailsService userDetailsService) {
    this.userDetailsService = userDetailsService;
  }

  private UserDetailsService userDetailsService;

  @Override
  public Authentication authenticate(Authentication a) throws AuthenticationException {
    UsernamePasswordAuthenticationToken token = null;
    UserDetails user = userDetailsService.loadUserByUsername(a.getPrincipal().toString());
    if (a.getPrincipal().equals(user.getUsername())
        && a.getCredentials().equals(user.getPassword())) {
      token = new UsernamePasswordAuthenticationToken(
          user.getUsername(),
          "",
          user.getAuthorities());
    }
    return token;
  }
}
