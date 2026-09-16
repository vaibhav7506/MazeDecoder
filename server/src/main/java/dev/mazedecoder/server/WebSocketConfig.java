package dev.mazedecoder.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer, WebMvcConfigurer {
    private final MazeSocketHandler handler;
    private final String[] origins;
    public WebSocketConfig(MazeSocketHandler handler,
                           @Value("${maze.allowed-origins:http://localhost:5173,http://localhost:3000}") String[] origins) {
        this.handler = handler;
        this.origins = origins;
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/maze").setAllowedOrigins(origins);
    }
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("POST").allowedHeaders("Content-Type");
    }
}
