package com.kaushiksridhar.finledger.notification;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.user.UserRepository;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository,
            UserRepository userRepository,
            Clock clock) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * Creates a notification unless one with the same dedupe key already exists for this user.
     * Returns true if a new one was created.
     */
    @Transactional
    public boolean notify(long userId, NotificationType type, String dedupeKey, String title, String message, String link) {
        if (notificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
            return false;
        }

        Notification notification = new Notification();
        notification.setUser(userRepository.getReferenceById(userId));
        notification.setType(type);
        notification.setDedupeKey(dedupeKey);
        notification.setTitle(truncate(title, 120));
        notification.setMessage(truncate(message, 255));
        notification.setLink(link);
        notification.setCreatedAt(clock.instant());
        notificationRepository.save(notification);
        return true;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(long userId) {
        return new NotificationListResponse(
                notificationRepository.countByUserIdAndReadAtIsNull(userId),
                notificationRepository.findTop30ByUserIdOrderByIdDesc(userId).stream()
                        .map(NotificationResponse::from)
                        .toList());
    }

    @Transactional
    public void markRead(long userId, long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
        }
    }

    @Transactional
    public void markAllRead(long userId) {
        notificationRepository.markAllRead(userId, clock.instant());
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }
}
