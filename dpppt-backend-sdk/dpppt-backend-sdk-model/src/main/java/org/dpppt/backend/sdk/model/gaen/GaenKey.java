package org.dpppt.backend.sdk.model.gaen;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A GaenKey is a Temporary Exposure Key of a person being infected, so it's also an Exposed Key.
 * To protect timing attacks, a key can be invalidated by the client by setting _fake_ to 1.
 */
public class GaenKey {
    public final static Integer GaenKeyDefaultRollingPeriod = 144;

    @NotNull
    @Size(min = 24, max = 24)
    @Schema(description = "Represents the 16-byte Temporary Exposure Key in base64")
    private String keyData;

    @NotNull
    @Schema(description = "The ENIntervalNumber as number of 10-minute intervals since the Unix epoch (1970-01-01)")
    private Integer rollingStartNumber;

    @NotNull
    @Schema(description = "The TEKRollingPeriod indicates for how many 10-minute intervals the Temporary Exposure Key is valid")
    private Integer rollingPeriod;

    @NotNull
    @Schema(description = "According to the Google API description a value between 0 and 4096, with higher values indicating a higher risk")
    private Integer transmissionRiskLevel;

    @Schema(description = "If fake = 0, the key is a valid key. If fake = 1, the key will be discarded.")
    private Integer fake = 0;

    public GaenKey() {
    }

    public GaenKey(String keyData, Integer rollingStartNumber, Integer rollingPeriod, Integer transmissionRiskLevel) {
        this.keyData = keyData;
        this.rollingStartNumber = rollingStartNumber;
        this.rollingPeriod = rollingPeriod;
        this.transmissionRiskLevel = transmissionRiskLevel;
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

}