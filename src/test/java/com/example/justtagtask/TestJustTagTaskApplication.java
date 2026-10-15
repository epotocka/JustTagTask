package com.example.justtagtask;

import org.springframework.boot.SpringApplication;

public class TestJustTagTaskApplication {

    public static void main(String[] args) {
        SpringApplication.from(JustTagTaskApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
