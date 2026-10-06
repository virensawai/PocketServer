plugins {
    id("java-library")
    id("application")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("com.example.pocketserver.relay.NettyGatewayServer")
}

dependencies {
    implementation(libs.netty.all)
    implementation(libs.gson)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
}
