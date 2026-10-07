package io.github.dreyes17.courses.identity.repository;

import io.github.dreyes17.courses.identity.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * A bulk delete, executed right away rather than at flush, so the instructor row the account references can be
     * deleted after it in the same transaction.
     */
    @Modifying
    @Query("delete from UserAccount u where u.instructorId = :instructorId")
    int deleteByInstructorId(@Param("instructorId") UUID instructorId);
}
