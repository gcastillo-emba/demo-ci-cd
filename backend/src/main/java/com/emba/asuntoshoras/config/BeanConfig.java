package com.emba.asuntoshoras.config;

import com.emba.asuntoshoras.adapters.out.security.BCryptPasswordHasher;
import com.emba.asuntoshoras.adapters.out.security.JwtTokenIssuer;
import com.emba.asuntoshoras.application.port.in.AuthenticateUseCase;
import com.emba.asuntoshoras.application.port.in.ClientUseCases;
import com.emba.asuntoshoras.application.port.in.GetCurrentUserQuery;
import com.emba.asuntoshoras.application.port.in.GetDashboardSummary;
import com.emba.asuntoshoras.application.port.in.GetFeaturesQuery;
import com.emba.asuntoshoras.application.port.in.MatterUseCases;
import com.emba.asuntoshoras.application.port.in.TimeEntryUseCases;
import com.emba.asuntoshoras.application.port.out.ClientRepository;
import com.emba.asuntoshoras.application.port.out.DailyHoursLock;
import com.emba.asuntoshoras.application.port.out.DashboardReadRepository;
import com.emba.asuntoshoras.application.port.out.FeatureFlags;
import com.emba.asuntoshoras.application.port.out.MatterCodeSequence;
import com.emba.asuntoshoras.application.port.out.MatterRepository;
import com.emba.asuntoshoras.application.port.out.PasswordHasher;
import com.emba.asuntoshoras.application.port.out.TimeEntryRepository;
import com.emba.asuntoshoras.application.port.out.TokenIssuer;
import com.emba.asuntoshoras.application.port.out.UserRepository;
import com.emba.asuntoshoras.application.service.AuthenticationService;
import com.emba.asuntoshoras.application.service.ClientService;
import com.emba.asuntoshoras.application.service.CurrentUserService;
import com.emba.asuntoshoras.application.service.DashboardQueryService;
import com.emba.asuntoshoras.application.service.FeaturesService;
import com.emba.asuntoshoras.application.service.MatterService;
import com.emba.asuntoshoras.application.service.TimeEntryService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/** Wiring de casos de uso y adaptadores de salida (los servicios no llevan estereotipos). */
@Configuration(proxyBeanMethods = false)
public class BeanConfig {

  @Bean
  PasswordHasher passwordHasher() {
    return new BCryptPasswordHasher();
  }

  @Bean
  TokenIssuer tokenIssuer(JwtEncoder jwtEncoder, Clock clock, AppProperties properties) {
    return new JwtTokenIssuer(jwtEncoder, clock, properties.jwt().ttl());
  }

  @Bean
  FeatureFlags featureFlags(AppProperties properties) {
    return () -> properties.features().descriptionAssistant();
  }

  @Bean
  AuthenticateUseCase authenticateUseCase(
      UserRepository users, PasswordHasher passwordHasher, TokenIssuer tokenIssuer) {
    return new AuthenticationService(users, passwordHasher, tokenIssuer);
  }

  @Bean
  GetCurrentUserQuery getCurrentUserQuery(UserRepository users) {
    return new CurrentUserService(users);
  }

  @Bean
  GetFeaturesQuery getFeaturesQuery(FeatureFlags featureFlags) {
    return new FeaturesService(featureFlags);
  }

  @Bean
  ClientUseCases clientUseCases(ClientRepository clients, Clock clock) {
    return new ClientService(clients, clock);
  }

  @Bean
  MatterUseCases matterUseCases(
      MatterRepository matters,
      ClientRepository clients,
      MatterCodeSequence codeSequence,
      Clock clock) {
    return new MatterService(matters, clients, codeSequence, clock);
  }

  @Bean
  TimeEntryUseCases timeEntryUseCases(
      TimeEntryRepository entries,
      MatterRepository matters,
      DailyHoursLock dailyHoursLock,
      Clock clock) {
    return new TimeEntryService(entries, matters, dailyHoursLock, clock);
  }

  @Bean
  GetDashboardSummary getDashboardSummary(
      DashboardReadRepository dashboard, UserRepository users, Clock clock) {
    return new DashboardQueryService(dashboard, users, clock);
  }
}
