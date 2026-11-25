package com.hackathon.kintai.controller;

import com.hackathon.kintai.model.*;
import com.hackathon.kintai.repository.*;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/partner")
public class PartnerController {

    @Autowired private AttendanceRepository attendanceRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private ShiftRepository shiftRepo;

    // セッションチェック共通ロジック（新しいUserのuserId / isActiveで判定）
    private boolean isInvalidSession(HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null || sessionUser.getUserId() == null) return true;
        User dbUser = userRepo.findById(sessionUser.getUserId()).orElse(null);
        if (dbUser == null || Boolean.FALSE.equals(dbUser.getIsActive())) {
            session.invalidate();
            return true;
        }
        return false;
    }

    private String normalizeTargetMonth(String rawMonth) {
        if (rawMonth == null || rawMonth.trim().isEmpty()) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        String trimmed = rawMonth.trim();
        return trimmed.length() > 7 ? trimmed.substring(0, 7) : trimmed;
    }

    private String trimOrNull(String val) {
        if (val == null) return null;
        String t = val.trim();
        return t.isEmpty() ? null : t;
    }

    // 1〜15日 = "前半"、16日以降 = "後半"
    private String resolvePeriodType(String explicitPeriodType, int dayOfMonth) {
        String trimmed = trimOrNull(explicitPeriodType);
        if ("前半".equals(trimmed) || "後半".equals(trimmed)) {
            return trimmed;
        }
        return (dayOfMonth <= 15) ? "前半" : "後半";
    }

    private LocalDateTime parseTimeToDateTime(String rawTime, LocalDate baseDate) {
        String val = trimOrNull(rawTime);
        if (val == null) return null;

        try {
            if (val.contains("T")) {
                return LocalDateTime.parse(val);
            }
            LocalTime lt = LocalTime.parse(val);
            return LocalDateTime.of(baseDate, lt);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @GetMapping
    public String dashboard(HttpSession session,
                            HttpServletResponse response,
                            Model model,
                            @RequestParam(name = "startDate", required = false) String startDate,
                            @RequestParam(name = "endDate", required = false) String endDate) {
        if (isInvalidSession(session)) return "redirect:/?error=already_logged_in";
        User sessionUser = (User) session.getAttribute("user");
        User user = userRepo.findById(sessionUser.getUserId()).orElse(sessionUser);
        session.setAttribute("user", user);

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        Attendance active = attendanceRepo.findTopByUserIdAndEndTimeIsNullOrderByStartTimeDesc(user.getUserId());
        String breakKey = "activeBreakStart_" + user.getUserId();
        String lastBreakStartKey = "lastBreakStart_" + user.getUserId();
        String lastBreakEndKey = "lastBreakEnd_" + user.getUserId();

        LocalDateTime breakStart = (LocalDateTime) session.getAttribute(breakKey);

        String currentStatus = "OFF";
        if (active != null) {
            currentStatus = (breakStart != null) ? "REST" : "WORK";
        } else {
            session.removeAttribute(breakKey);
        }

        List<Attendance> allHistories = attendanceRepo.findAllByUserIdOrderByStartTimeDesc(user.getUserId());
        List<Attendance> filteredHistories;
        String titleLabel = "全期間の";

        if (startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
            try {
                LocalDateTime startOfPeriod = LocalDate.parse(startDate).atStartOfDay();
                LocalDateTime endOfPeriod = LocalDate.parse(endDate).atTime(23, 59, 59);

                filteredHistories = allHistories.stream()
                        .filter(h -> h.getStartTime() != null &&
                                     !h.getStartTime().isBefore(startOfPeriod) &&
                                     !h.getStartTime().isAfter(endOfPeriod))
                        .collect(Collectors.toList());

                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd");
                titleLabel = startOfPeriod.format(dtf) + " ～ " + endOfPeriod.format(dtf) + " の";

            } catch (Exception e) {
                filteredHistories = allHistories;
                startDate = "";
                endDate = "";
            }
        } else {
            filteredHistories = allHistories;
            startDate = "";
            endDate = "";
        }

        // 勤務履歴の「休憩開始」「休憩終了」に表示する時刻をセット
        LocalDateTime displayBreakStart = (breakStart != null)
                ? breakStart
                : (LocalDateTime) session.getAttribute(lastBreakStartKey);
        LocalDateTime displayBreakEnd = (breakStart != null)
                ? null
                : (LocalDateTime) session.getAttribute(lastBreakEndKey);

        boolean applied = false;
        for (Attendance h : filteredHistories) {
            if (h.getEndTime() == null) {
                if (displayBreakStart != null) {
                    h.setBreakStartTime(displayBreakStart);
                }
                if (displayBreakEnd != null) {
                    h.setBreakEndTime(displayBreakEnd);
                }
                applied = true;
            }
        }
        // 退勤済みの場合は最新レコード（先頭）に直近の休憩開始・終了を表示
        if (!applied && !filteredHistories.isEmpty()) {
            Attendance latest = filteredHistories.get(0);
            if (displayBreakStart != null) {
                latest.setBreakStartTime(displayBreakStart);
            }
            if (displayBreakEnd != null) {
                latest.setBreakEndTime(displayBreakEnd);
            }
        }

        model.addAttribute("user", user);
        model.addAttribute("myHistories", filteredHistories);
        model.addAttribute("status", currentStatus);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("titleLabel", titleLabel);

        // 来月分のシフト提出状況チェック（新スキーマ targetMonth / startTime / leaveType に準拠）
        LocalDate today = LocalDate.now();
        if (today.getDayOfMonth() >= 25) {
            LocalDate nextMonthDate = today.plusMonths(1);
            String nextMonthStr = nextMonthDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            int totalDaysInNextMonth = YearMonth.from(nextMonthDate).lengthOfMonth();

            long submittedCount = shiftRepo.findAll().stream()
                    .filter(s -> user.getUserId().equals(s.getUserId())
                            && s.getTargetMonth() != null
                            && nextMonthStr.equals(s.getTargetMonth().trim())
                            && (s.getStartTime() != null || (s.getLeaveType() != null && !s.getLeaveType().trim().isEmpty())))
                    .count();

            if (submittedCount < totalDaysInNextMonth) {
                model.addAttribute("showShiftAlert", Boolean.TRUE);
                model.addAttribute("alertMonth", nextMonthStr);
            } else {
                model.addAttribute("showShiftAlert", Boolean.FALSE);
            }
        } else {
            model.addAttribute("showShiftAlert", Boolean.FALSE);
        }

        return "partner_dash";
    }

    @PostMapping("/attendance/clock")
    public String clockAttendance(
            @RequestParam("type") String type,
            @RequestParam(value = "work_place", required = false) String workPlace,
            @RequestParam(value = "overtime_reason", required = false) String overtimeReason,
            HttpSession session,
            RedirectAttributes ra) {

        if (isInvalidSession(session)) return "redirect:/?error=already_logged_in";
        User user = (User) session.getAttribute("user");

        Attendance active = attendanceRepo.findTopByUserIdAndEndTimeIsNullOrderByStartTimeDesc(user.getUserId());

        if ("0".equals(type)) {
            if (active != null) {
                ra.addFlashAttribute("errorMsg", "すでに出勤しています。");
                return "redirect:/partner";
            }
            if (workPlace == null || workPlace.trim().isEmpty()) {
                ra.addFlashAttribute("errorMsg", "出勤時は勤務場所の入力が必須です。");
                return "redirect:/partner";
            }
            if (workPlace.length() > 100) {
                ra.addFlashAttribute("errorMsg", "勤務場所は100文字以内で入力してください。");
                return "redirect:/partner";
            }

            Attendance a = new Attendance();
            a.setUserId(user.getUserId());
            a.setUserName(user.getName());
            a.setStartTime(LocalDateTime.now());
            a.setWorkPlace(workPlace.trim());
            a.setBreakMinutes(0);
            attendanceRepo.save(a);

            session.removeAttribute("activeBreakStart_" + user.getUserId());
            session.removeAttribute("lastBreakStart_" + user.getUserId());
            session.removeAttribute("lastBreakEnd_" + user.getUserId());
            ra.addFlashAttribute("success", "出勤打刻しました。");

        } else if ("1".equals(type)) {
            if (active == null) {
                ra.addFlashAttribute("errorMsg", "出勤打刻がされていません。");
                return "redirect:/partner";
            }
            if (overtimeReason != null && overtimeReason.length() > 500) {
                ra.addFlashAttribute("errorMsg", "残業理由は500文字以内で入力してください。");
                return "redirect:/partner";
            }

            LocalDateTime now = LocalDateTime.now();
            String breakKey = "activeBreakStart_" + user.getUserId();
            LocalDateTime breakStart = (LocalDateTime) session.getAttribute(breakKey);
            if (breakStart != null) {
                long minutes = Duration.between(breakStart, now).toMinutes();
                if (minutes < 0) minutes = 0;
                int currentBreak = (active.getBreakMinutes() != null) ? active.getBreakMinutes() : 0;
                active.setBreakMinutes(currentBreak + (int) minutes);
                active.setBreakEndTime(now);
                session.setAttribute("lastBreakEnd_" + user.getUserId(), now);
                session.removeAttribute(breakKey);
            }

            active.setEndTime(now);
            if (overtimeReason != null && !overtimeReason.trim().isEmpty()) {
                active.setOvertimeReason(overtimeReason);
            }
            attendanceRepo.save(active);

            session.removeAttribute(breakKey);
            ra.addFlashAttribute("success", "退勤打刻しました。");
        }

        return "redirect:/partner";
    }

    @PostMapping("/break-start")
    public String breakStart(HttpSession session, RedirectAttributes ra) {
        if (isInvalidSession(session)) return "redirect:/?error=already_logged_in";
        User user = (User) session.getAttribute("user");
        Attendance a = attendanceRepo.findTopByUserIdAndEndTimeIsNullOrderByStartTimeDesc(user.getUserId());

        if (a == null) {
            ra.addFlashAttribute("errorMsg", "出勤中のデータが見つかりません。");
            return "redirect:/partner";
        }

        String key = "activeBreakStart_" + user.getUserId();
        if (session.getAttribute(key) == null) {
            LocalDateTime now = LocalDateTime.now();
            session.setAttribute(key, now);
            session.setAttribute("lastBreakStart_" + user.getUserId(), now);
            session.removeAttribute("lastBreakEnd_" + user.getUserId());
            a.setBreakStartTime(now);
            a.setBreakEndTime(null);
            ra.addFlashAttribute("success", "休憩を開始しました。");
        }
        return "redirect:/partner";
    }

    @PostMapping("/break-end")
    public String breakEnd(HttpSession session, RedirectAttributes ra) {
        if (isInvalidSession(session)) return "redirect:/?error=already_logged_in";
        User user = (User) session.getAttribute("user");
        Attendance a = attendanceRepo.findTopByUserIdAndEndTimeIsNullOrderByStartTimeDesc(user.getUserId());

        if (a == null) {
            ra.addFlashAttribute("errorMsg", "出勤中のデータが見つかりません。");
            return "redirect:/partner";
        }

        String key = "activeBreakStart_" + user.getUserId();
        LocalDateTime breakStart = (LocalDateTime) session.getAttribute(key);
        if (breakStart != null) {
            LocalDateTime now = LocalDateTime.now();
            long minutes = Duration.between(breakStart, now).toMinutes();
            if (minutes < 0) minutes = 0;

            int currentBreak = (a.getBreakMinutes() != null) ? a.getBreakMinutes() : 0;
            a.setBreakMinutes(currentBreak + (int) minutes);
            a.setBreakEndTime(now);
            attendanceRepo.save(a);

            session.setAttribute("lastBreakStart_" + user.getUserId(), breakStart);
            session.setAttribute("lastBreakEnd_" + user.getUserId(), now);
            session.removeAttribute(key);
            ra.addFlashAttribute("success", "休憩を終了しました（+" + minutes + "分）。");
        }
        return "redirect:/partner";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@RequestParam(value = "telephoneNum", required = false) String telephoneNum,
                                @RequestParam(value = "mailAddress", required = false) String mailAddress,
                                HttpSession session,
                                RedirectAttributes ra) {
        if (isInvalidSession(session)) return "redirect:/?error=already_logged_in";

        User sessionUser = (User) session.getAttribute("user");
        User dbUser = userRepo.findById(sessionUser.getUserId()).orElse(null);
        if (dbUser == null) {
            session.invalidate();
            return "redirect:/?error=already_logged_in";
        }

        String tel = (telephoneNum != null && !telephoneNum.trim().isEmpty()) ? telephoneNum.trim() : null;
        String mail = (mailAddress != null && !mailAddress.trim().isEmpty()) ? mailAddress.trim() : null;

        if (tel != null && tel.length() > 15) {
            ra.addFlashAttribute("errorMsg", "電話番号は15文字以内で入力してください。");
            return "redirect:/partner";
        }
        if (mail != null && mail.length() > 100) {
            ra.addFlashAttribute("errorMsg", "メールアドレスは100文字以内で入力してください。");
            return "redirect:/partner";
        }

        dbUser.setTelephoneNum(tel);
        dbUser.setMailAddress(mail);
        userRepo.save(dbUser);

        session.setAttribute("user", dbUser);
        ra.addFlashAttribute("success", "連絡先（電話番号・メールアドレス）を更新しました。");
        return "redirect:/partner";
    }

    @PostMapping("/password-reset")
    public String resetPassword(@RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                HttpSession session,
                                RedirectAttributes ra) {
        if (isInvalidSession(session)) return "redirect:/?error=already_logged_in";

        if (newPassword.matches(".*[^\\x21-\\x7e].*")) {
            return "redirect:/partner?error=invalid_characters";
        }

        if (!newPassword.equals(confirmPassword)) {
            return "redirect:/partner?error=password_mismatch";
        }

        User sessionUser = (User) session.getAttribute("user");
        User user = userRepo.findById(sessionUser.getUserId()).orElseThrow();

        if (newPassword.equals(user.getPasswordHash())) {
            return "redirect:/partner?error=same_as_old";
        }

        user.setPasswordHash(newPassword);
        userRepo.save(user);
        session.setAttribute("user", user);
        ra.addFlashAttribute("success", "パスワードを更新しました。");
        return "redirect:/partner";
    }

    @GetMapping("/shift/request")
    public String showShiftRequest(@RequestParam(name = "month", required = false) String month,
                                   HttpSession session,
                                   Model model) {
        if (isInvalidSession(session)) return "redirect:/?error=already_logged_in";
        User user = (User) session.getAttribute("user");
        model.addAttribute("user", user);

        String targetMonth = normalizeTargetMonth(month);
        model.addAttribute("currentMonth", targetMonth);

        List<Shift> userMonthShifts = new ArrayList<>();
        Map<String, String> htmlShifts = new HashMap<>();
        DateTimeFormatter hmFormatter = DateTimeFormatter.ofPattern("HH:mm");

        for (Shift s : shiftRepo.findAll()) {
            if (user.getUserId().equals(s.getUserId())
                    && s.getTargetMonth() != null
                    && targetMonth.equals(s.getTargetMonth().trim())) {
                userMonthShifts.add(s);

                if (s.getStartTime() != null) {
                    int day = s.getStartTime().getDayOfMonth();
                    String key = "shift_in_" + user.getUserId() + "_" + targetMonth + "_" + day;
                    htmlShifts.put(key, s.getStartTime().format(hmFormatter));
                }
                if (s.getEndTime() != null) {
                    int day = s.getEndTime().getDayOfMonth();
                    String key = "shift_out_" + user.getUserId() + "_" + targetMonth + "_" + day;
                    htmlShifts.put(key, s.getEndTime().format(hmFormatter));
                }
            }
        }

        model.addAttribute("shifts", userMonthShifts);
        model.addAttribute("dbShifts", htmlShifts);

        return "partner_shift_request";
    }

    @PostMapping("/shift/submit")
    public String submitShiftRequest(@RequestParam Map<String, String> allParams,
                                     @RequestParam(name = "targetMonth", required = false) String targetMonth,
                                     @RequestParam(name = "month", required = false) String monthParam,
                                     @RequestParam(name = "periodType", required = false) String periodType,
                                     @RequestParam(name = "startTime", required = false) String startTimeParam,
                                     @RequestParam(name = "endTime", required = false) String endTimeParam,
                                     @RequestParam(name = "leaveType", required = false) String leaveTypeParam,
                                     HttpSession session,
                                     RedirectAttributes ra) {
        if (isInvalidSession(session)) return "redirect:/?error=already_logged_in";
        User user = (User) session.getAttribute("user");

        String resolvedMonth = normalizeTargetMonth(
                (targetMonth != null && !targetMonth.trim().isEmpty()) ? targetMonth : monthParam
        );
        String resolvedLeave = trimOrNull(leaveTypeParam);

        boolean savedAny = false;
        LocalDateTime now = LocalDateTime.now();

        // 日別フォーム（shift_in_<userId>_<YYYY-MM>_<day> 形式）で送信された場合の処理
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue() != null ? entry.getValue().trim() : "";

            if (key.startsWith("shift_in_")) {
                int lastUnderscore = key.lastIndexOf('_');
                if (lastUnderscore < 0) continue;

                int day;
                try {
                    day = Integer.parseInt(key.substring(lastUnderscore + 1));
                } catch (NumberFormatException e) {
                    continue;
                }

                String withoutDay = key.substring(0, lastUnderscore);
                int secondLastUnderscore = withoutDay.lastIndexOf('_');
                if (secondLastUnderscore < 0) continue;

                String ymStr = normalizeTargetMonth(withoutDay.substring(secondLastUnderscore + 1));
                String prefixAndUser = withoutDay.substring(0, secondLastUnderscore);
                String userIdFromKey = prefixAndUser.substring("shift_in_".length());
                String targetUserId = (userIdFromKey != null && !userIdFromKey.isEmpty())
                        ? userIdFromKey
                        : user.getUserId();

                String outKey = "shift_out_" + targetUserId + "_" + ymStr + "_" + day;
                String endVal = allParams.getOrDefault(outKey, "").trim();

                if (val.isEmpty() && endVal.isEmpty()) {
                    continue;
                }

                YearMonth ym = YearMonth.parse(ymStr);
                int safeDay = Math.max(1, Math.min(day, ym.lengthOfMonth()));
                LocalDate baseDate = ym.atDay(safeDay);

                LocalDateTime startDt = parseTimeToDateTime(val, baseDate);
                LocalDateTime endDt = parseTimeToDateTime(endVal, baseDate);

                Shift shift = new Shift();
                shift.setUserId(targetUserId);
                shift.setTargetMonth(ymStr);
                shift.setPeriodType(resolvePeriodType(periodType, safeDay));
                shift.setStartTime(startDt);
                shift.setEndTime(endDt);
                shift.setLeaveType(resolvedLeave);
                shift.setSubmittedAt(now);

                shiftRepo.save(shift);
                savedAny = true;
            }
        }

        // 単一フォーム（targetMonth, startTime, endTime, leaveType, periodType）で送信された場合の処理
        if (!savedAny) {
            YearMonth ym = YearMonth.parse(resolvedMonth);
            int fallbackDay = Math.min(LocalDate.now().getDayOfMonth(), ym.lengthOfMonth());
            LocalDate baseDate = ym.atDay(fallbackDay);
            LocalDateTime startDt = parseTimeToDateTime(startTimeParam, baseDate);
            LocalDateTime endDt = parseTimeToDateTime(endTimeParam, baseDate);

            if (startDt != null || endDt != null || resolvedLeave != null) {
                Shift shift = new Shift();
                shift.setUserId(user.getUserId());
                shift.setTargetMonth(resolvedMonth);
                shift.setPeriodType(resolvePeriodType(periodType, fallbackDay));
                shift.setStartTime(startDt);
                shift.setEndTime(endDt);
                shift.setLeaveType(resolvedLeave);
                shift.setSubmittedAt(now);

                shiftRepo.save(shift);
                savedAny = true;
            }
        }

        if (!savedAny) {
            ra.addFlashAttribute("errorMsg", "入力内容が空のため保存されませんでした。");
        }

        String redirectUrl = "/partner/shift/request?success=true&month=" + resolvedMonth;
        return "redirect:" + redirectUrl;
    }
}