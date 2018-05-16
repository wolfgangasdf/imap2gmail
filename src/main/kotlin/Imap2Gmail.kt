import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import javax.mail.Flags
import kotlin.concurrent.thread

fun getTimestamp(): String {
    return SimpleDateFormat("yyyyMMdd-H:mm:ss").format(Date())
}

object Imap2Gmail {
    val imapprotocol = Settings.getOrSet("imapprotocol", "imap or imaps")
    val imapserver = Settings.getOrSet("imapserver", "imapserver or IP that should be checked")
    val imapport = Settings.getOrSet("imapport", "port or -1 for default").toInt()
    val imapuser = Settings.getOrSet("imapuser", "imap user name")
    val imappass = Settings.getOrSet("imappass", "imap password")
    val imapfolder = Settings.getOrSet("imapfolder", "imapfolder to check like INBOX")
    val imapfoldermoved = Settings.getOrSet("imapfoldermoved", "imapfolder to put in moved emails")
    val gmailclientid = Settings.getOrSet("gmailclientid", "imapfolder to check like INBOX")
    val gmailclientsecret = Settings.getOrSet("gmailclientsecret", "imapfolder to check like INBOX")
    val mailfrom = Settings.getOrSet("mailfrom", "notification email From:")
    val mailto = Settings.getOrSet("mailto", "notification recipient email")
    val mailsmtpprot = Settings.getOrSet("mailsmtpprot", "smtp or smtps")
    val mailsmtpserver = Settings.getOrSet("mailsmtpserver", "smtpserver such as localhost")
    val mailsmtpport = Settings.getOrSet("mailsmtpport", "smtp port or -1 for default").toInt()
    val mailsmtpuser = Settings.getOrSet("mailsmtpuser", "smtp username or emtpy")
    val mailsmtppass = Settings.getOrSet("mailsmtppass", "smtp password or emtpy")

    private const val IDLEMS = 10*60*1000 // ms after which idle is refreshed

    fun doit() { // NEVER put this in init, must return from init (can't access variables from launched thread there!)
        if (Settings.error) {
            Settings.save()
            println("Check settings file and restart, see README.md!")
            System.exit(1)
        }

        val lastIdle = AtomicLong(System.currentTimeMillis())
        var lastIdleTimeoutEmail = 0L
        // start watchThread which checks for periodic IDLE
        thread(start = true, name = "watchThread", isDaemon = true) {
            while (true) {
                val currms = System.currentTimeMillis()
                if (currms - lastIdle.get() > 1.5*IDLEMS && currms - lastIdleTimeoutEmail > 60 * 60 * 1000) {
                    println("watchThread: idle too long ago! ${currms - lastIdle.get()} > ${1.5*IDLEMS}")
                    ImapMailer.sendMail("Idle timeout!", "Didn't IDLE for ${(currms - lastIdle.get())/(1000*60)} minutes.\n" +
                            "Check that imap2gmail is working properly, possibly the IMAP server is temporarily down.")
                    lastIdleTimeoutEmail = currms
                }
                Thread.sleep(1000)
            }
        }

        var firstrun = true
        while (true) {
            println("--- within big loop (${Thread.currentThread().id}), connecting...")
            try {
                GmailStuff.initialize()
                ImapStuff.initialize()

                if (firstrun) {
                    ImapMailer.sendMail("started!", "")
                    firstrun = false
                }

                // loop IDLE commands. loop is interrupted by exception.
                while (true) {
                    // first check for messages
                    while (ImapStuff.folder.messageCount > 0) {
                        val msgs = ImapStuff.folder.messages
                        println("Have messages: " + msgs.size)
                        msgs.forEach { m ->
                            println(" Handling message: ${m.messageNumber}: ${m.subject}")
                            try {
                                //testing ImapStuff.store.close()
                                GmailStuff.importMessage(m)
                                // if successful, move to some folder!
                                ImapStuff.folder.copyMessages(arrayOf(m), ImapStuff.foldermoveto)
                                m.setFlag(Flags.Flag.DELETED, true)
                                ImapStuff.folder.expunge()
                                println("successfully imported into gmail and moved to folder $imapfoldermoved!")
                            } catch (e: Exception) {
                                println("exception importmessage!!!! " + e.message)
                                e.printStackTrace()
                                throw(e)
                            }
                        }
                    }
                    // start watchThreadIdle which stops idle after 10mins
                    println("before thread " + getTimestamp())
                    val watchThreadIdle = thread(start = true, name = "watchThreadIdle") {
                        println("watchThreadIdle(${Thread.currentThread().id}): started ")
                        val t0 = System.currentTimeMillis()
                        // wait
                        var doit = true // needed because Thread.interrupted is cleared if exception is caught...???
                        while (doit && System.currentTimeMillis() - t0 < IDLEMS && !Thread.interrupted()) { // 10 mins
                            try { Thread.sleep(100) } catch (e: InterruptedException) {
                                println("watchThreadIdle(${Thread.currentThread().id}): interrupted!")
                                doit = false
                            }
                        }
                        println("watchThreadIdle(${Thread.currentThread().id}): after wait, interr=" + Thread.interrupted())
                        if (doit && !Thread.interrupted()) { // if not killed, do some imap command to interrupt idle!
                            try {
                                ImapStuff.folder.messageCount // any command interrupts IDLE
                            } catch (e: Exception) {
                                println("watchThreadIdle(${Thread.currentThread().id}): exception " + e.message)
                            }
                        }
                        println("watchThreadIdle(${Thread.currentThread().id}): end")
                    }
                    // now wait with idle
                    println("before idle " + getTimestamp())
                    lastIdle.set(System.currentTimeMillis())
                    ImapStuff.folder.idle(true)
                    // kill watchThreadIdle
                    watchThreadIdle.interrupt()
                    watchThreadIdle.join()
                    println("after idle  " + getTimestamp())
                }
            } catch (ex: Exception) {
                println("Main loop got exception!")
                val wh = ex.stackTrace.find { ste -> ste.className == this.javaClass.name }?.let { ste -> "${ste.className}:${ste.lineNumber}" }
                println("${getTimestamp()} got exception (ok if disconnected!): ${ex.message} in $wh")
                if (firstrun) {
                    ImapMailer.sendMail("error in startup","${ex.message}\n${ex.stackTrace.joinToString("\n") {ste -> ste.toString()}}")
                    System.exit(1)
                }
                Thread.sleep(10000)
            }
        }
    }
}
