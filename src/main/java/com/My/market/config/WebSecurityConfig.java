package com.My.market.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Autowired
    private DataSource dataSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf((csrfConfig) -> csrfConfig.disable())
                .authorizeHttpRequests((requests) -> requests
                        .requestMatchers("/", "/account/register", "/css/**").permitAll()
                        // 가입 화면의 아이디 중복 확인만 로그인 없이 허용한다. 나머지 /api/** 는 모두 로그인이 필요하다.
                        // (예전에는 /api/** 전체가 열려 있어서 로그인 없이 회원 목록 조회·상품/계정 삭제가 가능했다)
                        .requestMatchers(HttpMethod.POST, "/api/users/ConfirmId").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin((form) -> form
                        .loginPage("/account/login")
                        .permitAll()
                )
                .logout((logout) -> logout.permitAll());

        return http.build();
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth)
            throws Exception {
        auth.jdbcAuthentication()
                .dataSource(dataSource)
                .passwordEncoder(passwordEncoder())
        /*인증*/ .usersByUsernameQuery("select username,password,enabled "
                        + "from user "
                        + "where username = ?")
        /*권한*/ .authoritiesByUsernameQuery("select u.username, r.name "
                        + "from user_role ur inner join user u on ur.user_id = u.id "
                        + "inner join role r on ur.role_id = r.id "
                        + "where u.username = ?");
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

//Authentication - 로그인
//Authorization - 권한