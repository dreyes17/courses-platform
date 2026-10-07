package io.github.dreyes17.courses.identity.application;

import io.github.dreyes17.courses.catalog.application.InstructorService;
import io.github.dreyes17.courses.enrollment.application.StudentService;
import io.github.dreyes17.courses.enrollment.application.StudentView;
import io.github.dreyes17.courses.identity.domain.Role;
import io.github.dreyes17.courses.identity.domain.UserAccount;
import io.github.dreyes17.courses.identity.repository.UserAccountRepository;
import io.github.dreyes17.courses.shared.application.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String DUMMY_HASH = "{bcrypt}dummy";

    @Mock
    private UserAccountRepository users;
    @Mock
    private StudentService students;
    @Mock
    private InstructorService instructors;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenIssuer tokenIssuer;

    private AccountService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("timing-equalizer")).thenReturn(DUMMY_HASH);
        service = new AccountService(users, students, instructors, passwordEncoder, tokenIssuer);
    }

    @Test
    void unknownEmailStillPaysForAHashComparisonAndFails() {
        when(users.findByEmail("nobody@learn.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("nobody@learn.test", "some-password"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwordEncoder).matches("some-password", DUMMY_HASH);
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void wrongPasswordFailsWithTheSameError() {
        UserAccount user = UserAccount.student("ada@learn.test", "{bcrypt}real", UUID.randomUUID());
        when(users.findByEmail("ada@learn.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "{bcrypt}real")).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate("ada@learn.test", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    void validCredentialsIssueATokenAndEmailIsCaseInsensitive() {
        UserAccount user = UserAccount.student("ada@learn.test", "{bcrypt}real", UUID.randomUUID());
        var token = new AccessToken("jwt", "Bearer", 3600);
        when(users.findByEmail("ada@learn.test")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("right-password", "{bcrypt}real")).thenReturn(true);
        when(tokenIssuer.issueFor(user)).thenReturn(token);

        assertThat(service.authenticate("  Ada@Learn.TEST ", "right-password")).isEqualTo(token);
    }

    @Test
    void studentSignUpStoresOnlyThePasswordHash() {
        UUID studentId = UUID.randomUUID();
        when(users.existsByEmail("ada@learn.test")).thenReturn(false);
        when(students.register("Ada", "Lovelace", "ada@learn.test"))
                .thenReturn(new StudentView(studentId, "Ada", "Lovelace", "ada@learn.test", Instant.now()));
        when(passwordEncoder.encode("plain-password")).thenReturn("{bcrypt}hashed");

        service.registerStudent("Ada", "Lovelace", "Ada@Learn.test", "plain-password");

        var saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}hashed");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.STUDENT);
        assertThat(saved.getValue().getStudentId()).isEqualTo(studentId);
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@learn.test");
    }

    @Test
    void emailAlreadyTakenIsRejectedBeforeCreatingAnyProfile() {
        when(users.existsByEmail("ada@learn.test")).thenReturn(true);

        assertThatThrownBy(() -> service.registerStudent("Ada", "Lovelace", "ada@learn.test", "plain-password"))
                .isInstanceOf(DuplicateResourceException.class);
        verifyNoInteractions(students);
    }
}
