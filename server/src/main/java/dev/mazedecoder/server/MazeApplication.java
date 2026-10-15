package dev.mazedecoder.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@SpringBootApplication
@EnableScheduling
public class MazeApplication {
    public static void main(String[] args) { SpringApplication.run(MazeApplication.class, args); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
