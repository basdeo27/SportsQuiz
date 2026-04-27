plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.3.4"
	id("io.spring.dependency-management") version "1.1.6"
}

group = "com.elliotmoose"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

val pactVersion = "4.6.7"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("software.amazon.awssdk:dynamodb:2.25.65")
	implementation("org.springframework.security:spring-security-crypto")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
	testImplementation("au.com.dius.pact.consumer:junit5:$pactVersion")
	testImplementation("au.com.dius.pact.provider:junit5:$pactVersion")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

// Pact consumer test — generates pact files in build/pacts/.
/*val pactConsumerTest by tasks.registering(Test::class) {
	useJUnitPlatform()
	filter {
		includeTestsMatching("*PactConsumerTest")
		isFailOnNoMatchingTests = false
	}
}

// Pact provider test — reads the pact files generated above and verifies the running app.
val pactProviderTest by tasks.registering(Test::class) {
	useJUnitPlatform()
	filter {
		includeTestsMatching("*PactProviderTest")
		isFailOnNoMatchingTests = false
	}
	shouldRunAfter(pactConsumerTest)
}

// Main test task runs all non-pact tests.
// Pact tests live in their own tasks so that Gradle's --tests filter
// (which applies to every Test task in the graph) doesn't interfere
// with normal test discovery when targeting a specific class.
tasks.test {
	useJUnitPlatform()
	dependsOn(pactConsumerTest, pactProviderTest)
	filter {
		excludeTestsMatching("*Pact*")
		isFailOnNoMatchingTests = false
	}
}*/
