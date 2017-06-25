package com.helix.aws

import com.amazonaws.auth.{AWSCredentialsProvider, BasicAWSCredentials, BasicSessionCredentials, DefaultAWSCredentialsProviderChain}

import scala.util.Try

/**
  * From https://github.com/samtools/htslib/blob/develop/hfile_s3.c
  *
  *       "Our S3 URL format is s3[+SCHEME]://[ID[:SECRET[:TOKEN]]@]BUCKET/PATH"
  */
case object S3UrlGenerator {

  val credentialsProvider: AWSCredentialsProvider = DefaultAWSCredentialsProviderChain.getInstance()

  def generateUrl(bucket: String, path: String): Try[String] = {
    val creds = credentialsProvider.getCredentials

    def basicUrl(accessKey: String, secretKey: String): Try[String] = Try{
      if( List(accessKey, secretKey) exists { _ matches "[:@]+" } ) {
        throw new IllegalArgumentException("basic IAM credentials contain delimiter characters")
      }

      s"s3://$accessKey:$secretKey@$bucket/$path"
    }

    def sessionUrl(accessKey: String, secretKey: String, token: String): Try[String] = Try{
      if( List(accessKey, secretKey, token) exists { _ contains ":" } ) {
        throw new IllegalArgumentException("session IAM credentials contain delimiter characters")
      }

      s"s3://$accessKey:$secretKey:$token@$bucket/$path"
    }

    creds match {
      case c: BasicAWSCredentials =>
        basicUrl(c.getAWSAccessKeyId, c.getAWSSecretKey)
      case c: BasicSessionCredentials =>
        sessionUrl(c.getAWSAccessKeyId, c.getAWSSecretKey, c.getSessionToken)
      case _ =>
        throw new Exception(s"unsupported credentials type: ${creds.getClass}")
    }
  }
}