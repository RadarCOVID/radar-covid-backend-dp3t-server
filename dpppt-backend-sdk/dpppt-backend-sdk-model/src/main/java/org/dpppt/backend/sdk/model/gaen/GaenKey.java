package org.dpppt.backend.sdk.model.gaen;

import ch.ubique.openapi.docannotations.Documentation;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * A GaenKey is a Temporary Exposure Key of a person being infected, so it's also an Exposed Key. To
 * protect timing attacks, a key can be invalidated by the client by setting _fake_ to 1.
 */
public class GaenKey {
  public static final Integer GaenKeyDefaultRollingPeriod = 144;

  @NotNull
  @Size(min = 24, max = 24)
  @Documentation(description = "Represents the 16-byte Temporary Exposure Key in base64")
  private String keyData;

  @NotNull
  @Documentation(
      description =
          "The ENIntervalNumber as number of 10-minute intervals since the Unix epoch (1970-01-01)")
  private Integer rollingStartNumber;

  @NotNull
  @Documentation(
      description =
          "The TEKRollingPeriod indicates for how many 10-minute intervals the Temporary Exposure"
              + " Key is valid")
  private Integer rollingPeriod;

  @NotNull
  @Documentation(
      description =
          "According to the Google API description a value between 0 and 4096, with higher values"
              + " indicating a higher risk")
  private Integer transmissionRiskLevel;

  @Documentation(
      description = "If fake = 0, the key is a valid key. If fake = 1, the key will be discarded.")
  private Integer fake = 0;

  @Documentation(
      description = "Country origin for EFGS")
  private String countryOrigin;

  @Documentation(
      description = "Report Type")
  private Integer reportType;

  @Documentation(
      description = "Days since onset of symptons")
  private Long daysSinceOnsetOfSymptons;

  @Documentation(
      description = "If key is shareable with EFGS or not")
  private Boolean efgsSharing;
  
  @Documentation(
	      description = "Visited countries for EFGS")
  private List<String> visitedCountries = new ArrayList<>();
  
  public GaenKey() {}

  public GaenKey(
      String keyData,
      Integer rollingStartNumber,
      Integer rollingPeriod,
      Integer transmissionRiskLevel,
      String countryOrigin,
      Integer reportType,
      Long daysSinceOnsetOfSymptons,
      Boolean efgsSharing,
      List<String> visitedCountries) {
    this.keyData = keyData;
    this.rollingStartNumber = rollingStartNumber;
    this.rollingPeriod = rollingPeriod;
    this.transmissionRiskLevel = transmissionRiskLevel;
    this.countryOrigin = countryOrigin;
    this.reportType = reportType;
    this.daysSinceOnsetOfSymptons = daysSinceOnsetOfSymptons;
    this.efgsSharing = efgsSharing;
    this.visitedCountries = visitedCountries;
  }

  public String getKeyData() {
    return this.keyData;
  }

  public void setKeyData(String keyData) {
    this.keyData = keyData;
  }

  public Integer getRollingStartNumber() {
    return this.rollingStartNumber;
  }

  public void setRollingStartNumber(Integer rollingStartNumber) {
    this.rollingStartNumber = rollingStartNumber;
  }

  public Integer getRollingPeriod() {
    return this.rollingPeriod;
  }

  public void setRollingPeriod(Integer rollingPeriod) {
    this.rollingPeriod = rollingPeriod;
  }

  public Integer getTransmissionRiskLevel() {
    return this.transmissionRiskLevel;
  }

  public void setTransmissionRiskLevel(Integer transmissionRiskLevel) {
    this.transmissionRiskLevel = transmissionRiskLevel;
  }

  public Integer getFake() {
    return this.fake;
  }

  public void setFake(Integer fake) {
    this.fake = fake;
  }

  public String getCountryOrigin() {
    return countryOrigin;
  }

  public void setCountryOrigin(String countryOrigin) {
    this.countryOrigin = countryOrigin;
  }

  public Integer getReportType() {
    return reportType;
  }

  public void setReportType(Integer reportType) {
    this.reportType = reportType;
  }

  public Long getDaysSinceOnsetOfSymptons() {
    return daysSinceOnsetOfSymptons;
  }

  public void setDaysSinceOnsetOfSymptons(Long daysSinceOnsetOfSymptons) {
    this.daysSinceOnsetOfSymptons = daysSinceOnsetOfSymptons;
  }

  public Boolean getEfgsSharing() {
    return efgsSharing;
  }

  public void setEfgsSharing(Boolean efgsSharing) {
    this.efgsSharing = efgsSharing;
  }

  public List<String> getVisitedCountries() {
	return visitedCountries;
  }

  public void setVisitedCountries(List<String> visitedCountries) {
	this.visitedCountries = visitedCountries;
  }

  @Override
  public String toString() {
    return "GaenKey{" +
            "keyData='" + keyData + '\'' +
            ", rollingStartNumber=" + rollingStartNumber +
            ", rollingPeriod=" + rollingPeriod +
            ", transmissionRiskLevel=" + transmissionRiskLevel +
            ", fake=" + fake +
            ", countryOrigin='" + countryOrigin + '\'' +
            ", reportType=" + reportType +
            ", daysSinceOnsetOfSymptons=" + daysSinceOnsetOfSymptons +
            ", efgsSharing=" + efgsSharing +
            ", visitedCountries=" + visitedCountries +
            '}';
  }

}
