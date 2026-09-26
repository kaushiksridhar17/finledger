package com.kaushiksridhar.finledger.status;

import java.time.Instant;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
public class StatusController {

    private final JdbcTemplate jdbcTemplate;

    public StatusController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public StatusResponse getStatus() {
        String databaseStatus;
        String databaseVersion;

        try {
            databaseVersion = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            databaseStatus = "UP";
        } catch (DataAccessException e) {
            databaseVersion = null;
            databaseStatus = "DOWN";
        }

        return new StatusResponse(
                "FinLedger API",
                "UP",
                databaseStatus,
                databaseVersion,
                Instant.now().toString());
    }

    public record StatusResponse(
            String app,
            String api,
            String database,
            String databaseVersion,
            String serverTime) {
    }
}