plugins {
    `java-library`
}

dependencies {
    api(project(":platform-common"))
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-validation")
}
