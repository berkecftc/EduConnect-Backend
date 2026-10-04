package com.educonnect.postservice.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JobLock {

    private final JdbcTemplate jdbcTemplate;

    public JobLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryAcquire(String job) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select pg_try_advisory_xact_lock(hashtext(?))", Boolean.class, job));
    }
}
