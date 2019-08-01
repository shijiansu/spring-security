package spring.boot._2.security.oauth._2.by.shijiansu.authorization_server;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class CustomizedUserDetailsService implements UserDetailsService {

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    org.springframework.security.core.userdetails.User user = null;
    if (username.equalsIgnoreCase("mario")) {
      List<GrantedAuthority> grants = new ArrayList<>();
      grants.add(new SimpleGrantedAuthority("jump"));
      grants.add(new SimpleGrantedAuthority("fast"));
      user = new org.springframework.security.core.userdetails.User("mario", "mario123", grants);
    } else if (username.equalsIgnoreCase("luigi")) {
      List<GrantedAuthority> grants = new ArrayList<>();
      grants.add(new SimpleGrantedAuthority("jump high"));
      grants.add(new SimpleGrantedAuthority("slow"));
      user = new org.springframework.security.core.userdetails.User("luigi", "luigi123", grants);
    }
    return user;
  }
}