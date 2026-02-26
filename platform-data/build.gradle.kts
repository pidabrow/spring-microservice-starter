plugins {
    `java-library`
}

dependencies {
    api(project(":platform-common"))
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-aop")
    runtimeOnly("org.postgresql:postgresql")
}
