package com.huawei.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.huawei.auth.model.User;
import com.huawei.auth.repository.UserRepository;

/**
 * Unit tests for UserService.
 *
 * Tests cover authentication, user creation, password updates,
 * and account management functionality.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
@SuppressWarnings("null")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Nested
    @DisplayName("authenticate()")
    class AuthenticateTests {

        @Test
        @DisplayName("Should return user when credentials are valid")
        void authenticate_ValidCredentials_ReturnsUser() {
            // Given
            String username = "testuser";
            String rawPassword = "securePassword123";
            String encodedPassword = "$2a$10$encoded";

            User user = createUser(1L, username, encodedPassword, "USER", true);
            when(userRepository.findByUsernameAndEnabledTrue(username))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches(rawPassword, encodedPassword))
                    .thenReturn(true);

            // When
            Optional<User> result = userService.authenticate(username, rawPassword);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo(username);
            verify(userRepository).findByUsernameAndEnabledTrue(username);
            verify(passwordEncoder).matches(rawPassword, encodedPassword);
        }

        @Test
        @DisplayName("Should return empty when password is incorrect")
        void authenticate_IncorrectPassword_ReturnsEmpty() {
            // Given
            String username = "testuser";
            String wrongPassword = "wrongPassword";
            String encodedPassword = "$2a$10$encoded";

            User user = createUser(1L, username, encodedPassword, "USER", true);
            when(userRepository.findByUsernameAndEnabledTrue(username))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches(wrongPassword, encodedPassword))
                    .thenReturn(false);

            // When
            Optional<User> result = userService.authenticate(username, wrongPassword);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty when user does not exist")
        void authenticate_UserNotFound_ReturnsEmpty() {
            // Given
            String username = "nonexistent";
            String password = "anyPassword";

            when(userRepository.findByUsernameAndEnabledTrue(username))
                    .thenReturn(Optional.empty());

            // When
            Optional<User> result = userService.authenticate(username, password);

            // Then
            assertThat(result).isEmpty();
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("Should return empty when user is disabled")
        void authenticate_DisabledUser_ReturnsEmpty() {
            // Given
            String username = "disableduser";
            String password = "anyPassword";

            // findByUsernameAndEnabledTrue won't return disabled users
            when(userRepository.findByUsernameAndEnabledTrue(username))
                    .thenReturn(Optional.empty());

            // When
            Optional<User> result = userService.authenticate(username, password);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty for null username")
        void authenticate_NullUsername_ReturnsEmpty() {
            // When
            Optional<User> result = userService.authenticate(null, "password");

            // Then
            assertThat(result).isEmpty();
            verify(userRepository, never()).findByUsernameAndEnabledTrue(anyString());
        }

        @Test
        @DisplayName("Should return empty for null password")
        void authenticate_NullPassword_ReturnsEmpty() {
            // When
            Optional<User> result = userService.authenticate("testuser", null);

            // Then
            assertThat(result).isEmpty();
            verify(userRepository, never()).findByUsernameAndEnabledTrue(anyString());
        }
    }

    @Nested
    @DisplayName("createUser()")
    class CreateUserTests {

        @Test
        @DisplayName("Should create user with encoded password")
        void createUser_ValidInput_CreatesUser() {
            // Given
            String username = "newuser";
            String rawPassword = "securePassword123";
            String encodedPassword = "$2a$10$encoded";
            String role = "USER";

            when(userRepository.existsByUsername(username)).thenReturn(false);
            when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });

            // When
            User result = userService.createUser(username, rawPassword, role);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo(username);
            assertThat(result.getPasswordHash()).isEqualTo(encodedPassword);
            assertThat(result.getRole()).isEqualTo(role);
            assertThat(result.isEnabled()).isTrue();

            verify(passwordEncoder).encode(rawPassword);
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw exception when username already exists")
        void createUser_DuplicateUsername_ThrowsException() {
            // Given
            String username = "existinguser";
            String rawPassword = "password123";
            String role = "USER";

            when(userRepository.existsByUsername(username)).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> userService.createUser(username, rawPassword, role))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Username already exists")
                    .hasMessageContaining(username);

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should create user with ADMIN role")
        void createUser_AdminRole_CreatesAdminUser() {
            // Given
            String username = "adminuser";
            String rawPassword = "secureAdminPass123";
            String role = "ADMIN";

            when(userRepository.existsByUsername(username)).thenReturn(false);
            when(passwordEncoder.encode(rawPassword)).thenReturn("$2a$10$encoded");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });

            // When
            User result = userService.createUser(username, rawPassword, role);

            // Then
            assertThat(result.getRole()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("Should save user with correct properties")
        void createUser_ValidInput_SavesCorrectUser() {
            // Given
            String username = "newuser";
            String rawPassword = "securePassword123";
            String encodedPassword = "$2a$10$encoded";
            String role = "OPERATOR";

            when(userRepository.existsByUsername(username)).thenReturn(false);
            when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            when(userRepository.save(userCaptor.capture())).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });

            // When
            userService.createUser(username, rawPassword, role);

            // Then
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getUsername()).isEqualTo(username);
            assertThat(savedUser.getPasswordHash()).isEqualTo(encodedPassword);
            assertThat(savedUser.getRole()).isEqualTo(role);
            assertThat(savedUser.isEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("findByUsername()")
    class FindByUsernameTests {

        @Test
        @DisplayName("Should return user when found")
        void findByUsername_UserExists_ReturnsUser() {
            // Given
            String username = "existinguser";
            User user = createUser(1L, username, "encoded", "USER", true);

            when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

            // When
            Optional<User> result = userService.findByUsername(username);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo(username);
        }

        @Test
        @DisplayName("Should return empty when user not found")
        void findByUsername_UserNotFound_ReturnsEmpty() {
            // Given
            String username = "nonexistent";

            when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

            // When
            Optional<User> result = userService.findByUsername(username);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("updatePassword()")
    class UpdatePasswordTests {

        @Test
        @DisplayName("Should update password for existing user")
        void updatePassword_UserExists_UpdatesPassword() {
            // Given
            String username = "existinguser";
            String newPassword = "newSecurePassword";
            String newEncodedPassword = "$2a$10$newEncoded";
            User user = createUser(1L, username, "oldEncoded", "USER", true);

            when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(newPassword)).thenReturn(newEncodedPassword);
            when(userRepository.save(any(User.class))).thenReturn(user);

            // When
            userService.updatePassword(username, newPassword);

            // Then
            verify(passwordEncoder).encode(newPassword);
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo(newEncodedPassword);
        }

        @Test
        @DisplayName("Should do nothing when user not found")
        void updatePassword_UserNotFound_DoesNothing() {
            // Given
            String username = "nonexistent";
            String newPassword = "newPassword";

            when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

            // When
            userService.updatePassword(username, newPassword);

            // Then
            verify(passwordEncoder, never()).encode(anyString());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("disableUser()")
    class DisableUserTests {

        @Test
        @DisplayName("Should disable existing user")
        void disableUser_UserExists_DisablesUser() {
            // Given
            String username = "activeuser";
            User user = createUser(1L, username, "encoded", "USER", true);

            when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            // When
            userService.disableUser(username);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should do nothing when user not found")
        void disableUser_UserNotFound_DoesNothing() {
            // Given
            String username = "nonexistent";

            when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

            // When
            userService.disableUser(username);

            // Then
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should disable already disabled user without error")
        void disableUser_AlreadyDisabled_SetsDisabledAgain() {
            // Given
            String username = "disableduser";
            User user = createUser(1L, username, "encoded", "USER", false);

            when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            // When
            userService.disableUser(username);

            // Then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().isEnabled()).isFalse();
        }
    }

    private User createUser(Long id, String username, String passwordHash, String role, boolean enabled) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setEnabled(enabled);
        return user;
    }
}
