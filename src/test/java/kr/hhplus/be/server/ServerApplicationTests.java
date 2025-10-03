package kr.hhplus.be.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")  // 이 부분이 중요!
class ServerApplicationTests {

	@Test
	void contextLoads() {
		// 빈 테스트 - Spring Context만 로드 확인
	}
}