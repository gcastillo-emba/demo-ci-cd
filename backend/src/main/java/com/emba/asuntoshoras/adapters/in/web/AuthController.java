package com.emba.asuntoshoras.adapters.in.web;

import com.emba.asuntoshoras.adapters.in.web.api.AuthApi;
import com.emba.asuntoshoras.adapters.in.web.api.model.LoginRequest;
import com.emba.asuntoshoras.adapters.in.web.api.model.LoginResponse;
import com.emba.asuntoshoras.adapters.in.web.api.model.User;
import com.emba.asuntoshoras.application.port.in.AuthenticateUseCase;
import com.emba.asuntoshoras.application.port.in.AuthenticateUseCase.AuthenticationResult;
import com.emba.asuntoshoras.application.port.in.GetCurrentUserQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implementa {@link AuthApi} (generada desde el contrato). Solo registra el userId en logs. */
@RestController
public class AuthController implements AuthApi {

  private static final Logger log = LoggerFactory.getLogger(AuthController.class);

  private final AuthenticateUseCase authenticate;
  private final GetCurrentUserQuery currentUser;

  public AuthController(AuthenticateUseCase authenticate, GetCurrentUserQuery currentUser) {
    this.authenticate = authenticate;
    this.currentUser = currentUser;
  }

  @Override
  public ResponseEntity<LoginResponse> login(LoginRequest loginRequest) {
    AuthenticationResult result =
        authenticate.authenticate(loginRequest.getEmail(), loginRequest.getPassword());
    log.info("Inicio de sesión exitoso userId={}", result.user().id());
    LoginResponse body =
        new LoginResponse()
            .accessToken(result.accessToken())
            .tokenType(LoginResponse.TokenTypeEnum.BEARER)
            .expiresIn(Math.toIntExact(result.expiresInSeconds()))
            .user(ApiMapper.toDto(result.user()));
    return ResponseEntity.ok(body);
  }

  @Override
  public ResponseEntity<User> getMe() {
    return ResponseEntity.ok(ApiMapper.toDto(currentUser.getCurrentUser(CurrentUserId.require())));
  }
}
