package com.pintogether.backend.auth;

import com.pintogether.backend.domain.User;
import com.pintogether.backend.domain.enums.RegistrationSource;
import com.pintogether.backend.repository.UserRepository;
import com.pintogether.backend.util.RandomNicknameGenerator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    @Autowired
    private UserRepository userRepository;
    @Value("${jwt.signing.key}")
    private String signingKey;
    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws ServletException, IOException {

        String registrationPk="";
        OAuth2AuthenticationToken oAuth2AuthenticationToken = (OAuth2AuthenticationToken) authentication;
        DefaultOAuth2User principal = (DefaultOAuth2User) authentication.getPrincipal();
        Map<String, Object> attributes = principal.getAttributes();

        if ("google".equals(oAuth2AuthenticationToken.getAuthorizedClientRegistrationId())) {
            registrationPk = attributes.getOrDefault("email", "").toString();
        } else if ("naver".equals(oAuth2AuthenticationToken.getAuthorizedClientRegistrationId())) {
            Map<String, Object> responseNaver = (Map<String, Object>) attributes.get("response");
            registrationPk = responseNaver.getOrDefault("id", "").toString();
            String name = responseNaver.getOrDefault("name", "").toString();
            System.out.println("registrationPk : " + registrationPk);
        } else if ("kakao".equals(oAuth2AuthenticationToken.getAuthorizedClientRegistrationId())) {
            registrationPk = attributes.getOrDefault("id", "").toString();
        }

        Optional<User> foundUser = userRepository.findByRegistrationPk(registrationPk);
        if (foundUser.isPresent()) {
            sendJwtByCookie(registrationPk, response);
        } else {
            String newNickname = RandomNicknameGenerator.generateNickname();
            User user = User.builder()
                    .nickname(newNickname)
                    .registrationSource(RegistrationSource.valueOf(oAuth2AuthenticationToken.getAuthorizedClientRegistrationId().toUpperCase()))
                    .registrationPk(registrationPk)
                    .build();
            userRepository.save(user);
            sendJwtByCookie(registrationPk, response);

        }
        this.setAlwaysUseDefaultTargetUrl(true);
        this.setDefaultTargetUrl(frontendUrl);
        super.onAuthenticationSuccess(request, response, authentication);

    }

    public void sendJwtByCookie(String registrationPk, HttpServletResponse response) {
        SecretKey key = Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8));
        String jwt = Jwts.builder()
                .setClaims(Map.of("registrationPk", registrationPk,  "role", "ROLE_USER"))
                .signWith(key)
                .compact();
        Cookie cookie = new Cookie("Authorization", jwt);
        cookie.setDomain("localhost");
        cookie.setPath("/");
        cookie.setMaxAge(30*60);
        cookie.setSecure(true);
        response.setHeader("Authorization", jwt);
        response.addCookie(cookie);
    }
}

