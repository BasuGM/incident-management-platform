package com.example.incidentmanagement.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.incidentmanagement.common.exception.DuplicateEmailException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, new BCryptPasswordEncoder());
    }

    @Test
    void registerNormalizesEmailAndHashesPassword() {
        when(userRepository.existsByEmailIgnoreCase("john@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.register(
                "  John@Example.com ", "Password1", "John", "Smith", UserRole.ENGINEER);

        assertThat(user.getEmail()).isEqualTo("john@example.com");
        assertThat(user.getRole()).isEqualTo(UserRole.ENGINEER);
        assertThat(user.getPasswordHash()).isNotEqualTo("Password1");
        verify(userRepository).save(user);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("john@example.com")).thenReturn(true);
        assertThatThrownBy(() -> userService.register(
                        "john@example.com", "Password1", "John", "Smith", UserRole.ENGINEER))
                .isInstanceOf(DuplicateEmailException.class);
    }
}
