package com.projeto.mapi.architecture;

import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

/**
 * Regras do ADR 0002 (camadas {@code controller -> service -> repository}, {@code model} = entidades JPA).
 * Cada regra é congelada com {@code FreezingArchRule}: as violações que já existiam ficam registradas em
 * {@code src/test/resources/archunit_store} e só violações NOVAS reprovam o build. Ao corrigir uma violação
 * antiga, o registro encolhe sozinho (ver {@code archunit.properties}).
 */
@AnalyzeClasses(packages = "com.projeto.mapi", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersNaoDependemDeRepository = freeze(
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..repository.."));

    /** Inclui tipos genéricos: {@code ResponseEntity<User>} e {@code List<FloodPoint>} também contam. */
    @ArchTest
    static final ArchRule controllersNaoRetornamEntidade = freeze(
            methods().that().areDeclaredInClassesThat().resideInAPackage("..controller..")
                    .and().arePublic()
                    .should(naoRetornarTipoDoPacote("..model..")));

    @ArchTest
    static final ArchRule modelNaoDependeDeControllerNemDto = freeze(
            noClasses().that().resideInAPackage("..model..")
                    .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..dto.."));

    @ArchTest
    static final ArchRule servicesNaoDependemDeController = freeze(
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..controller.."));

    /** DTO é contrato da API; depender de entidade acopla o contrato ao esquema JPA (achado da Fase 1). */
    @ArchTest
    static final ArchRule dtoNaoDependeDeModel = freeze(
            noClasses().that().resideInAPackage("..dto..")
                    .should().dependOnClassesThat().resideInAPackage("..model.."));

    /** Métodos públicos de service não devem expor entidade (achado da Fase 1: SensorService expõe FloodPoint). */
    @ArchTest
    static final ArchRule servicesNaoExpoemEntidade = freeze(
            methods().that().areDeclaredInClassesThat().resideInAPackage("..service..")
                    .and().arePublic()
                    .should(naoRetornarTipoDoPacote("..model..")));

    private static ArchCondition<JavaMethod> naoRetornarTipoDoPacote(String pacote) {
        return new ArchCondition<>("não retornar tipo de " + pacote) {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaClass tipo : method.getReturnType().getAllInvolvedRawTypes()) {
                    if (PackageMatcher.of(pacote).matches(tipo.getPackageName())) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " retorna " + tipo.getName()));
                    }
                }
            }
        };
    }
}
