# Introduction

Goal: Get continuously all emails from an IMAP server into GMail without loosing emails, and using all features (filtering, spam detection) of GMail.

Imap2Gmail uses imap IDLE to poll emails from an IMAP server and imports them into a GMail account via GMail API Users.messages.import,
this ensures that mail scanners and filters are applied in GMail. If successful, the email is moved to the folder `imapfoldermoved` on the IMAP server.
imap2gmail is supposed to run on some server 24/7 which can send emails for error notification. Disable SPAM mail filtering on the IMAP server.

I wrote this little program because (i) normal GMail doesn't poll IMAP servers and (ii) "redirecting" emails to GMail is very unreliable, in particular with Exchange servers.

# How to use

* Generate your own GMail API keys for imap2gmail, see below.
* Download and extract the [zip](https://github.com/wolfgangasdf/imap2gmail/releases) or `wget https://github.com/wolfgangasdf/imap2gmail/releases/download/SNAPSHOT/imap2gmail-linux.zip`. It is so large because it contains a Java runtime environment.
* Run `imap2gmail-linux/bin/imap2gmail` or `while : ; do imap2gmail-linux/bin/imap2gmail ; sleep 60 ; done` (for low-end systems, where the jvm can be killed due to out-of-memory). 
* After the first start, the settings file `~/imap2gmail-settings.txt` will be created, which you have to edit. See below.
* On the second start, you have to give imap2gmail access to your gmail account, follow the instructions (note: in browser, click `advanced`, then `proceed...`).
* I run imap2gmail in a [GNU screen](https://en.wikipedia.org/wiki/GNU_Screen) that is started automatically.

## GMail API keys
Unfortunately, you need to get your own GMail API keys for imap2gmail; there is a quota for API usage and I don't want to deal with this.

Follow https://developers.google.com/gmail/api/quickstart/java , which means:

1. Go to click https://console.developers.google.com/start/api?id=gmail
2. Select `Create a project`, `Continue`, then on the `Add credentials to your project` page, click the `Cancel` button.
3. Select the `OAuth consent screen` tab. Select an Email address, enter a Product name if not already set, and click the Save button.
4. Select the `Credentials` tab, click `Create credentials` and select `OAuth client ID`.
5. Select the application type `Other`, enter the name `imap2gmail`, and click the Create button.
6. Copy and paste the client id and secret id into the imap2gmail.txt settings.

## Example settings file

~~~~
# the server from which emails are pulled
imapprotocol=imaps
imapserver=imap.imapserver.org
imapport=993
imapuser=username1
imappass=password1
imapfolder=INBOX
imapfoldermoved=nowingmail
# warning email settings
mailfrom=root@ownserver.org
mailto=mygmailusername@gmail.com
mailsmtpprot=smtp
mailsmtpserver=localhost
mailsmtpport=-1
mailsmtpuser=
mailsmtppass=
# your GMail API keys for imap2gmail
gmailclientid=blablabla.apps.googleusercontent.com
gmailclientsecret=asdfasddfgdfs
# maximum email size, larger emails will be quarantined.
maxemailsize=20000000
~~~~


# How to develop, compile & package

* Get Java JDK >= 13 and [gradle](https://gradle.org/install/)
* check out the code (`git clone ...` or download a zip)
* I use the free community version of [IntelliJ IDEA](https://www.jetbrains.com/idea/download/), just open the project to get started.
* Run: `gradle run`
* Package:
  * Download a suitable JDK for the target platform (if not same as development) from https://jdk.java.net/13/
  * Point to it `JDK_LINUX_HOME=/.../openjdk-13_linux-x64/` 
  * `gradle runtimeZip`. The resulting (~40 MB) ZIP file is `build/image-zip/imap2gmail-linux.zip`


# Used frameworks #

* [Kotlin](https://kotlinlang.org)
* [JavaMail](https://javaee.github.io/javamail/)
* [Beryx runtime](https://badass-runtime-plugin.beryx.org) to create the app image with JRE etc.
