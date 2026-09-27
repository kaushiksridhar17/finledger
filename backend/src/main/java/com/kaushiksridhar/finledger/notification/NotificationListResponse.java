package com.kaushiksridhar.finledger.notification;

import java.util.List;

/** The newest notifications plus how many are unread, for the bell badge. */
public record NotificationListResponse(long unreadCount, List<NotificationResponse> items) {
}
