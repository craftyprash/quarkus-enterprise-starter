package com.starter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.starter.common.domain.BaseEntity;
import com.starter.common.domain.BaseUuidEntity;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Entity;
import jakarta.ws.rs.Path;
import java.util.Set;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@AnalyzeClasses(packages = "com.starter")
public class ArchitectureTest {

    private static final String BASE_PKG = "com.starter";
    private static final Set<String> INFRA_MODULES = Set.of("common");

    // ── Layer rules (within a module) ──

    @ArchTest
    static final ArchRule api_should_not_access_domain =
            noClasses()
                    .that()
                    .resideInAPackage("..api..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..domain..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_api =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..api..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_internal =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..internal..");

    // ── Cross-module isolation ──

    @ArchTest
    static final ArchRule no_cross_module_internal_access =
            noClasses()
                    .that()
                    .resideInAPackage("..internal..")
                    .should(dependOnForeignPackage("internal"));

    @ArchTest
    static final ArchRule no_cross_module_domain_access =
            noClasses()
                    .that()
                    .resideInAPackage("..internal..")
                    .should(dependOnForeignPackage("domain"));

    @ArchTest
    static final ArchRule no_cross_module_domain_to_domain_access =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should(dependOnForeignPackage("domain"));

    @ArchTest
    static final ArchRule no_cross_module_api_access =
            noClasses()
                    .that()
                    .resideInAPackage("..internal..")
                    .should(dependOnForeignPackage("api"));

    @ArchTest
    static final ArchRule cross_module_deps_must_be_interfaces =
            noClasses()
                    .that()
                    .resideInAPackage("..internal..")
                    .should(dependOnForeignModuleConcreteClasses());

    // ── Module contract rules ──

    @ArchTest
    static final ArchRule module_root_classes_must_be_interfaces =
            classes()
                    .that(onlyInModuleRoot())
                    .should()
                    .beInterfaces()
                    .as("Classes at module root (e.g. com.starter.applicant) must be interfaces");

    // ── HTTP isolation ──

    @ArchTest
    static final ArchRule internal_should_not_depend_on_jaxrs =
            noClasses()
                    .that()
                    .resideInAPackage("..internal..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("jakarta.ws.rs..")
                    .as(
                            "Services must not depend on jakarta.ws.rs — throw JDK or custom exceptions");

    // ── Naming conventions ──

    @ArchTest
    static final ArchRule requests_end_with_Req =
            classes()
                    .that()
                    .resideInAPackage("..api.request..")
                    .should()
                    .haveSimpleNameEndingWith("Req");

    @ArchTest
    static final ArchRule responses_end_with_Res =
            classes()
                    .that()
                    .resideInAPackage("..api.response..")
                    .should()
                    .haveSimpleNameEndingWith("Res");

    @ArchTest
    static final ArchRule path_only_in_api =
            classes()
                    .that()
                    .areAnnotatedWith(Path.class)
                    .and()
                    .areNotAnnotatedWith(RegisterRestClient.class)
                    .should()
                    .resideInAPackage("..api..");

    @ArchTest
    static final ArchRule resources_end_with_Resource =
            classes()
                    .that()
                    .areAnnotatedWith(Path.class)
                    .and()
                    .areNotAnnotatedWith(RegisterRestClient.class)
                    .should()
                    .haveSimpleNameEndingWith("Resource");

    @ArchTest
    static final ArchRule repos_end_with_Repo =
            classes()
                    .that()
                    .implement(PanacheRepository.class)
                    .should()
                    .haveSimpleNameEndingWith("Repo");

    @ArchTest
    static final ArchRule repos_are_application_scoped =
            classes()
                    .that()
                    .implement(PanacheRepository.class)
                    .should()
                    .beAnnotatedWith(ApplicationScoped.class);

    @ArchTest
    static final ArchRule entities_in_domain =
            classes().that().areAnnotatedWith(Entity.class).should().resideInAPackage("..domain..");

    @ArchTest
    static final ArchRule entities_extend_base =
            classes()
                    .that()
                    .areAnnotatedWith(Entity.class)
                    .should()
                    .beAssignableTo(BaseEntity.class)
                    .orShould()
                    .beAssignableTo(BaseUuidEntity.class);

    // ── Helpers ──

    private static String moduleOf(JavaClass clazz) {
        var pkg = clazz.getPackageName();
        if (!pkg.startsWith(BASE_PKG + ".")) return "";
        var afterBase = pkg.substring(BASE_PKG.length() + 1);
        var dot = afterBase.indexOf('.');
        return dot > 0 ? afterBase.substring(0, dot) : afterBase;
    }

    private static boolean isModuleSubpackage(String pkg, String subpackage) {
        return pkg.contains("." + subpackage + ".") || pkg.endsWith("." + subpackage);
    }

    private static ArchCondition<JavaClass> dependOnForeignPackage(String subpackage) {
        return new ArchCondition<>("not depend on another module's " + subpackage + " package") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                var myModule = moduleOf(clazz);
                clazz.getDirectDependenciesFromSelf().stream()
                        .filter(
                                d ->
                                        isModuleSubpackage(
                                                d.getTargetClass().getPackageName(), subpackage))
                        .filter(d -> !d.getTargetClass().getPackageName().contains("common."))
                        .filter(d -> !moduleOf(d.getTargetClass()).equals(myModule))
                        .forEach(
                                d ->
                                        events.add(
                                                SimpleConditionEvent.violated(
                                                        clazz,
                                                        clazz.getName()
                                                                + " depends on "
                                                                + d.getTargetClass().getName()
                                                                + " (cross-module "
                                                                + subpackage
                                                                + ")")));
            }
        };
    }

    private static ArchCondition<JavaClass> dependOnForeignModuleConcreteClasses() {
        return new ArchCondition<>(
                "not depend on concrete classes from another module's root package") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                var myModule = moduleOf(clazz);
                clazz.getDirectDependenciesFromSelf().stream()
                        .filter(d -> d.getTargetClass().getPackageName().startsWith(BASE_PKG + "."))
                        .filter(d -> !d.getTargetClass().getPackageName().contains("common."))
                        .filter(d -> !moduleOf(d.getTargetClass()).equals(myModule))
                        .filter(d -> isAtModuleRoot(d.getTargetClass()))
                        .filter(d -> !d.getTargetClass().isInterface())
                        .filter(d -> !d.getTargetClass().getSimpleName().contains("$"))
                        .forEach(
                                d ->
                                        events.add(
                                                SimpleConditionEvent.violated(
                                                        clazz,
                                                        clazz.getName()
                                                                + " depends on concrete class "
                                                                + d.getTargetClass().getName()
                                                                + " — use the module interface"
                                                                + " instead")));
            }
        };
    }

    private static boolean isAtModuleRoot(JavaClass clazz) {
        var pkg = clazz.getPackageName();
        if (!pkg.startsWith(BASE_PKG + ".")) return false;
        var afterBase = pkg.substring(BASE_PKG.length() + 1);
        return !afterBase.contains(".");
    }

    private static com.tngtech.archunit.base.DescribedPredicate<JavaClass> onlyInModuleRoot() {
        return com.tngtech.archunit.base.DescribedPredicate.describe(
                "top-level class in module root package",
                (JavaClass c) -> {
                    if (c.getSimpleName().contains("$")) return false;
                    if (c.getName().contains("$")) return false;
                    if (c.getSimpleName().endsWith("Test")) return false;
                    var pkg = c.getPackageName();
                    if (!pkg.startsWith(BASE_PKG + ".")) return false;
                    var afterBase = pkg.substring(BASE_PKG.length() + 1);
                    if (afterBase.contains(".")) return false;
                    return !INFRA_MODULES.contains(afterBase);
                });
    }
}
