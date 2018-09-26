import java.io.File
import java.nio.file.Files
import java.util.*

object Settings {

    private val jarfile = File(this::class.java.protectionDomain.codeSource.location.toURI().path)
    private val configfolder: String = if (jarfile.toString().endsWith(".jar")) jarfile.parent else File(".").absoluteFile.parent
    private val configfile = File(configfolder + File.separator + "imap2gmail.txt")

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
            java.lang.System.exit(1)
        }

    }
}