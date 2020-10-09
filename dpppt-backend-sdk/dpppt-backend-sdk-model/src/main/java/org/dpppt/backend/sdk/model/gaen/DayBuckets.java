package org.dpppt.backend.sdk.model.gaen;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public class DayBuckets {

	@Schema(description = "The day of all buckets, as midnight in milliseconds since the Unix epoch (1970-01-01)", example = "1593043200000")
	private Long dayTimestamp;
	@Schema(description = "The day as given by the request in /v1/gaen/buckets/{dayDateStr}", example = "2020-06-27")
	private String day;
	@Schema(description = "Relative URLs for the available release buckets", example = "['/exposed/1593043200000', '/exposed/1593046800000'")
	private List<String> relativeUrls;

    public String getDay() {
        return this.day;
    }

    public List<String> getRelativeUrls() {
        return this.relativeUrls;
    }
    
    public Long getDayTimestamp() {
		return dayTimestamp;
	}

    public DayBuckets setDay(String day) {
        this.day = day;
        return this;
    }

    public DayBuckets setRelativeUrls(List<String> relativeUrls) {
        this.relativeUrls = relativeUrls;
        return this;
    }
    
    public DayBuckets setDayTimestamp(Long dayTimestamp) {
    	this.dayTimestamp = dayTimestamp;
    	return this;
    }
    
}