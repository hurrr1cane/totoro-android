// buildSrc/src/main/kotlin/LocalProperties.kt
import java.util.Properties

fun org.gradle.api.Project.localProperties(key: String, defaultValue: String? = null): String? {
    val localProps = Properties()
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { localProps.load(it) }
    }
    val sysProp = System.getenv(key) ?: System.getProperty(key)
    return sysProp ?: localProps.getProperty(key) ?: defaultValue
}
