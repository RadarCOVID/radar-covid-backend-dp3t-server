/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package org.dpppt.backend.sdk.ws.security.signature;

import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenUnit;
import org.dpppt.backend.sdk.model.gaen.proto.TemporaryExposureKeyFormat;
import org.dpppt.backend.sdk.model.gaen.proto.TemporaryExposureKeyFormat.SignatureInfo;
import org.dpppt.backend.sdk.model.gaen.proto.v2.TemporaryExposureKeyFormatV2;
import org.dpppt.backend.sdk.model.gaen.proto.v2.TemporaryExposureKeyFormatV2.TemporaryExposureKey.ReportType;
import org.dpppt.backend.sdk.utils.UTCInstant;

public class ProtoSignature {

  private static final byte[] EXPORT_MAGIC = {
    0x45, 0x4B, 0x20, 0x45, 0x78, 0x70, 0x6F, 0x72, 0x74, 0x20, 0x76, 0x31, 0x20, 0x20, 0x20, 0x20
  }; // "EK Export v1    "

  private final String algorithm;
  private final KeyPair keyPair;
  private final String appBundleId;
  private final String apkPackage;
  private final String keyVersion;
  private final String keyVerificationId;
  private final String gaenRegion;
  private final Duration releaseBucketDuration;

  public Map<String, String> oidToJavaSignature = Map.of("1.2.840.10045.4.3.2", "SHA256withECDSA");

  public ProtoSignature(
      String algorithm,
      KeyPair keyPair,
      String appBundleId,
      String apkPackage,
      String keyVersion,
      String keyVerificationId,
      String gaenRegion,
      Duration releaseBucketDuration) {
    this.keyPair = keyPair;
    this.algorithm = algorithm.trim();
    this.appBundleId = appBundleId;
    this.apkPackage = apkPackage;
    this.keyVerificationId = keyVerificationId;
    this.keyVersion = keyVersion;
    this.gaenRegion = gaenRegion;
    this.releaseBucketDuration = releaseBucketDuration;
  }

  /**
   * Creates a ZIP file containing the given keys and the corresponding signature.
   *
   * @param keys
   * @return
   * @throws IOException
   * @throws InvalidKeyException
   * @throws SignatureException
   * @throws NoSuchAlgorithmException
   */
  public ProtoSignatureWrapper getPayload(List<GaenKey> keys)
      throws IOException, InvalidKeyException, SignatureException, NoSuchAlgorithmException {
    if (keys.isEmpty()) {
      throw new IOException("Keys should not be empty");
    }

    // Shuffle the keys so that the clients don't know the order of arrival of the keys.
    Collections.shuffle(keys);

    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    ZipOutputStream zip = new ZipOutputStream(byteOut);
    ByteArrayOutputStream hashOut = new ByteArrayOutputStream();
    var digest = MessageDigest.getInstance("SHA256");
    var keyDate = Duration.of(keys.get(0).getRollingStartNumber(), GaenUnit.TenMinutes);
    var protoFile = getProtoKey(keys, keyDate);

    zip.putNextEntry(new ZipEntry("export.bin"));
    byte[] protoFileBytes = protoFile.toByteArray();
    byte[] exportBin = new byte[EXPORT_MAGIC.length + protoFileBytes.length];
    System.arraycopy(EXPORT_MAGIC, 0, exportBin, 0, EXPORT_MAGIC.length);
    System.arraycopy(protoFileBytes, 0, exportBin, EXPORT_MAGIC.length, protoFileBytes.length);
    zip.write(exportBin);
    zip.closeEntry();

    var signatureList = getSignatureObject(exportBin);
    digest.update(exportBin);
    digest.update(keyPair.getPublic().getEncoded());
    hashOut.write(digest.digest());

    byte[] exportSig = signatureList.toByteArray();
    zip.putNextEntry(new ZipEntry("export.sig"));
    zip.write(exportSig);
    zip.closeEntry();

    zip.flush();
    zip.close();
    byteOut.flush();
    byteOut.close();
    hashOut.flush();
    hashOut.close();

    return new ProtoSignatureWrapper(hashOut.toByteArray(), byteOut.toByteArray());
  }

  /**
   * Creates a ZIP file containing the given keys and the corresponding signature. The keys are
   * returned in the new v2 protobuf format.
   *
   * @param keys
   * @return
   * @throws IOException
   * @throws InvalidKeyException
   * @throws SignatureException
   * @throws NoSuchAlgorithmException
   */
  public ProtoSignatureWrapper getPayloadV2(List<GaenKey> keys)
      throws IOException, InvalidKeyException, SignatureException, NoSuchAlgorithmException {
    if (keys.isEmpty()) {
      throw new IOException("Keys should not be empty");
    }
    // Apple likes to have keys shuffled. See
    // https://developer.apple.com/documentation/exposurenotification/setting_up_a_key_server
    // This prevents the clients to know the order of arrival of the keys.
    Collections.shuffle(keys);

    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    ZipOutputStream zip = new ZipOutputStream(byteOut);
    ByteArrayOutputStream hashOut = new ByteArrayOutputStream();
    var digest = MessageDigest.getInstance("SHA256");
    var keyDate = Duration.of(keys.get(0).getRollingStartNumber(), GaenUnit.TenMinutes);
    var protoFile = getProtoKeyV2(keys, keyDate);

    zip.putNextEntry(new ZipEntry("export.bin"));
    byte[] protoFileBytes = protoFile.toByteArray();
    byte[] exportBin = new byte[EXPORT_MAGIC.length + protoFileBytes.length];
    System.arraycopy(EXPORT_MAGIC, 0, exportBin, 0, EXPORT_MAGIC.length);
    System.arraycopy(protoFileBytes, 0, exportBin, EXPORT_MAGIC.length, protoFileBytes.length);
    zip.write(exportBin);
    zip.closeEntry();

    var signatureList = getSignatureObjectV2(exportBin);
    digest.update(exportBin);
    digest.update(keyPair.getPublic().getEncoded());
    hashOut.write(digest.digest());

    byte[] exportSig = signatureList.toByteArray();
    zip.putNextEntry(new ZipEntry("export.sig"));
    zip.write(exportSig);
    zip.closeEntry();

    zip.flush();
    zip.close();
    byteOut.flush();
    byteOut.close();
    hashOut.flush();
    hashOut.close();

    return new ProtoSignatureWrapper(hashOut.toByteArray(), byteOut.toByteArray());
  }

  private byte[] sign(byte[] data)
      throws SignatureException, InvalidKeyException, NoSuchAlgorithmException {
    Signature signature = Signature.getInstance(oidToJavaSignature.get(algorithm));
    signature.initSign(keyPair.getPrivate());
    signature.update(data);
    return signature.sign();
  }

  private org.dpppt.backend.sdk.model.gaen.proto.v2.TemporaryExposureKeyFormatV2.TEKSignatureList
      getSignatureObjectV2(byte[] keyExport)
          throws InvalidKeyException, SignatureException, NoSuchAlgorithmException {
    byte[] exportSignature = sign(keyExport);
    var signatureList = TemporaryExposureKeyFormatV2.TEKSignatureList.newBuilder();
    var theSignature = TemporaryExposureKeyFormatV2.TEKSignature.newBuilder();
    theSignature
        .setSignatureInfo(tekSignatureV2())
        .setSignature(ByteString.copyFrom(exportSignature))
        .setBatchNum(1)
        .setBatchSize(1);
    signatureList.addSignatures(theSignature);
    return signatureList.build();
  }

  private org.dpppt.backend.sdk.model.gaen.proto.v2.TemporaryExposureKeyFormatV2.SignatureInfo
      tekSignatureV2() {
    var tekSignature = TemporaryExposureKeyFormatV2.SignatureInfo.newBuilder();
    tekSignature
        .setVerificationKeyVersion(keyVersion)
        .setVerificationKeyId(keyVerificationId)
        .setSignatureAlgorithm(algorithm);
    return tekSignature.build();
  }

  private TemporaryExposureKeyFormat.TEKSignatureList getSignatureObject(byte[] keyExport)
      throws InvalidKeyException, SignatureException, NoSuchAlgorithmException {
    byte[] exportSignature = sign(keyExport);
    var signatureList = TemporaryExposureKeyFormat.TEKSignatureList.newBuilder();
    var theSignature = TemporaryExposureKeyFormat.TEKSignature.newBuilder();
    theSignature
        .setSignatureInfo(tekSignature())
        .setSignature(ByteString.copyFrom(exportSignature))
        .setBatchNum(1)
        .setBatchSize(1);
    signatureList.addSignatures(theSignature);
    return signatureList.build();
  }

  private SignatureInfo tekSignature() {
    var tekSignature = TemporaryExposureKeyFormat.SignatureInfo.newBuilder();
    tekSignature
        .setAppBundleId(appBundleId)
        .setVerificationKeyVersion(keyVersion)
        .setVerificationKeyId(keyVerificationId)
        .setSignatureAlgorithm(algorithm);
    return tekSignature.build();
  }

  public PublicKey getPublicKey() {
    return keyPair.getPublic();
  }

  public byte[] getPayload(Map<String, List<GaenKey>> groupedBuckets)
      throws IOException, InvalidKeyException, SignatureException, NoSuchAlgorithmException {
    ByteArrayOutputStream byteOutCollection = new ByteArrayOutputStream();
    ZipOutputStream zipCollection = new ZipOutputStream(byteOutCollection);

    for (var keyGroup : groupedBuckets.entrySet()) {
      var keys = keyGroup.getValue();
      var group = keyGroup.getKey();
      if (keys.isEmpty()) {
        continue;
      }

      var keyDate = Duration.of(keys.get(0).getRollingStartNumber(), GaenUnit.TenMinutes);
      var protoFile = getProtoKeyV2(keys, keyDate);
      var zipFileName = new StringBuilder();

      zipFileName.append("key_export_").append(group);

      zipCollection.putNextEntry(new ZipEntry(zipFileName.toString()));
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
      ZipOutputStream zip = new ZipOutputStream(byteOut);

      zip.putNextEntry(new ZipEntry("export.bin"));
      byte[] protoFileBytes = protoFile.toByteArray();
      byte[] exportBin = new byte[EXPORT_MAGIC.length + protoFileBytes.length];
      System.arraycopy(EXPORT_MAGIC, 0, exportBin, 0, EXPORT_MAGIC.length);
      System.arraycopy(protoFileBytes, 0, exportBin, EXPORT_MAGIC.length, protoFileBytes.length);
      zip.write(exportBin);
      zip.closeEntry();

      var signatureList = getSignatureObjectV2(exportBin);

      byte[] exportSig = signatureList.toByteArray();
      zip.putNextEntry(new ZipEntry("export.sig"));
      zip.write(exportSig);
      zip.closeEntry();
      zip.flush();
      zip.close();
      byteOut.flush();
      byteOut.close();
      zipCollection.write(byteOut.toByteArray());

      zipCollection.closeEntry();
    }
    zipCollection.flush();
    zipCollection.close();
    byteOutCollection.close();
    return byteOutCollection.toByteArray();
  }

  public byte[] getPayload(Collection<List<GaenKey>> buckets)
      throws IOException, InvalidKeyException, SignatureException, NoSuchAlgorithmException {
    Map<String, List<GaenKey>> grouped = new HashMap<String, List<GaenKey>>();
    for (var keys : buckets) {
      if (keys.isEmpty()) continue;
      var keyLocalDate =
          UTCInstant.of(keys.get(0).getRollingStartNumber(), GaenUnit.TenMinutes)
              .getLocalDate()
              .toString();
      grouped.put(keyLocalDate, keys);
    }
    return getPayload(grouped);
  }

  private TemporaryExposureKeyFormat.TemporaryExposureKeyExport getProtoKey(
      List<GaenKey> exposedKeys, Duration batchReleaseTimeDuration) {
    var file = TemporaryExposureKeyFormat.TemporaryExposureKeyExport.newBuilder();

    var tekList = new ArrayList<TemporaryExposureKeyFormat.TemporaryExposureKey>();
    for (var key : exposedKeys) {
      var protoKey =
          TemporaryExposureKeyFormat.TemporaryExposureKey.newBuilder()
              .setKeyData(ByteString.copyFrom(Base64.getDecoder().decode(key.getKeyData())))
              .setRollingPeriod(key.getRollingPeriod())
              .setRollingStartIntervalNumber(key.getRollingStartNumber())
              .setTransmissionRiskLevel(key.getTransmissionRiskLevel())
              .build();
      tekList.add(protoKey);
    }

    file.addAllKeys(tekList);

    file.setRegion(gaenRegion)
        .setBatchNum(1)
        .setBatchSize(1)
        .setStartTimestamp(batchReleaseTimeDuration.toSeconds())
        .setEndTimestamp(batchReleaseTimeDuration.toSeconds() + releaseBucketDuration.toSeconds());

    file.addSignatureInfos(tekSignature());

    return file.build();
  }

  private TemporaryExposureKeyFormatV2.TemporaryExposureKeyExport getProtoKeyV2(
      List<GaenKey> exposedKeys, Duration batchReleaseTimeDuration) {
    var file = TemporaryExposureKeyFormatV2.TemporaryExposureKeyExport.newBuilder();

    var tekList = new ArrayList<TemporaryExposureKeyFormatV2.TemporaryExposureKey>();
    for (var key : exposedKeys) {
      var protoKey =
          TemporaryExposureKeyFormatV2.TemporaryExposureKey.newBuilder()
              .setKeyData(ByteString.copyFrom(Base64.getDecoder().decode(key.getKeyData())))
              .setRollingPeriod(key.getRollingPeriod())
              .setRollingStartIntervalNumber(key.getRollingStartNumber())
              .setDaysSinceOnsetOfSymptoms(key.getDaysSinceOnsetOfSymptons().intValue()) 
              .setReportType(ReportType.forNumber(key.getReportType()))
              .build();
      tekList.add(protoKey);
    }

    file.addAllKeys(tekList);

    file.setRegion(gaenRegion)
        .setBatchNum(1)
        .setBatchSize(1)
        .setStartTimestamp(batchReleaseTimeDuration.toSeconds())
        .setEndTimestamp(batchReleaseTimeDuration.toSeconds() + releaseBucketDuration.toSeconds());

    file.addSignatureInfos(tekSignatureV2());

    return file.build();
  }

  public class ProtoSignatureWrapper {
    private final byte[] hash;
    private final byte[] zip;

    public ProtoSignatureWrapper(byte[] hash, byte[] zip) {
      this.hash = hash;
      this.zip = zip;
    }

    public byte[] getHash() {
      return hash;
    }

    public byte[] getZip() {
      return zip;
    }
  }
}
