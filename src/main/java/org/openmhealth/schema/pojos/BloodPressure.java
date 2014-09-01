package org.openmhealth.schema.pojos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import org.joda.time.DateTime;
import org.openmhealth.schema.pojos.generic.DescriptiveStatistic;
import org.openmhealth.schema.pojos.generic.TimeFrame;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonRootName(value = BloodPressure.SCHEMA_BLOOD_PRESSURE, namespace = DataPoint.NAMESPACE)
public class BloodPressure extends BaseDataPoint {

    @JsonProperty(value = "systolic-blood-pressure", required = false)
    private SystolicBloodPressure systolic;

    @JsonProperty(value = "diastolic-blood-pressure", required = false)
    private DiastolicBloodPressure diastolic;

    @JsonProperty(value = "effective-time-frame", required = false)
    private TimeFrame effectiveTimeFrame;

    @JsonProperty(value = "position-during-measurement", required = false)
    private Position position;

    @JsonProperty(value = "numeric-descriptor", required = false)
    private DescriptiveStatistic descriptiveStatistic;

    public static final String SCHEMA_BLOOD_PRESSURE = "blood-pressure";

    @JsonProperty(value = "notes", required = false)
    private String notes;

    public enum Position {sitting, lying_down, standing}

    @Override
    @JsonIgnore
    public String getSchemaName() {
        return SCHEMA_BLOOD_PRESSURE;
    }

    @Override
    @JsonIgnore
    public DateTime getTimeStamp() {
        return effectiveTimeFrame.getTimestamp();
    }

    public TimeFrame getEffectiveTimeFrame() {
        return effectiveTimeFrame;
    }

    public void setEffectiveTimeFrame(TimeFrame effectiveTimeFrame) {
        this.effectiveTimeFrame = effectiveTimeFrame;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public SystolicBloodPressure getSystolic() {
        return systolic;
    }

    public void setSystolic(SystolicBloodPressure systolic) {
        this.systolic = systolic;
    }

    public DiastolicBloodPressure getDiastolic() {
        return diastolic;
    }

    public void setDiastolic(DiastolicBloodPressure diastolic) {
        this.diastolic = diastolic;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public DescriptiveStatistic getDescriptiveStatistic() {
        return descriptiveStatistic;
    }

    public void setDescriptiveStatistic(DescriptiveStatistic descriptiveStatistic) {
        this.descriptiveStatistic = descriptiveStatistic;
    }
}
