package io.github.dreyes17.courses;

import org.springframework.boot.SpringApplication;

public class TestCoursesApplication {

	public static void main(String[] args) {
		SpringApplication.from(CoursesApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
