package com.petcuidar.auth;

import com.petcuidar.exception.ApiErro;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
public class AutorizacaoConfig {
    @Bean
    WebMvcConfigurer autorizacao(SessaoService sessoes, JdbcTemplate banco, ObjectMapper json) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registro) {
                registro.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                            throws IOException {
                        if (!(handler instanceof HandlerMethod metodo) || "OPTIONS".equals(request.getMethod())
                                || request.getDispatcherType() == jakarta.servlet.DispatcherType.ERROR) {
                            return true;
                        }
                        Permissao regra = metodo.getMethodAnnotation(Permissao.class);
                        if (regra == null) {
                            regra = metodo.getBeanType().getAnnotation(Permissao.class);
                        }
                        response.setHeader("Cache-Control", "no-store");
                        if (regra != null && regra.publica()) {
                            return true;
                        }
                        var conta = sessoes.recuperar(token(request));
                        if (conta.isEmpty()) {
                            return recusar(response, json, 401, "Sessão necessária.");
                        }
                        if (regra == null || !Arrays.asList(regra.value()).contains(conta.get().perfil())
                                || !leitura(request) && !"1".equals(request.getHeader(AuthController.CABECALHO))) {
                            return recusar(response, json, 403, "Acesso negado.");
                        }
                        request.setAttribute(Acesso.ATRIBUTO, new Acesso(conta.get(), banco));
                        return true;
                    }
                });
            }
        };
    }

    private static boolean leitura(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
    }

    private static String token(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies).filter(cookie -> SessaoService.COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }

    private static boolean recusar(HttpServletResponse response, ObjectMapper json, int status, String mensagem)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        json.writeValue(response.getOutputStream(), new ApiErro(status, mensagem));
        return false;
    }
}
