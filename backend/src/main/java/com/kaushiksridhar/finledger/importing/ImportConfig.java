package com.kaushiksridhar.finledger.importing;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Imports run on their own small thread pool, so a big file never ties up a web request thread.
 * If more files arrive than the pool and queue can hold, the upload is refused (see ImportController).
 */
@Configuration
@EnableAsync
public class ImportConfig {

    public static final String EXECUTOR = "importExecutor";

    @Bean(name = EXECUTOR)
    public Executor importExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("import-");
        executor.initialize();
        return executor;
    }
}
