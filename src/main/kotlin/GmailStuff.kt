import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AbstractPromptReceiver
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.AbstractInputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.ModifyMessageRequest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.mail.Message
import kotlin.math.roundToInt
import com.google.api.services.gmail.model.Message as GmailAPIMessage


// based on https://developers.google.com/gmail/api/quickstart/java

object GmailStuff {
    private const val APPLICATION_NAME = "imap2gmail"
    private val JSON_FACTORY = JacksonFactory.getDefaultInstance()
    private const val CREDENTIALS_FOLDER = "credentials" // Directory to store user credentials.

    // If modifying these scopes, delete your previously saved credentials/ folder.
    private val SCOPES = listOf(GmailScopes.GMAIL_LABELS, GmailScopes.GMAIL_MODIFY, GmailScopes.GMAIL_INSERT)

    private class GooglePromptReceiver: AbstractPromptReceiver() {
        override fun getRedirectUri(): String {
            return GoogleOAuthConstants.OOB_REDIRECT_URI
        }
    }

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If there is no client_secret.
     */
    @Throws(IOException::class)
    private fun getCredentials(HTTP_TRANSPORT: NetHttpTransport): Credential {
        // Build flow and trigger user authorization request.
        val flow = GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, Imap2Gmail.gmailclientid, Imap2Gmail.gmailclientsecret, SCOPES)
                .setDataStoreFactory(FileDataStoreFactory(java.io.File(CREDENTIALS_FOLDER)))
                .setAccessType("offline")
                .build()
//        return AuthorizationCodeInstalledApp(flow, LocalServerReceiver()).authorize("user") // doesn't work through firewalls
        return AuthorizationCodeInstalledApp(flow, GooglePromptReceiver()).authorize("user")
    }

    // https://github.com/google/mail-importer/blob/master/src/main/java/to/lean/tools/gmail/importer/gmail/Mailbox.java
    fun importMessage(message: Message) {
        try {
            //testing throw InterruptedException("test")
            // get rfc822 raw form of javax message and insert into gmailmessage
            debug("downloading message from IMAP...")
            val output = ByteArrayOutputStream()
            message.writeTo(output)
            val rawEmail = output.toByteArray()
            debug("importing into gmail...")
            val service = getService()
            val req1 = service.users().messages()
                    .gmailImport("me",
                            GmailAPIMessage(),
                            object : AbstractInputStreamContent("message/rfc822") {

                                @Throws(IOException::class)
                                override fun getInputStream(): InputStream {
                                    return ByteArrayInputStream(rawEmail)
                                }

                                @Throws(IOException::class)
                                override fun getLength(): Long {
                                    return rawEmail.size.toLong()
                                }

                                override fun retrySupported(): Boolean {
                                    return true
                                }
                            })
            req1.mediaHttpUploader.setProgressListener { uploader ->
                debug("[${uploader.uploadState}] Progress: ${(uploader.progress * 100).roundToInt()}")
            }

            val res1 = req1.execute()
            output.close()
            debug("done (${res1.id}), adding labels INBOX and UNREAD...")

            val req2 = service.users().messages().modify("me", res1.id, ModifyMessageRequest().setAddLabelIds(listOf("INBOX", "UNREAD")))
            req2.execute()
            debug("done!")
        } catch (e: GoogleJsonResponseException) {
            if (e.details.message.equals("Invalid From header", ignoreCase = true)) {
                throw e
            }
            throw RuntimeException(e)
        } catch (e: IOException) {
            warn("Failed to upload message!!: \n")
            throw RuntimeException(e)
        }
    }

    private fun getService(): Gmail {
        warn("initialize gmail...")
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        return Gmail.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
                .setApplicationName(APPLICATION_NAME)
                .build()
    }

    fun testit() {
        // do this to catch connection errors already on startup, otherwise only called later!
        val res = getService().users().labels().list("me").execute()
        warn("gmail initialized, have ${res.labels.size} labels!")
    }
}
