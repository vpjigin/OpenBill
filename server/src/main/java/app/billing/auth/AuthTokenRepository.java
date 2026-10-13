package app.billing.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import app.billing.channel.Channel;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    Optional<AuthToken> findByTokenHash(String tokenHash);

    List<AuthToken> findByExpiresAtAfterOrderByLastSeenAtDesc(Instant now);

    @Modifying
    @Query("delete from AuthToken t where t.channel = :channel")
    int deleteByChannel(Channel channel);

    @Modifying
    @Query("delete from AuthToken t where t.user.id = :userId")
    int deleteByUserId(UUID userId);

    @Modifying
    @Query("delete from AuthToken t where t.user.id = :userId and t.id <> :keepTokenId")
    int deleteByUserIdAndIdNot(UUID userId, UUID keepTokenId);

    @Modifying
    @Query("delete from AuthToken t where t.user.id = :userId and t.channel in :channels")
    int deleteByUserIdAndChannelIn(UUID userId, java.util.Collection<Channel> channels);

    @Modifying
    @Query("delete from AuthToken t where t.expiresAt < :now")
    int deleteExpired(Instant now);
}
