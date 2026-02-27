package com.pidabrow.starter.sample.application.port.out;

import com.pidabrow.starter.sample.domain.user.NotificationRequest;

/**
 * Outbound port for saving a notification request.
 * This is a port interface following hexagonal architecture principles.
 */
public interface SaveNotificationRequestPort {
    
    /**
     * Saves a notification request.
     * 
     * @param notificationRequest the notification request to save
     * @return the saved notification request
     */
    NotificationRequest save(NotificationRequest notificationRequest);
}

