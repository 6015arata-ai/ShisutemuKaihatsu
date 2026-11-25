package com.hackathon.kintai.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "t_kintai")
@Data
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendance_id")
    private Long id;

    @Column(name = "user_id", length = 10, nullable = false)
    private String userId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "clockin_time")
    private LocalDateTime startTime;

    @Column(name = "clockout_time")
    private LocalDateTime endTime;

    @Column(name = "break_minutes", nullable = false)
    private Integer breakMinutes = 0;

    @Column(name = "work_place", length = 100)
    private String workPlace;

    @Column(name = "overtime_reason", length = 500)
    private String overtimeReason;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // t_kintai に存在しない項目は @Transient にして DB カラム化を防ぐ
    @Transient
    private String userName;

    @Transient
    private LocalDateTime breakStartTime;

    @Transient
    private LocalDateTime breakEndTime;

    @PrePersist
    @PreUpdate
    public void prePersistOrUpdate() {
        if (this.breakMinutes == null) {
            this.breakMinutes = 0;
        }
        if (this.workDate == null) {
            if (this.startTime != null) {
                this.workDate = this.startTime.toLocalDate();
            } else {
                this.workDate = LocalDate.now();
            }
        }
        this.updatedAt = LocalDateTime.now();
    }

    // 内部計算用の「最終的な休憩時間（分）」を取得
    public int getCalculatedBreakMinutes() {
        if (breakMinutes != null && breakMinutes > 0) {
            return breakMinutes;
        }
        if (breakStartTime != null && breakEndTime != null) {
            return (int) Duration.between(breakStartTime, breakEndTime).toMinutes();
        }
        return breakMinutes != null ? breakMinutes : 0;
    }

    // 画面表示用の休憩時間
    public String getBreakTimeDisplay() {
        int mins = getCalculatedBreakMinutes();
        if (mins == 0) return "0分";
        if (mins < 60) return mins + "分";
        return (mins / 60) + "時間" + (mins % 60) + "分";
    }

    // 実労働時間の計算
    public String getWorkTime() {
        if (startTime == null || endTime == null) {
            return "-";
        }
        long totalMinutes = Duration.between(startTime, endTime).toMinutes();
        long actualMinutes = totalMinutes - getCalculatedBreakMinutes();

        if (actualMinutes < 0) return "エラー(時間不整合)";

        if (actualMinutes < 60) {
            return actualMinutes + "分";
        } else {
            return (actualMinutes / 60) + "時間" + (actualMinutes % 60) + "分";
        }
    }
}