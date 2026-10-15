package tradinghub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 조회 오류 알림 (service.FailureAlerts, 10분마다)
public class TradingHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingHubApplication.class, args);
    }
}
