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
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("au.com.dius.pact.consumer:junit5:$pactVersion")
	testImplementation("au.com.dius.pact.provider:junit5:$pactVersion")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

// Consumer tests run first — they generate the pact files that the provider test reads.
// Consumer tests run as a dedicated task first, generating the pact file in build/pacts/.
// The main test task depends on this so the provider test always finds an up-to-date pact.
val pactConsumerTest by tasks.registering(Test::class) {
	useJUnitPlatform()
	filter { includeTestsMatching("*PactConsumerTest") }
}

tasks.test {
	dependsOn(pactConsumerTest)
}
