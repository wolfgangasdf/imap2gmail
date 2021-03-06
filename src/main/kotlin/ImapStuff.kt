import Imap2Gmail.IDLEMS
import Imap2Gmail.imapfolder
import Imap2Gmail.imapfoldermoved
import Imap2Gmail.imapfolderquarantine
import Imap2Gmail.imappass
import Imap2Gmail.imapport
import Imap2Gmail.imapprotocol
import Imap2Gmail.imapserver
import Imap2Gmail.imapuser
import Imap2Gmail.mailfrom
import Imap2Gmail.mailsmtppass
import Imap2Gmail.mailsmtpport
import Imap2Gmail.mailsmtpprot
import Imap2Gmail.mailsmtpserver
import Imap2Gmail.mailsmtpuser
import Imap2Gmail.mailto
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.MimeMessage

object ImapMailer {
    fun sendMail(subject: String, content: String) {
        warn("sending mail: $subject ...")
        val props = System.getProperties()
        val session = Session.getInstance(props, null)
        val email = MimeMessage(session)
        email.setFrom(mailfrom)
        email.setRecipients(Message.RecipientType.TO, mailto)
        email.subject = "[Imap2Gmail] $subject"
        email.setContent(content, "text/plain")
        email.saveChanges()
        val transp = session.getTransport(mailsmtpprot)
        transp.connect(mailsmtpserver, mailsmtpport, mailsmtpuser, mailsmtppass)
        transp.sendMessage(email, email.allRecipients)
        // could use Transport.send(email) ; doesn't need savechanges and transp!
        transp.close()
        warn("sent mail!")
    }
}

object ImapStuff {

    lateinit var folder: IMAPFolder
    lateinit var foldermoveto: IMAPFolder
    lateinit var folderquarantine: IMAPFolder
    private lateinit var store: IMAPStore
    private lateinit var session: Session

    fun initialize() {
        warn("initialize imap...")
        val props = System.getProperties()
        props.setProperty("mail.$imapprotocol.timeout", (1.5*IDLEMS).toInt().toString()) // Socket I/O timeout value in milliseconds. Default is infinite timeout.
        props.setProperty("mail.$imapprotocol.connectiontimeout", (1.5*IDLEMS).toInt().toString()) // Socket connection timeout value in milliseconds. Default is infinite timeout.
        props.setProperty("mail.$imapprotocol.writetimeout", "5000") // Socket write timeout value in milliseconds, creates watch thread.
        props.setProperty("mail.$imapprotocol.partialfetch", "false") // otherwise, very large emails come in very slowly and it times out!

        session = Session.getInstance(props, null)
        session.debug = false // VERY HELPFUL!
        store = session.getStore(imapprotocol) as IMAPStore
        store.connect(imapserver, imapport, imapuser, imappass)

        fun openOrCreateFolder(name: String, mode: Int): IMAPFolder {
            val folder = store.getFolder(name)
            if (folder == null || !folder.exists()) {
                warn("error: invalid folder: $name, creating it...")
                val res = folder.create(Folder.HOLDS_MESSAGES or Folder.READ_WRITE)
                if (res) warn("Folder created!") else throw Exception("Can't create imap folder $name")
            }
            folder.open(mode)
            return folder as IMAPFolder
        }
        folder = openOrCreateFolder(imapfolder, Folder.READ_WRITE)
        foldermoveto = openOrCreateFolder(imapfoldermoved, Folder.READ_WRITE)
        folderquarantine = openOrCreateFolder(imapfolderquarantine, Folder.READ_WRITE)
        //folder.setSubscribed(true) // should not be needed...
        warn("imap initialized!")
    }


}