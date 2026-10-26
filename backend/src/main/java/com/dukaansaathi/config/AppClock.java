package com.dukaansaathi.config;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** What "today" is. Normally the real date; the demo pins it with dukaan.today because the sample data ends on a fixed day. */
@Component
public class AppClock {
    private final String configuredToday;

    public AppClock(@Value("${dukaan.today:}") String configuredToday) {
        this.configuredToday = configuredToday;
    }

    public LocalDate today() {
        return configuredToday.isBlank() ? LocalDate.now() : LocalDate.parse(configuredToday);
    }
}
