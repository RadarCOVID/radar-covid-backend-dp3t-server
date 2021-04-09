package org.dpppt.backend.sdk.data.gaen;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.springframework.jdbc.core.RowMapper;

public class GaenKeyRowMapper implements RowMapper<GaenKey> {

  @Override
  public GaenKey mapRow(ResultSet rs, int rowNum) throws SQLException {
    var gaenKey = new GaenKey();
    gaenKey.setKeyData(rs.getString("key"));
    gaenKey.setRollingStartNumber(rs.getInt("rolling_start_number"));
    gaenKey.setRollingPeriod(rs.getInt("rolling_period"));
    gaenKey.setTransmissionRiskLevel(rs.getInt("transmission_risk_level"));
    
    Long daysSinceOnset;

    try {
    	daysSinceOnset = rs.getLong("days_since_onset");
    } catch (java.sql.SQLException e) {
    	daysSinceOnset = null;
    }
    if(daysSinceOnset != null) {
        gaenKey.setDaysSinceOnsetOfSymptons(daysSinceOnset);    	
    }

    Integer reportType;

    try {
    	reportType = rs.getInt("report_type");
    } catch (java.sql.SQLException e) {
    	reportType = null;
    }
    if(reportType != null) {
        gaenKey.setReportType(reportType);    	
    }

    return gaenKey;
  }
}
