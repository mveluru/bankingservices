package org.brite;

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

@SpringBootApplication(scanBasePackages = {"org.brite.banking", "org.brite.sample", "org.brite.common"})
@EnableAsync
@EnableCaching
@EnableScheduling
@ConfigurationPropertiesScan(basePackages = {"org.brite.banking", "org.brite.sample"})
@EnableJpaRepositories(basePackages = "org.brite.banking")
@EntityScan(basePackages = "org.brite.banking")
public class BankingServicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingServicesApplication.class, args);
    }

}
