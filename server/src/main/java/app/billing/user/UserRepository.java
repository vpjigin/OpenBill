package app.billing.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameIgnoreCaseAndDeletedAtIsNull(String username);

    boolean existsByUsernameIgnoreCaseAndDeletedAtIsNull(String username);

    List<User> findByDeletedAtIsNullOrderByCreatedAtAsc();

    long countByDeletedAtIsNull();

    long countByRoleAndActiveTrueAndDeletedAtIsNull(Role role);
}
