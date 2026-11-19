package com.hackathon.kintai.model;

import java.time.LocalDateTime;
import java.time.Duration;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "attendances")
@Data
public class Attendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String userName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // パートナー画面の打刻で記録される休憩時間
    private LocalDateTime breakStartTime;
    private LocalDateTime breakEndTime;

    // 🆕 管理者が手動で修正・入力した休憩時間（分）
    private Integer breakMinutes;

    // 🆕 詳細設計書準拠：勤務場所（最大100文字）
    @Column(name = "work_place", length = 100)
    private String workPlace;

    // 🆕 詳細設計書準拠：残業理由（最大500文字）
    @Column(name = "overtime_reason", length = 500)
    private String overtimeReason;

    // 内部計算用の「最終的な休憩時間（分）」を取得
    public int getCalculatedBreakMinutes() {
        if (breakMinutes != null) {
            return breakMinutes; // 手動修正があれば優先
        }
        if (breakStartTime != null && breakEndTime != null) {
            return (int) Duration.between(breakStartTime, breakEndTime).toMinutes(); // 打刻から計算
        }
        return 0; // 休憩なし
    }

    // 画面表示用の休憩時間（例：1時間0分、45分など）
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