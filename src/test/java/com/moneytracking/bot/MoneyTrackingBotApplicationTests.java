package com.moneytracking.bot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.telegram.telegrambots.meta.TelegramBotsApi;

@SpringBootTest(properties = {"TELEGRAM_BOT_TOKEN=123:dummy", "gemini.api.key=dummy", "TELEGRAM_BOT_USERNAME=dummy"})
class MoneyTrackingBotApplicationTests {

    @MockitoBean
    private TelegramBotsApi telegramBotsApi; // Prevent real bot connection in tests

    @Test
    void contextLoads() {
    }

}
