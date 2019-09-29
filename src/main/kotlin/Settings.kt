import java.io.File
import java.nio.file.Files
import java.util.*
import kotlin.system.exitProcess

object Settings {

    private val configfile = File(System.getProperty("user.home") + File.separator + "imap2gmail-settings.txt")

    private val settings = LinkedHashMap<String, String>() // java.Properties stores unsorted, not nice

    fun save() {
        Files.write(configfile.toPath(), (settings.map { (k, v) -> "$k=$v" }) )
    }

    var error = false
    fun getOrSet(key: String, defaultval: String): String {
        return settings.getOrElse(key) {
            error = true
            settings[key] = defaultval
            "-1"
        }
    }

    init {
        // Load
        warn("Settings file: $configfile")
        try {
            if (!configfile.exists()) configfile.createNewFile()
            Files.readAllLines(configfile.toPath()).toSet().forEach { l ->
                if (!l.trim().startsWith("#")) {
                    val sl = l.split("=", ignoreCase = false, limit = 2) // return empty value strings!
                    if (sl.size == 2) {
                        settings[sl[0].trim()] = sl[1].trim()
                    }
                }
            }
        } catch (e: Exception) {
            warn("Error loading settings, file: ${configfile.path}: ${e.message}")
            exitProcess(1)
        }

    }
}