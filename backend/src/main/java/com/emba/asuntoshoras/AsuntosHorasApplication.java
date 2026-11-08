package com.emba.asuntoshoras;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Punto de entrada del backend Asuntos &amp; Horas. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AsuntosHorasApplication {

  public static void main(String[] args) {
    SpringApplication.run(AsuntosHorasApplication.class, args);
  }
}
