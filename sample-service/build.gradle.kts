plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":platform-common"))
    implementation(project(":platform-web"))
    implementation(project(":platform-data"))
    implementation(project(":platform-infrastructure"))
    implementation("org.flywaydb:flyway-core")
    implementation("com.github.java-json-tools:json-patch:1.13")
    
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("com.h2database:h2")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.awaitility:awaitility:4.2.0")
}
