package com.moneytracking.bot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SelfPingService {

    private static final Logger logger = LoggerFactory.getLogger(SelfPingService.class);
    private final RestTemplate restTemplate;

    @Value("${APP_URL:}")
    private String appUrl;

    public SelfPingService() {
        this.restTemplate = new RestTemplate();
    }

    // Runs every 14 minutes (840000 milliseconds)
    // Render sleeps after 15 minutes of inactivity, so 14 minutes is safe.
    @Scheduled(fixedRate = 840000)
    public void pingSelf() {
        if (appUrl == null || appUrl.isEmpty()) {
            logger.debug("Self-ping bypassed: APP_URL is not set.");
            return;
        }

        try {
            String url = appUrl + "/api/health";
            logger.info("Executing self-ping to keep app awake: {}", url);
            restTemplate.getForObject(url, String.class);
            logger.info("Self-ping successful.");
        } catch (Exception e) {
            logger.error("Error during self-ping: ", e);
        }
    }
}
