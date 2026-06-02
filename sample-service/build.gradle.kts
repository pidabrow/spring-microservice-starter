plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":platform-common"))
    implementation(project(":platform-web"))
    implementation(project(":platform-data"))
    implementation(project(":platform-infrastructure"))
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.github.java-json-tools:json-patch:1.13")
    
    testImplementation(testFixtures(project(":platform-testing")))
    testImplementation("com.h2database:h2")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.awaitility:awaitility:4.2.0")
}
