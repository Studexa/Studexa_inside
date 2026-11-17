package com.studexa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.studexa.game.GameProperties;

@SpringBootApplication
@EnableConfigurationProperties(GameProperties.class)
public class StudexaApplication {

	public static void main(String[] args) {
		SpringApplication.run(StudexaApplication.class, args);
	}
}
