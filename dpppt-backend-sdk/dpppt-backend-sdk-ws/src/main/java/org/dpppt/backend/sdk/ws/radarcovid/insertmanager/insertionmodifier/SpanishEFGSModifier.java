package org.dpppt.backend.sdk.ws.radarcovid.insertmanager.insertionmodifier;

import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenUnit;
import org.dpppt.backend.sdk.semver.Version;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.dpppt.backend.sdk.ws.insertmanager.InsertException;
import org.dpppt.backend.sdk.ws.insertmanager.OSType;
import org.dpppt.backend.sdk.ws.insertmanager.insertionmodifier.KeyInsertionModifier;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class SpanishEFGSModifier implements KeyInsertionModifier {

    private final String countryOrigin;
    private final int reportType;

    public SpanishEFGSModifier(String countryOrigin, int reportType) {
        this.countryOrigin = countryOrigin;
        this.reportType = reportType;
    }

    @Override
    public List<GaenKey> modify(UTCInstant now, List<GaenKey> content, OSType osType, Version osVersion,
                                Version appVersion, Object principal) throws InsertException {

        Jwt token = null;
        boolean efgsSharing = false;
        String onsetDay = LocalDate.now().toString();

        if (principal instanceof Jwt) {
            token = (Jwt) principal;
            efgsSharing = token.containsClaim("efgs") && token.getClaimAsBoolean("efgs");
            if (token.containsClaim("onset")) {
                onsetDay = token.getClaimAsString("onset");
            }
        }

        for (GaenKey gaenKey : content) {
            gaenKey.setCountryOrigin(countryOrigin);
            gaenKey.setEfgsSharing(efgsSharing);
            gaenKey.setReportType(reportType);
            gaenKey.setDaysSinceOnsetOfSymptons(daysSinceOnsetOfSymtoms(onsetDay, gaenKey.getRollingStartNumber()));
            gaenKey.getVisitedCountries().add(countryOrigin);
        }

        return content;
    }

    private long daysSinceOnsetOfSymtoms(String dateAsString, long rollingStartNumber) {
        var onset = UTCInstant.parseDate(dateAsString);
        var rollingDate = UTCInstant.midnight1970().plus(
                GaenUnit.TenMinutes.getDuration().multipliedBy(rollingStartNumber));
        return ChronoUnit.DAYS.between(rollingDate.getLocalDate(), onset.getLocalDate());
    }

}
