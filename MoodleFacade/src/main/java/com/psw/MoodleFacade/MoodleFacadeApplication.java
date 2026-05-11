package com.psw.MoodleFacade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MoodleFacadeApplication {

	public static void main(String[] args) {
		SpringApplication.run(MoodleFacadeApplication.class, args);
	}

}