package org.brite.banking.contoller;

import org.brite.banking.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Trigger fire-and-forget task (returns immediately)
    @GetMapping("/notify")
    public String notifyUser(@RequestParam String name) {
        notificationService.sendEmail(name);
        return "Email request accepted! Check console logs in 3 seconds.";
    }

    // Trigger fire-and-forget SMS task (returns immediately)
    @GetMapping("/notify-sms")
    public String notifyUserBySms(@RequestParam String name) {
        notificationService.sendSms(name);
        return "SMS request accepted! Check console logs in 3 seconds.";
    }

    // Trigger async task that eventually returns a response
    @GetMapping("/report")
    public CompletableFuture<String> getReport() {
        return notificationService.fetchReportData();
    }
}
