package com.auction.item_service.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Configuration for Spring Scheduling and ShedLock distributed coordination.
 *
 * ShedLock ensures that scheduled tasks run on only one instance at a time
 * in a multi-instance deployment, preventing duplicate execution.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "50s")
public class SchedulerConfig {

    /**
     * Creates a JDBC-based lock provider for ShedLock.
     *
     * Uses the 'shedlock' table in PostgreSQL to coordinate locks across instances.
     *
     * @param dataSource the configured DataSource
     * @return LockProvider for distributed locking
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime() // Use database time for consistency across instances
                .build()
        );
    }
}
