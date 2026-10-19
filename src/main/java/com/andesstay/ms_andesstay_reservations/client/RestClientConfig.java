package com.andesstay.ms_andesstay_reservations.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

/**
 * Un RestClient por microservicio, con su URL base y el JWT del usuario propagado (mismo patron
 * que el BFF). Los timeouts evitan que un servicio caido deje colgada la peticion.
 */
@Configuration
@EnableConfigurationProperties(ServicesProperties.class)
public class RestClientConfig {

    @Bean
    public RestClient catalogRestClient(ServicesProperties props) {
        return conJwt(builder(props), props.catalog().url());
    }

    @Bean
    public RestClient auditRestClient(ServicesProperties props) {
        return conJwt(builder(props), props.audit().url());
    }

    /** URL base + header Authorization con el token del usuario autenticado. */
    public static RestClient conJwt(RestClient.Builder builder, String baseUrl) {
        return builder
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAuth.getToken().getTokenValue());
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    private static RestClient.Builder builder(ServicesProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return RestClient.builder().requestFactory(factory);
    }
}
