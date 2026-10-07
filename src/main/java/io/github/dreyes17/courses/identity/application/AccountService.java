package io.github.dreyes17.courses.identity.application;

import io.github.dreyes17.courses.catalog.application.InstructorService;
import io.github.dreyes17.courses.catalog.application.InstructorView;
import io.github.dreyes17.courses.enrollment.application.StudentService;
import io.github.dreyes17.courses.enrollment.application.StudentView;
import io.github.dreyes17.courses.identity.domain.UserAccount;
import io.github.dreyes17.courses.identity.repository.UserAccountRepository;
import io.github.dreyes17.courses.shared.application.DuplicateResourceException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private final UserAccountRepository users;
    private final StudentService students;
    private final InstructorService instructors;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final String dummyHash;

    public AccountService(UserAccountRepository users, StudentService students, InstructorService instructors,
                          PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer) {
        this.users = users;
        this.students = students;
        this.instructors = instructors;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.dummyHash = passwordEncoder.encode("timing-equalizer");
    }

    /** Self sign-up: creates the student profile and its login in one transaction. */
    @Transactional
    public StudentView registerStudent(String firstName, String lastName, String email, String password) {
        String normalizedEmail = requireUnusedEmail(email);
        StudentView student = students.register(firstName, lastName, normalizedEmail);
        users.save(UserAccount.student(normalizedEmail, passwordEncoder.encode(password), student.id()));
        return student;
    }

    @Transactional
    public InstructorView registerInstructor(String name, String email, String bio, String password) {
        String normalizedEmail = requireUnusedEmail(email);
        InstructorView instructor = instructors.create(name, normalizedEmail, bio);
        users.save(UserAccount.instructor(normalizedEmail, passwordEncoder.encode(password), instructor.id()));
        return instructor;
    }

    /**
     * Deletes the instructor together with their login account, which references them. While they still have
     * courses the instructor can't be deleted, and the account deletion rolls back with it.
     */
    @Transactional
    public void deleteInstructor(UUID instructorId) {
        users.deleteByInstructorId(instructorId);
        instructors.delete(instructorId);
    }

    @Transactional
    public boolean ensureAdmin(String email, String password) {
        String normalizedEmail = UserAccount.normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            return false;
        }
        users.save(UserAccount.admin(normalizedEmail, passwordEncoder.encode(password)));
        return true;
    }

    /**
     * An unknown email still pays for a hash comparison, so response time doesn't reveal which emails
     * have an account.
     */
    @Transactional(readOnly = true)
    public AccessToken authenticate(String email, String password) {
        Optional<UserAccount> user = users.findByEmail(UserAccount.normalizeEmail(email));
        boolean matches = passwordEncoder.matches(password, user.map(UserAccount::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }
        return tokenIssuer.issueFor(user.get());
    }

    private String requireUnusedEmail(String email) {
        String normalizedEmail = UserAccount.normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("Account", "email", normalizedEmail);
        }
        return normalizedEmail;
    }
}
