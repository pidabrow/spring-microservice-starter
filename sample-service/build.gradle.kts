plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":platform-common"))
    implementation(project(":platform-data"))
    implementation(project(":platform-web"))
    
    testImplementation("com.h2database:h2")
}
