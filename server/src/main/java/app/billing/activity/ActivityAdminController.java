package app.billing.activity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.billing.channel.Channel;

@RestController
@RequestMapping("/api/admin/activity")
public class ActivityAdminController {

    private final ActivityService activity;

    public ActivityAdminController(ActivityService activity) {
        this.activity = activity;
    }

    public record ActivityDto(UUID id, Instant createdAt, UUID userId, String userName, Channel channel, String ip,
                              String action, String target, String details) {
        static ActivityDto of(ActivityService.ActivityEntry e) {
            return new ActivityDto(e.id(), e.createdAt(), e.userId(), e.userName(), e.channel(), e.ip(), e.action(),
                    e.target(), e.details());
        }
    }

    @GetMapping
    public List<ActivityDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String user,
            @RequestParam(defaultValue = "100") int limit) {
        return activity.search(from, to, action, user, limit).stream().map(ActivityDto::of).toList();
    }
}
