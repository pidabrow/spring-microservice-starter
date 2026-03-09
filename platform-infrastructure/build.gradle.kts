plugins {
    `java-library`
}

dependencies {
    api(project(":platform-common"))
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.kafka:spring-kafka")
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.10.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.10.0")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    runtimeOnly("org.postgresql:postgresql")
}

