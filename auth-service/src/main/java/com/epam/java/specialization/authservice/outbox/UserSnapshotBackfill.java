package com.epam.java.specialization.authservice.outbox;

import com.epam.java.specialization.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes a {@code UserSnapshot} for every user at startup, so consumers that joined later (or missed the events
 * from before snapshots existed) get the full state. Snapshots are upserts on the consumer side, so repeats are harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.snapshots.republish-on-startup", havingValue = "true", matchIfMissing = true)
public class UserSnapshotBackfill implements ApplicationRunner {

    private final UserRepository userRepository;
    private final OutboxService outboxService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Integer count = transactionTemplate.execute(status -> {
            var users = userRepository.findAll();
            users.forEach(outboxService::saveUserSnapshot);
            return users.size();
        });
        log.info("Queued UserSnapshot for {} existing users", count);
    }
}
