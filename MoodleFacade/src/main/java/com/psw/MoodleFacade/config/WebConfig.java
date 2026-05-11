package com.psw.MoodleFacade.config;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;

@Configuration
public class WebConfig {

    private HttpClient createHttpClientWithInsecureSSL() throws SSLException {
        return HttpClient.create()
                .secure(ssl -> {
                    try {
                        ssl.sslContext(
                                SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .build()
                        );
                    } catch (SSLException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    @Bean
    public WebClient moodleClient(@Value("${moodle.base-url}") String baseUrl) throws Exception {
        HttpClient httpClient = createHttpClientWithInsecureSSL();

        return WebClient.builder()
                .baseUrl(baseUrl + "/webservice/rest/server.php")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer ->
                    configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024)) //100MB just to be save
                .build();
    }

    @Bean
    public WebClient moodleUploadClient(@Value("${moodle.base-url}") String baseUrl) throws Exception {
        HttpClient httpClient = createHttpClientWithInsecureSSL();

        return WebClient.builder()
                .baseUrl(baseUrl + "/webservice/upload.php")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
