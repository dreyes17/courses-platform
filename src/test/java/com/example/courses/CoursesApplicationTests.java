package com.example.courses;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CoursesApplicationTests {

	@Test
	void contextLoads() {
	}

}
