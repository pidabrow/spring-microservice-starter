plugins {
    `java-library`
}

dependencies {
    api(project(":platform-common"))
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")
}
