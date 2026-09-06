package ba.sum.kviz.service;

import ba.sum.kviz.dto.AuthResponse;
import ba.sum.kviz.dto.LoginRequest;
import ba.sum.kviz.dto.RegisterRequest;
import ba.sum.kviz.model.User;
import ba.sum.kviz.repository.UserRepository;
import ba.sum.kviz.security.JwtService;
import ba.sum.kviz.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Korisničko ime je već zauzeto");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email je već registriran");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());

        userRepository.save(user);

        UserPrincipal principal = new UserPrincipal(user);
        return new AuthResponse(
                jwtService.generateToken(principal),
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(), request.password()));

        User user = userRepository.findByUsername(request.username())
                .orElseThrow();

        UserPrincipal principal = new UserPrincipal(user);
        return new AuthResponse(
                jwtService.generateToken(principal),
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );
    }
}