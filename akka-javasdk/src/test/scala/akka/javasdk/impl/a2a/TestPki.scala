/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.util.Base64
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

import scala.sys.process._

/**
 * Certificates for TLS tests, made with the JDK's keytool when the test runs, so no key is committed. A certificate
 * authority signs a server certificate, a server certificate for another host name, and a client certificate. A second
 * authority is trusted by nothing in the test.
 */
final class TestPki private (dir: Path) {
  import TestPki._

  private val storePassword = "changeit"

  private def keytool(args: String*): Unit = {
    val javaHome = new File(System.getProperty("java.home"))
    val exe = Seq("keytool.exe", "keytool").map(new File(new File(javaHome, "bin"), _)).find(_.exists()).get
    val output = new StringBuilder
    val code = Process(Seq(exe.getPath) ++ args ++ Seq("-storetype", "PKCS12", "-storepass", storePassword))
      .!(ProcessLogger(line => output.append(line).append('\n')))
    require(code == 0, s"keytool ${args.mkString(" ")} failed:\n$output")
  }

  private def file(name: String): String = dir.resolve(name).toString

  private def createAuthority(name: String): String = {
    keytool(
      "-genkeypair",
      "-alias",
      "ca",
      "-keyalg",
      "RSA",
      "-keysize",
      "2048",
      "-dname",
      s"CN=$name",
      "-validity",
      "2",
      "-ext",
      "bc:c",
      "-ext",
      "ku:c=keyCertSign,cRLSign",
      "-keystore",
      file(s"$name.p12"))
    keytool("-exportcert", "-alias", "ca", "-rfc", "-file", file(s"$name.pem"), "-keystore", file(s"$name.p12"))
    read(s"$name.pem")
  }

  /** The certificate is signed by the authority, and the key is in PKCS#8 PEM. */
  private def createLeaf(
      authority: String,
      name: String,
      algorithm: String,
      subjectAlternativeNames: String,
      usage: String): (String, String) = {
    val store = file(s"$name.p12")
    val keySize = if (algorithm == "EC") "256" else "2048"
    keytool(
      "-genkeypair",
      "-alias",
      name,
      "-keyalg",
      algorithm,
      "-keysize",
      keySize,
      "-dname",
      s"CN=$name",
      "-validity",
      "2",
      "-keystore",
      store)
    keytool("-certreq", "-alias", name, "-file", file(s"$name.csr"), "-keystore", store)
    keytool(
      "-gencert",
      "-alias",
      "ca",
      "-infile",
      file(s"$name.csr"),
      "-outfile",
      file(s"$name.pem"),
      "-rfc",
      "-validity",
      "2",
      "-ext",
      s"san=$subjectAlternativeNames",
      "-ext",
      s"eku=$usage",
      "-keystore",
      file(s"$authority.p12"))
    val keyStore = KeyStore.getInstance("PKCS12")
    val in = Files.newInputStream(dir.resolve(s"$name.p12"))
    try keyStore.load(in, storePassword.toCharArray)
    finally in.close()
    val key = keyStore.getKey(name, storePassword.toCharArray).asInstanceOf[PrivateKey]
    (read(s"$name.pem"), pem("PRIVATE KEY", key.getEncoded))
  }

  private def read(name: String): String = Files.readString(dir.resolve(name))

  val caCertificate: String = createAuthority("test-ca")
  val otherCaCertificate: String = createAuthority("other-ca")

  /** Valid for localhost and 127.0.0.1. */
  val (serverCertificate, serverKey) =
    createLeaf("test-ca", "server", "RSA", "dns:localhost,ip:127.0.0.1", "serverAuth")

  /** Signed by the trusted authority, but for another host name. */
  val (wrongHostCertificate, wrongHostKey) =
    createLeaf("test-ca", "wrong-host", "RSA", "dns:other.example.com", "serverAuth")

  /** An EC key, which shows that the client does not assume RSA. */
  val (clientCertificate, clientKey) = createLeaf("test-ca", "client", "EC", "dns:client.example.com", "clientAuth")

  /** The TLS context of a server that holds the certificate and trusts the authority for client certificates. */
  def serverContext(certificate: String, key: String): SSLContext = {
    val chain = CertificateFactory
      .getInstance("X.509")
      .generateCertificates(new ByteArrayInputStream(certificate.getBytes(StandardCharsets.UTF_8)))
      .toArray(Array.empty[java.security.cert.Certificate])
    val privateKey = java.security.KeyFactory
      .getInstance("RSA")
      .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(der(key)))
    val keys = KeyStore.getInstance("PKCS12")
    keys.load(null, null)
    keys.setKeyEntry("server", privateKey, Array.emptyCharArray, chain)
    val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    keyManagers.init(keys, Array.emptyCharArray)
    val trust = KeyStore.getInstance("PKCS12")
    trust.load(null, null)
    trust.setCertificateEntry("ca", caCertificateObject)
    val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagers.init(trust)
    val context = SSLContext.getInstance("TLS")
    context.init(keyManagers.getKeyManagers, trustManagers.getTrustManagers, null)
    context
  }

  private def caCertificateObject: java.security.cert.Certificate =
    CertificateFactory
      .getInstance("X.509")
      .generateCertificate(new ByteArrayInputStream(caCertificate.getBytes(StandardCharsets.UTF_8)))
}

object TestPki {
  def generate(): TestPki = new TestPki(Files.createTempDirectory("a2a-test-pki"))

  private def pem(label: String, der: Array[Byte]): String =
    s"-----BEGIN $label-----\n${Base64.getMimeEncoder(64, "\n".getBytes).encodeToString(der)}\n-----END $label-----\n"

  private def der(pem: String): Array[Byte] =
    Base64.getMimeDecoder.decode(pem.linesIterator.filterNot(_.startsWith("-----")).mkString)
}
