package org.bee.banking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bee.banking.messages.BankingMessages;
import org.bee.banking.domain.BriteEmailConfigValues;
import org.bee.banking.domain.BriteSmsNotificationConfigValues;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final BriteEmailConfigValues emailConfigValues;
    private final BriteSmsNotificationConfigValues smsConfigValues;

    private final AtomicInteger dailySmsCount = new AtomicInteger(0);
    private volatile LocalDate smsCounterDate = LocalDate.now();

    // 1. Fire-and-Forget (void return type)
    @Async
    public void sendEmail(String customer) {
        if (!emailConfigValues.isEnabled()) {
            log.info(BankingMessages.LOG_EMAIL_DISABLED, customer);
            return;
        }
        try {
            Thread.sleep(3000); // Simulate 3-second delay
            log.info(BankingMessages.LOG_EMAIL_SENT, customer, Thread.currentThread().getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(BankingMessages.LOG_EMAIL_INTERRUPTED, customer);
        }
    }

    @Async
    public void sendSms(String customer) {
        if (!smsConfigValues.isEnabled()) {
            log.info(BankingMessages.LOG_SMS_DISABLED, customer);
            return;
        }
        if (!reserveDailySmsSlot()) {
            log.warn(BankingMessages.LOG_SMS_DAILY_LIMIT_REACHED, smsConfigValues.getDailyLimit(), customer);
            return;
        }
        try {
            Thread.sleep(3000); // Simulate 3-second delay
            log.info(BankingMessages.LOG_SMS_SENT, customer, Thread.currentThread().getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(BankingMessages.LOG_SMS_INTERRUPTED, customer);
        }
    }

    private synchronized boolean reserveDailySmsSlot() {
        LocalDate today = LocalDate.now();
        if (!today.equals(smsCounterDate)) {
            smsCounterDate = today;
            dailySmsCount.set(0);
        }
        if (dailySmsCount.get() >= smsConfigValues.getDailyLimit()) {
            return false;
        }
        dailySmsCount.incrementAndGet();
        return true;
    }

    // 2. Async method returning a result
    @Async
    public CompletableFuture<String> fetchReportData() {
        try {
            Thread.sleep(3000); // Simulate delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(BankingMessages.LOG_REPORT_INTERRUPTED);
        }
        log.info(BankingMessages.LOG_REPORT_GENERATED);
        return CompletableFuture.completedFuture("Report generated successfully!");
    }
}
