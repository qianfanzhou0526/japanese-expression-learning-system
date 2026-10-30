package com.zhou.japaneselearning;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// テスト専用DB(application-test.properties)を使用し、本番DB(data/)に接続しない
@SpringBootTest
@ActiveProfiles("test")
class JapaneseLearningApplicationTests {

	@Test
	void contextLoads() {
	}

}
