/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

import akka.annotation.InternalApi

/**
 * INTERNAL API
 *
 * Builds the TLS context for calls to a remote A2A agent from PEM text, with JDK classes only.
 */
@InternalApi
private[akka] object A2aTls {

  private val NoPassword = Array.emptyCharArray

  /**
   * @param caBundle
   *   PEM certificates that replace the trusted certificates of the JVM for this agent, or `None` to keep them
   * @param clientCertificate
   *   PEM certificate chain, the client certificate first
   * @param clientKey
   *   PKCS#8 PEM private key of the client certificate
   */
  def sslContext(caBundle: Option[String], clientCertificate: Option[String], clientKey: Option[String]): SSLContext = {
    val trustManagers: Array[TrustManager] = caBundle match {
      case Some(pem) => trustManagersFor(pem)
      case None      => null // the default trust of the JVM
    }
    val keyManagers: Array[KeyManager] = (clientCertificate, clientKey) match {
      case (Some(certPem), Some(keyPem)) => keyManagersFor(certPem, keyPem)
      case _                             => null
    }
    val context = SSLContext.getInstance("TLS")
    context.init(keyManagers, trustManagers, null)
    context
  }

  private def certificates(pem: String, what: String): Seq[Certificate] = {
    val certs =
      try CertificateFactory
        .getInstance("X.509")
        .generateCertificates(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)))
        .asScala
        .toSeq
      catch {
        case NonFatal(e) => throw new IllegalArgumentException(s"The $what is not valid PEM certificate text", e)
      }
    if (certs.isEmpty) throw new IllegalArgumentException(s"The $what holds no certificate")
    certs
  }

  private def newKeyStore(): KeyStore = {
    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(null, null)
    keyStore
  }

  private def trustManagersFor(caBundle: String): Array[TrustManager] = {
    val keyStore = newKeyStore()
    certificates(caBundle, "CA bundle").zipWithIndex.foreach { case (cert, i) =>
      keyStore.setCertificateEntry(s"ca-$i", cert)
    }
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore)
    factory.getTrustManagers
  }

  private def keyManagersFor(certificate: String, key: String): Array[KeyManager] = {
    val chain = certificates(certificate, "client certificate")
    val keyStore = newKeyStore()
    keyStore.setKeyEntry("client", privateKey(key), NoPassword, chain.toArray)
    val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore, NoPassword)
    factory.getKeyManagers
  }

  private val KeyBegin = "-----BEGIN PRIVATE KEY-----"
  private val KeyEnd = "-----END PRIVATE KEY-----"

  private def privateKey(pem: String): PrivateKey = {
    if (pem.contains("BEGIN ENCRYPTED PRIVATE KEY") || pem.contains("BEGIN RSA PRIVATE KEY") ||
      pem.contains("BEGIN EC PRIVATE KEY"))
      throw new IllegalArgumentException(
        "The client key must be an unencrypted PKCS#8 key (BEGIN PRIVATE KEY). " +
        "Convert it with: openssl pkcs8 -topk8 -nocrypt")
    val begin = pem.indexOf(KeyBegin)
    val end = pem.indexOf(KeyEnd)
    if (begin < 0 || end < begin) throw new IllegalArgumentException("The client key is not PKCS#8 PEM text")
    val der =
      try Base64.getMimeDecoder.decode(pem.substring(begin + KeyBegin.length, end))
      catch { case NonFatal(e) => throw new IllegalArgumentException("The client key is not valid Base64", e) }
    val spec = new PKCS8EncodedKeySpec(der)
    // A PKCS#8 key does not say which algorithm it is for, so each supported one is tried
    Iterator("RSA", "EC", "Ed25519")
      .flatMap(algorithm =>
        try Some(KeyFactory.getInstance(algorithm).generatePrivate(spec))
        catch { case NonFatal(_) => None })
      .nextOption()
      .getOrElse(throw new IllegalArgumentException("The client key is not an RSA, EC or Ed25519 PKCS#8 key"))
  }
}
