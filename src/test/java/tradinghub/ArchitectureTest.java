package tradinghub;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.PropertyResolver;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/** docs/ARCHITECTURE.md의 "어기면 안 되는 경계". */
@AnalyzeClasses(packages = "tradinghub", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule 층은_web_service_brokers_core_순서로만_부른다 = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("web").definedBy("tradinghub.web..")
            .layer("service").definedBy("tradinghub.service..")
            .layer("brokers").definedBy("tradinghub.brokers..")
            .layer("core").definedBy("tradinghub.core..")
            .whereLayer("web").mayNotBeAccessedByAnyLayer()
            .whereLayer("service").mayOnlyBeAccessedByLayers("web")
            .whereLayer("brokers").mayOnlyBeAccessedByLayers("service") // web은 brokers를 직접 부르지 않는다
            .whereLayer("core").mayOnlyBeAccessedByLayers("web", "service", "brokers");

    @ArchTest
    static final ArchRule 본체는_증권사_어댑터를_모른다 = noClasses()
            .that().resideInAnyPackage("tradinghub.core..", "tradinghub.service..", "tradinghub.web..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "tradinghub.brokers.toss..", "tradinghub.brokers.kiwoom..", "tradinghub.brokers.fake..");

    @ArchTest
    static final ArchRule 어디에도_주문_메서드가_없다 = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("tradinghub..") // Broker 밖의 도우미 클래스도 막는다
            .should().haveNameMatching("(?i).*(order|buy|sell|cancel|amend|modify|매수|매도|주문|정정|취소).*")
            .because("1차는 조회 전용 (decisions/005)");

    @ArchTest
    static final ArchRule 비밀값은_환경변수나_설정값으로_읽지_않는다 = noClasses()
            .should().callMethod(System.class, "getenv", String.class)
            .orShould().callMethod(System.class, "getenv")
            .orShould().callMethod(System.class, "getProperty", String.class)
            .orShould().callMethod(System.class, "getProperty", String.class, String.class)
            .orShould().dependOnClassesThat().areAssignableTo(PropertyResolver.class) // Environment 포함
            .orShould().dependOnClassesThat().areAssignableTo(Value.class)
            .orShould().dependOnClassesThat().areAssignableTo(ConfigurationProperties.class)
            .because("비밀값은 SecretStore로만 읽는다. 설정값이 꼭 필요해지면 이 규칙을 좁혀서 연다");
}
