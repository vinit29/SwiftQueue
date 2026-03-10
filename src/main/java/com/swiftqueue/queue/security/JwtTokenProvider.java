package com.swiftqueue.queue.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.ArrayList;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class JwtTokenProvider {

    @Value("${security.jwt.token.secret-key:DefaultSecretKeyForSwiftQueueApplicationThatIsLongEnoughForHS256}")
    private String secretKeyString;

    @Value("${security.jwt.token.expire-length:86400000}") // 24h
    private long validityInMilliseconds;
    private Key signingKey;

    @PostConstruct
    protected void init() {
        byte[] keyBytes = this.secretKeyString.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Long ownerId) {
        Claims claims = Jwts.claims().setSubject(String.valueOf(ownerId));
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(signingKey)
                .compact();
    }

    public Authentication getAuthentication(String token) {
        String ownerId = Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(token)
            .getBody()
            .getSubject();
        return new UsernamePasswordAuthenticationToken(ownerId, "", new ArrayList<>());
    }

    public String resolveToken(HttpServletRequest req) {
        String bearerToken = req.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public boolean validateToken(String token) throws JwtException {
        Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token);
        return true;
    }

    public Long getOwnerIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return Long.parseLong(claims.getSubject());
    }
}