package pay.conflux.backend.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import pay.conflux.backend.ConfluxPayApplication;

class ModularityTests {

  private final ApplicationModules modules = ApplicationModules.of(ConfluxPayApplication.class);

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
