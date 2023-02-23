package com.baeldung.spring;

import com.baeldung.persistence.dao.UserRepository;
import com.baeldung.security.CustomRememberMeServices;
import com.baeldung.security.google2fa.CustomAuthenticationProvider;
import com.baeldung.security.google2fa.CustomWebAuthenticationDetailsSource;
import com.baeldung.security.location.DifferentLocationChecker;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.rememberme.InMemoryTokenRepositoryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import java.io.File;
import java.io.IOException;

@ComponentScan(basePackages = { "com.baeldung.security" })
// @ImportResource({ "classpath:webSecurityConfig.xml" })
@EnableWebSecurity
public class SecSecurityConfig {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final AccessDeniedHandler accessDeniedHandler = (request, response, accessDeniedException) -> {
        LOGGER.info("{} attempted to access unauthorized URL {}", request.getUserPrincipal().getName(), request.getRequestURI());
        response.sendRedirect("/forbidden.html");
    };

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private AuthenticationSuccessHandler myAuthenticationSuccessHandler;

    @Autowired
    private LogoutSuccessHandler myLogoutSuccessHandler;

    @Autowired
    private AuthenticationFailureHandler authenticationFailureHandler;

    @Autowired
    private CustomWebAuthenticationDetailsSource authenticationDetailsSource;

    @Autowired
    private UserRepository userRepository;


    @Bean
    public AuthenticationManager authManager(HttpSecurity http) throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
            .authenticationProvider(authProvider())
            .build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
            .antMatchers("/resources/**", "/h2/**");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf()
            .disable()
            .authorizeRequests()
                .expressionHandler(webSecurityExpressionHandler())
                .antMatchers(HttpMethod.GET, "/roleHierarchy")
                .hasRole("STAFF")
            .antMatchers("/login*", "/logout*", "/signin/**", "/signup/**", "/customLogin", "/user/registration*", "/registrationConfirm*", "/expiredAccount*", "/registration*", "/badUser*", "/user/resendRegistrationToken*", "/forgetPassword*",
                "/user/resetPassword*", "/user/savePassword*", "/updatePassword*", "/user/changePassword*", "/emailError*", "/resources/**", "/old/user/registration*", "/successRegister*", "/qrcode*", "/user/enableNewLoc*")
            .permitAll()
            .antMatchers("/invalidSession*")
            .anonymous()
            .antMatchers("/user/updatePassword*")
            .hasAuthority("CHANGE_PASSWORD_PRIVILEGE")
            .antMatchers("/management*")
            .hasAuthority("MANAGE_PRIVILEGE")
            .antMatchers("/console*")
            .hasAuthority("ADMIN_PRIVILEGE")
            .and()
            .authorizeRequests()
            .anyRequest()
            .hasAuthority("READ_PRIVILEGE")
            .and()
            .formLogin()
            .loginPage("/login")
            .defaultSuccessUrl("/homepage.html")
            .failureUrl("/login?error=true")
            .successHandler(myAuthenticationSuccessHandler)
            .failureHandler(authenticationFailureHandler)
            .authenticationDetailsSource(authenticationDetailsSource)
            .permitAll()
            .and()
            .sessionManagement()
            .invalidSessionUrl("/invalidSession.html")
            .maximumSessions(1)
            .sessionRegistry(sessionRegistry())
            .and()
            .sessionFixation()
            .none()
            .and()
            .logout()
            .logoutSuccessHandler(myLogoutSuccessHandler)
            .invalidateHttpSession(true)
            .logoutSuccessUrl("/logout.html?logSucc=true")
            .deleteCookies("JSESSIONID")
            .permitAll()
            .and()
            .rememberMe()
            .rememberMeServices(rememberMeServices())
            .key("theKey")
            .and()
            .exceptionHandling()
            .defaultAccessDeniedHandlerFor(accessDeniedHandler, r -> r.getRequestURI().contains("/management"))
            .defaultAccessDeniedHandlerFor(accessDeniedHandler, r -> r.getRequestURI().contains("/console"));
        return http.build();
    }

    // beans

    @Bean
    public DaoAuthenticationProvider authProvider() {
        final CustomAuthenticationProvider authProvider = new CustomAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(encoder());
        authProvider.setPostAuthenticationChecks(differentLocationChecker());
        return authProvider;
    }

    @Bean
    public PasswordEncoder encoder() {
        return new BCryptPasswordEncoder(11);
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public RememberMeServices rememberMeServices() {
        CustomRememberMeServices rememberMeServices = new CustomRememberMeServices("theKey", userDetailsService, new InMemoryTokenRepositoryImpl());
        return rememberMeServices;
    }

    @Bean(name="GeoIPCountry")
    public DatabaseReader databaseReader() throws IOException, GeoIp2Exception {
        final File resource = new File("src/main/resources/maxmind/GeoLite2-Country.mmdb");
        return new DatabaseReader.Builder(resource).build();
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        RoleHierarchyImpl roleHierarchy = new RoleHierarchyImpl();
        String hierarchy = "ROLE_ADMIN > ROLE_MANAGER \n ROLE_ADMIN > ROLE_STAFF \n ROLE_MANAGER > ROLE_STAFF \n ROLE_STAFF > ROLE_USER";
        roleHierarchy.setHierarchy(hierarchy);
        return roleHierarchy;
    }

    @Bean
    public DefaultWebSecurityExpressionHandler webSecurityExpressionHandler() {
        DefaultWebSecurityExpressionHandler expressionHandler = new DefaultWebSecurityExpressionHandler();
        expressionHandler.setRoleHierarchy(roleHierarchy());
        return expressionHandler;
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public DifferentLocationChecker differentLocationChecker() {
        return new DifferentLocationChecker();
    }
}
