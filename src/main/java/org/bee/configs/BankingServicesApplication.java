package org.bee.configs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"org.bee.configs", "org.bee.banking", "org.bee.events", "org.bee.restapi", "org.bee.sample", "org.bee.common"})
@EnableAsync
@EnableCaching
@EnableScheduling
@ConfigurationPropertiesScan(basePackages = {"org.bee.configs", "org.bee.banking", "org.bee.events", "org.bee.restapi", "org.bee.sample"})
@EnableJpaRepositories(basePackages = {"org.bee.configs", "org.bee.banking", "org.bee.events"})
@EntityScan(basePackages = {"org.bee.configs", "org.bee.banking", "org.bee.events"})
public class BankingServicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingServicesApplication.class, args);
    }

}
