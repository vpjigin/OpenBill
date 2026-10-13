package app.billing.admin;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import app.billing.channel.ChannelFilter;
import app.billing.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class HealthController {

    private final AppProperties props;

    public HealthController(AppProperties props) {
        this.props = props;
    }

    @GetMapping("/api/health")
    public Map<String, String> health(HttpServletRequest req) {
        return Map.of(
                "status", "ok",
                "app", props.name(),
                "appName", props.name(),
                "channel", ChannelFilter.channelOf(req).name());
    }
}
