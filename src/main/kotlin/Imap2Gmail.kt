import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import javax.mail.Flags
import kotlin.concurrent.thread

fun getTimestamp(): String = SimpleDateFormat("yyyyMMdd-HH:mm:ss").format(Date())

fun warn(s: String) { println("[${getTimestamp()}][${Thread.currentThread().id}] " + s) }
fun debug(s: String) { warn(s) }

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

    private const val IDLEMS = 5*60*1000L // ms after which the IMAP idle command is interrupted/refreshed.

    fun doit() { // NEVER put this in init, must return from init (can't access variables from launched thread there!)
        if (Settings.error) {
            Settings.save()
            warn("Check settings file and restart, see README.md!")
            System.exit(1)
        }

        var mainThread: Thread? = null
        var watchThreadIdle: Thread? = null
        val lastIdle = AtomicLong(System.currentTimeMillis())
        var lastIdleTimeoutEmail = 0L
        // start watchThread which checks for periodic IDLE
        thread(start = true, name = "watchThread", isDaemon = true) {
            while (true) {
                val currms = System.currentTimeMillis()
                if (currms - lastIdle.get() > 1.5*IDLEMS && currms - lastIdleTimeoutEmail > 60 * 60 * 1000) {
                    warn("watchThread: idle too long ago! ${currms - lastIdle.get()} > ${1.5*IDLEMS}")
                    ImapMailer.sendMail("Idle timeout!", "Didn't IDLE for ${(currms - lastIdle.get())/(1000*60)} minutes.\n" +
                            "Check that imap2gmail is working properly, possibly the IMAP server is temporarily down.")
                    lastIdleTimeoutEmail = currms

                    if (watchThreadIdle?.isAlive == true) {
                        debug("interrupting watchThreadIdle[${watchThreadIdle?.id}]!")
                        watchThreadIdle?.interrupt()
                    }

                    if (mainThread?.isAlive == true) {
                        debug("interrupting mainThread[${mainThread?.id}]!")
                        mainThread?.interrupt() // TODO can't interrupt idle() command...
                    }

                }
                Thread.sleep(1000)
            }
        }

        var firstrun = true
        while (true) {
            mainThread = thread(start = true, name = "mainThread") {
                warn("mainThread start, connecting...")
                try {
                    GmailStuff.initialize()
                    ImapStuff.initialize()

                    if (firstrun) {
                        ImapMailer.sendMail("started!", "")
                        firstrun = false
                    }

                    // loop IDLE commands. loop is interrupted by exception.
                    while (true) {
                        warn("- within idle loop, checking for messages...")
                        // first check for messages
                        while (ImapStuff.folder.messageCount > 0) {
                            val msgs = ImapStuff.folder.messages
                            debug("Have messages: " + msgs.size)
                            msgs.forEach { m ->
                                debug(" Handling message: ${m.messageNumber}: ${m.subject}")
                                try {
                                    //testing ImapStuff.store.close()
                                    GmailStuff.importMessage(m)
                                    // if successful, move to some folder!
                                    ImapStuff.folder.copyMessages(arrayOf(m), ImapStuff.foldermoveto)
                                    m.setFlag(Flags.Flag.DELETED, true)
                                    ImapStuff.folder.expunge()
                                    debug("successfully imported into gmail and moved to folder $imapfoldermoved!")
                                } catch (e: Exception) {
                                    warn("exception importmessage!!!! " + e.message)
                                    e.printStackTrace()
                                    throw(e)
                                }
                            }
                        }
                        // start watchThreadIdle which stops idle after IDLEMS by requesting message count
                        watchThreadIdle = thread(start = true, name = "watchThreadIdle") {
                            debug("watchThreadIdle: started ")
                            try {
                                Thread.sleep(IDLEMS)
                                debug("watchThreadIdle: issue messagecount...")
                                ImapStuff.folder.messageCount // any command interrupts IDLE
                            } catch (e: InterruptedException) {
                                debug("watchThreadIdle: interrupted!")
                            } catch (e: Exception) {
                                debug("watchThreadIdle: swallowed " + e.message)
                            }
                            debug("watchThreadIdle: end")
                        }
                        // now wait with idle
                        debug("before idle")
                        lastIdle.set(System.currentTimeMillis())
                        ImapStuff.folder.idle(true)
                        // kill watchThreadIdle
                        watchThreadIdle?.interrupt()
                        debug("after idle")
                    }
                } catch (ex: Exception) {
                    warn("Main loop got exception!")
                    if (watchThreadIdle?.isAlive == true) {
                        debug("interrupting watchThreadIdle[${watchThreadIdle?.id}]!")
                        watchThreadIdle?.interrupt()
                    }
                    val wh = ex.stackTrace.find { ste -> ste.className == this.javaClass.name }?.let { ste -> "${ste.className}:${ste.lineNumber}" }
                    warn("got exception: ${ex.message} in $wh")
                    if (firstrun) {
                        ImapMailer.sendMail("error in startup","${ex.message}\n${ex.stackTrace.joinToString("\n") {ste -> ste.toString()}}")
                        System.exit(1)
                    }
                    Thread.sleep(60000) // run every minute max.
                }
                warn("mainThread: end")
            }
            mainThread.join()
        }
    }
}
