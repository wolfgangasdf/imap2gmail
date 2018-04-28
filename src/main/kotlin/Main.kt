import java.net.URLClassLoader


fun main(args: Array<String>) {

    for (url in (ClassLoader.getSystemClassLoader() as URLClassLoader).urLs) {
        println("classpath: " + url.file)
    }

    Imap2Gmail.doit()
}
