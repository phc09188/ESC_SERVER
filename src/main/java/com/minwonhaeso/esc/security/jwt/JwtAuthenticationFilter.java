package com.minwonhaeso.esc.security.jwt;

import com.minwonhaeso.esc.member.model.entity.Member;
import com.minwonhaeso.esc.member.service.MemberDetailService;
import com.minwonhaeso.esc.security.redis.LogoutAccessTokenRedisRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.security.auth.message.AuthException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final MemberDetailService memberDetailService;
    private final LogoutAccessTokenRedisRepository logoutAccessTokenRedisRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String accessToken = getToken(request);
        if (accessToken != null) {
            checkLogout(accessToken);
            String username = jwtTokenUtil.getUsername(accessToken);
            if (username != null) {
                UserDetails userDetails = memberDetailService.loadUserByUsername(username);
                validateAccessToken(accessToken, userDetails);
                processSecurity(request, userDetails);
            }
        }
        filterChain.doFilter(request, response);
    }
    private Authentication getAuthentication(HttpServletRequest request) throws AuthException {

        String token = request.getHeader("Authorization");

        if (token == null) {
            return null;
        }

        Claims claims;

        try {
            claims = jwtTokenUtil.extractAllClaims(token.substring("Bearer ".length()));
        } catch (JwtException e) {
            throw new AuthException("인증 토큰이 잘못되었습니다.");
        }

        Set<GrantedAuthority> roles = new HashSet<>();
        String role = (String) claims.get("role");
        roles.add(new SimpleGrantedAuthority("ROLE_" + role));

        return new UsernamePasswordAuthenticationToken(new Member(claims), null, roles);
    }

    private String getToken(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }

    private void checkLogout(String accessToken) {
        if (logoutAccessTokenRedisRepository.existsById(accessToken)) {
            throw new IllegalArgumentException("이미 로그아웃된 회원입니다.");
        }
    }

    private void equalsUsernameFromTokenAndUserDetails(String userDetailsUsername, String tokenUsername) {
        if (!userDetailsUsername.equals(tokenUsername)) {
            throw new IllegalArgumentException("name이 토큰과 맞지 않습니다.");
        }
    }

    private void validateAccessToken(String accessToken, UserDetails userDetails) {
        if (!jwtTokenUtil.validateToken(accessToken, userDetails)) {
            throw new IllegalArgumentException("토큰 검증 실패");
        }
    }

    private void processSecurity(HttpServletRequest request, UserDetails userDetails) {
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        usernamePasswordAuthenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
    }
}