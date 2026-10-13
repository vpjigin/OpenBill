package app.billing.held;

import java.util.UUID;

import app.billing.channel.Channel;
import app.billing.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "held_bill")
public class HeldBill extends BaseEntity {

    private String label;

    @Lob
    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(name = "created_by", nullable = false, updatable = false, length = 36)
    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Channel channel;

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
}
