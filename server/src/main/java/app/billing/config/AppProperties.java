package app.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String name,
        String dataDir,
        int adminPort,
        int billingPort,
        int publicPort,
        String cloudflaredPath,
        String adminSecret,
        String binDir) {
}
