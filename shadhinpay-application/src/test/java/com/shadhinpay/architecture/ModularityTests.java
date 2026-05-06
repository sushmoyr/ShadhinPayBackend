package com.shadhinpay.architecture;

import com.shadhinpay.ShadhinPayApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

  private final ApplicationModules modules = ApplicationModules.of(ShadhinPayApplication.class);

  @Test
  void verifyModules() {
    System.out.println(modules);
    modules.verify();
  }

  @Test
  void documentModules() {
    new Documenter(modules).writeDocumentation();
  }
}
