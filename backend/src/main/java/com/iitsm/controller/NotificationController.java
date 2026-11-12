package com.iitsm.controller;

import com.iitsm.dto.MarkAllReadResponse;
import com.iitsm.dto.NotificationResponse;
import com.iitsm.dto.PageResponse;
import com.iitsm.dto.UnreadCountResponse;
import com.iitsm.service.NotificationFacade;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in user's own notifications. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationFacade notifications;

    public NotificationController(NotificationFacade notifications) {
        this.notifications = notifications;
    }

    /** Newest first. {@code unreadOnly=true} leaves out the ones already read. Paging: {@code page} from 0, {@code size} (default 20, at most 100). */
    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @PageableDefault(size = 20) Pageable pageable) {
        return notifications.list(unreadOnly, pageable);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount() {
        return notifications.unreadCount();
    }

    /** Only the owner may mark a notification as read (anyone else gets 403). */
    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notifications.markRead(id);
    }

    @PostMapping("/read-all")
    public MarkAllReadResponse markAllRead() {
        return notifications.markAllRead();
    }
}
