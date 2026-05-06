package com.shadhinpay.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.entity.Auditable;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(
    packages = "com.shadhinpay",
    importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ArchitectureRulesTest {

  private static final Set<String> FEATURES =
      Set.of(
          "adapters",
          "identity",
          "invoice",
          "ledger",
          "paymentcore",
          "provisioning",
          "quota",
          "risk",
          "settlement");

  // §22 Naming Conventions — controllers live in a controller package
  @ArchTest
  static final ArchRule controllers_must_be_in_controller_package =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .resideInAPackage("..controller..");

  // §21 Forbidden: Controller → Repository (skip the use case layer)
  @ArchTest
  static final ArchRule controllers_must_not_access_repositories_directly =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..repository..");

  // §21 Forbidden: Controller → Entity (entities never leave the domain)
  @ArchTest
  static final ArchRule controllers_must_not_use_entities =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..entity..");

  // §4 Use Cases / §20 Custom Annotations — implementations are annotated @UseCase
  @ArchTest
  static final ArchRule usecases_implementations_must_be_annotated_UseCase =
      classes()
          .that()
          .haveSimpleNameEndingWith("UseCaseImpl")
          .should()
          .beAnnotatedWith(UseCase.class);

  // §20 Custom Annotations — @UseCase replaces @Service for use case classes
  @ArchTest
  static final ArchRule usecases_must_not_be_annotated_Service =
      classes().that(implementUseCaseInterface()).should().notBeAnnotatedWith(Service.class);

  // §22 Naming Conventions — UseCase interface and impl reside in usecase package
  // (the @UseCase annotation itself in common.annotation is excluded)
  @ArchTest
  static final ArchRule usecases_must_be_in_usecase_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("UseCase")
          .or()
          .haveSimpleNameEndingWith("UseCaseImpl")
          .and()
          .resideOutsideOfPackage("com.shadhinpay.common..")
          .should()
          .resideInAPackage("..usecase..");

  // §7 Entities & Domain Model — every @Entity inherits from Auditable
  @ArchTest
  static final ArchRule entities_must_extend_Auditable =
      classes().that().areAnnotatedWith(Entity.class).should().beAssignableTo(Auditable.class);

  // §7 Entities — @Data on @Entity breaks JPA proxy equals/hashCode (detected via Lombok's
  // generated canEqual(Object) method, since lombok.Data has SOURCE retention)
  @ArchTest
  static final ArchRule entities_must_not_use_Data_annotation =
      classes().that().areAnnotatedWith(Entity.class).should(notHaveLombokDataMarkers());

  // §21 Forbidden: DTO → Entity (DTO is unaware of domain)
  @ArchTest
  static final ArchRule entities_must_not_be_referenced_by_dtos =
      noClasses()
          .that()
          .resideInAPackage("..dto..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..entity..");

  // §21 Forbidden: Entity → DTO (entity is unaware of presentation)
  @ArchTest
  static final ArchRule dtos_must_not_be_referenced_by_entities =
      noClasses()
          .that()
          .resideInAPackage("..entity..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..dto..");

  // §6 Repositories — @Repository interfaces live in repository sub-package
  @ArchTest
  static final ArchRule repositories_must_be_in_repository_package =
      classes()
          .that()
          .areAnnotatedWith(Repository.class)
          .should()
          .resideInAPackage("..repository..");

  // §3 Feature Module Anatomy / §21 — no feature reaches into another feature's internals
  @ArchTest
  static final ArchRule feature_packages_must_not_depend_on_other_feature_internals =
      classes()
          .that()
          .resideInAPackage("com.shadhinpay..")
          .and()
          .resideOutsideOfPackage("com.shadhinpay.common..")
          .should(notReachIntoOtherFeatureInternals());

  // §4 Use Cases — constructor injection only via @RequiredArgsConstructor; no field injection
  @ArchTest
  static final ArchRule no_field_injection = noFields().should().beAnnotatedWith(Autowired.class);

  // §11 Exception Hierarchy — never write to standard streams; use SLF4J
  @ArchTest static final ArchRule no_System_out_or_err = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

  // §7 Entities — @Enumerated columns must be EnumType.STRING (never ORDINAL)
  @ArchTest
  static final ArchRule enums_for_status_must_be_String_mapped =
      fields().that().areAnnotatedWith(Enumerated.class).should(useEnumTypeString());

  // ---------- helpers ----------

  private static ArchCondition<JavaClass> notHaveLombokDataMarkers() {
    return new ArchCondition<JavaClass>("not be annotated with lombok.@Data") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        boolean hasCanEqual =
            item.getMethods().stream()
                .anyMatch(
                    m ->
                        m.getName().equals("canEqual")
                            && m.getRawParameterTypes().size() == 1
                            && m.getRawParameterTypes()
                                .get(0)
                                .getName()
                                .equals("java.lang.Object"));
        boolean hasEquals =
            item.getMethods().stream()
                .anyMatch(
                    m ->
                        m.getName().equals("equals")
                            && m.getRawParameterTypes().size() == 1
                            && m.getRawParameterTypes()
                                .get(0)
                                .getName()
                                .equals("java.lang.Object"));
        if (hasCanEqual && hasEquals) {
          events.add(
              SimpleConditionEvent.violated(
                  item,
                  String.format(
                      "Entity %s appears to use @Data (Lombok-generated canEqual + equals "
                          + "found); use @Getter @Setter @NoArgsConstructor @AllArgsConstructor",
                      item.getName())));
        }
      }
    };
  }

  private static DescribedPredicate<JavaClass> implementUseCaseInterface() {
    return new DescribedPredicate<JavaClass>("implement a *UseCase interface") {
      @Override
      public boolean test(JavaClass cls) {
        return cls.getAllRawInterfaces().stream()
            .anyMatch(i -> i.getSimpleName().endsWith("UseCase"));
      }
    };
  }

  private static ArchCondition<JavaField> useEnumTypeString() {
    return new ArchCondition<JavaField>("be mapped with EnumType.STRING") {
      @Override
      public void check(JavaField field, ConditionEvents events) {
        Enumerated ann = field.reflect().getAnnotation(Enumerated.class);
        if (ann == null || ann.value() != EnumType.STRING) {
          events.add(
              SimpleConditionEvent.violated(
                  field,
                  String.format(
                      "Field %s.%s is @Enumerated but not EnumType.STRING",
                      field.getOwner().getName(), field.getName())));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> notReachIntoOtherFeatureInternals() {
    return new ArchCondition<JavaClass>(
        "not depend on another feature's entity, repository, or mapper packages") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        String thisFeature = featureOf(item.getPackageName());
        if (thisFeature == null) {
          return;
        }
        for (var dep : item.getDirectDependenciesFromSelf()) {
          String targetPkg = dep.getTargetClass().getPackageName();
          String depFeature = featureOf(targetPkg);
          if (depFeature == null || depFeature.equals(thisFeature)) {
            continue;
          }
          String prefix = "com.shadhinpay." + depFeature + ".";
          if (targetPkg.equals(prefix + "entity")
              || targetPkg.startsWith(prefix + "entity.")
              || targetPkg.equals(prefix + "repository")
              || targetPkg.startsWith(prefix + "repository.")
              || targetPkg.equals(prefix + "mapper")
              || targetPkg.startsWith(prefix + "mapper.")) {
            events.add(
                SimpleConditionEvent.violated(
                    item,
                    String.format(
                        "Feature '%s' class %s depends on internals of feature '%s' (%s)",
                        thisFeature, item.getName(), depFeature, dep.getTargetClass().getName())));
          }
        }
      }
    };
  }

  private static String featureOf(String packageName) {
    if (packageName == null || !packageName.startsWith("com.shadhinpay.")) {
      return null;
    }
    String tail = packageName.substring("com.shadhinpay.".length());
    int dot = tail.indexOf('.');
    String first = dot < 0 ? tail : tail.substring(0, dot);
    return FEATURES.contains(first) ? first : null;
  }
}
