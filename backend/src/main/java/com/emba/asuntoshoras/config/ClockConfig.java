package com.emba.asuntoshoras.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Único punto donde se lee el reloj del sistema; el resto del código recibe un {@link Clock}. */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

  @Bean
  Clock clock(AppProperties properties) {
    return Clock.system(ZoneId.of(properties.timeZone()));
  }
}
