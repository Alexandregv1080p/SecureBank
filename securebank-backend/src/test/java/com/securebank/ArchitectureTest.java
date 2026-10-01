package com.securebank;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Guarda a Clean Architecture (seção 58): o domínio não conhece framework nem infraestrutura. */
@AnalyzeClasses(packages = "com.securebank", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnFrameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "org.hibernate..", "org.postgresql..",
                    "com.fasterxml..", "tools.jackson..", "..infrastructure..", "..application..");

    @ArchTest
    static final ArchRule noFloatingPointInTheDomain = fields()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
            .should().notHaveRawType(double.class)
            .andShould().notHaveRawType(float.class)
            .andShould().notHaveRawType(Double.class)
            .andShould().notHaveRawType(Float.class)
            .as("dinheiro nunca é double/float: usar Money (BigDecimal)");

    @ArchTest
    static final ArchRule modulesHaveNoCycles = slices()
            .matching("com.securebank.(*)..")
            .should().beFreeOfCycles();
}
