package com.epam.java.specialization.notificationservice.service;

import com.epam.java.specialization.notificationservice.model.NotificationTemplate;
import com.epam.java.specialization.notificationservice.repository.NotificationTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Creates the built-in templates that do not exist yet; admins can edit them afterwards (never overwritten). */
@Component
public class DefaultTemplates implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultTemplates.class);

    private final NotificationTemplateRepository repository;
    private final Clock clock;

    public DefaultTemplates(NotificationTemplateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        add("WELCOME", "Welcome to Spotty!",
                "<h1>Welcome, {{displayName}}!</h1><p>Your account is ready. Go and regret some music.</p>",
                "Sent after registration. Variables: displayName.");
        add("ACCOUNT_BLOCKED", "Your Spotty account has been suspended",
                "<p>Hi {{displayName}}, your account has been suspended by an administrator.</p>",
                "Sent when an administrator blocks an account. Variables: displayName.");
        add("ACCOUNT_UNBLOCKED", "Your Spotty account has been restored",
                "<p>Hi {{displayName}}, your account is active again. Welcome back.</p>",
                "Sent when a blocked account is restored. Variables: displayName.");
        add("NEW_RELEASE", "{{artistName}} just released {{trackTitle}}",
                "<h1>{{artistName}} has a new track</h1><p><b>{{trackTitle}}</b> is out now on Spotty.</p>",
                "Sent to followers when an artist publishes a track. Variables: artistName, trackTitle.");
        add("YEAR_SUMMARY", "Your year in music, {{displayName}}",
                "<p>Hi {{displayName}}, here is your yearly listening summary.</p>",
                "Yearly listening summary. Variables: displayName.");
    }

    private void add(String code, String subject, String body, String description) {
        if (!repository.existsById(code)) {
            repository.save(new NotificationTemplate(code, subject, body, description, true, clock.instant()));
            log.info("Created default template {}", code);
        }
    }
}
