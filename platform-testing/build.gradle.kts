plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    testFixturesApi("org.springframework.boot:spring-boot-starter-test")
    testFixturesApi("org.testcontainers:junit-jupiter")
    testFixturesApi("org.testcontainers:postgresql")
    testFixturesApi("org.testcontainers:kafka")
    testFixturesApi(project(":platform-data"))
}
