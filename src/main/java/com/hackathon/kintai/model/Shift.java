package com.hackathon.kintai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_shift")
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shift_id", nullable = false)
    private Long shiftId;

    @Column(name = "user_id", length = 10, nullable = false)
    private String userId;

    @Column(name = "target_month", columnDefinition = "char(7)", nullable = false)
    private String targetMonth;

    @Column(name = "period_type", length = 10, nullable = false)
    private String periodType;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "leave_type", length = 10)
    private String leaveType;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    public Shift() {
    }

    public Shift(
            Long shiftId,
            String userId,
            String targetMonth,
            String periodType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String leaveType,
            LocalDateTime submittedAt) {
        this.shiftId = shiftId;
        this.userId = userId;
        this.targetMonth = targetMonth;
        this.periodType = periodType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.leaveType = leaveType;
        this.submittedAt = submittedAt;
    }

    public Long getShiftId() {
        return shiftId;
    }

    public void setShiftId(Long shiftId) {
        this.shiftId = shiftId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTargetMonth() {
        return targetMonth;
    }

    public void setTargetMonth(String targetMonth) {
        this.targetMonth = targetMonth;
    }

    public String getPeriodType() {
        return periodType;
    }

    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(String leaveType) {
        this.leaveType = leaveType;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
}