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
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final int DEFAULT_PAID_LEAVE_DAYS = 20;

    @Autowired private UserRepository userRepo;
    @Autowired private AttendanceRepository attendanceRepo;
    @Autowired private ShiftRepository shiftRepo;

    // 管理者セッションチェック（新しいUserのuserId / roleで判定）
    private boolean isInvalidAdminSession(HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null || !"ADMIN".equals(sessionUser.getRole()) || sessionUser.getUserId() == null) {
            return true;
        }

        User dbUser = userRepo.findById(sessionUser.getUserId()).orElse(null);
        if (dbUser == null || Boolean.FALSE.equals(dbUser.getIsActive())) {
            session.invalidate();
            return true;
        }
        return false;
    }

    private void setNoCacheHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }

    private void keepFilters(RedirectAttributes attrs, String userId, String month, String start, String end) {
        if (userId != null && !userId.isEmpty()) attrs.addAttribute("userId", userId);
        if (month != null && !month.isEmpty()) attrs.addAttribute("month", month);
        if (start != null && !start.isEmpty()) attrs.addAttribute("startDate", start);
        if (end != null && !end.isEmpty()) attrs.addAttribute("endDate", end);
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
    public String dashboard(HttpSession session, HttpServletResponse response, Model model,
                            @RequestParam(name = "userId", required = false) String userId,
                            @RequestParam(name = "month", required = false) String month,
                            @RequestParam(name = "startDate", required = false) String startDate,
                            @RequestParam(name = "endDate", required = false) String endDate) {
        if (isInvalidAdminSession(session)) return "redirect:/?error=already_logged_in";
        setNoCacheHeaders(response);

        List<User> userlist = userRepo.findAll();
        model.addAttribute("userList", userlist);

        LocalDateTime startDatetime = null;
        LocalDateTime endDatetime = null;

        if (startDate != null && !startDate.isEmpty() && endDate != null && !endDate.isEmpty()) {
            startDatetime = LocalDate.parse(startDate).atStartOfDay();
            endDatetime = LocalDate.parse(endDate).plusDays(1).atStartOfDay().minusNanos(1);
        } else if (month != null && !month.isEmpty()) {
            YearMonth ym = YearMonth.parse(month);
            startDatetime = ym.atDay(1).atStartOfDay();
            endDatetime = ym.atEndOfMonth().plusDays(1).atStartOfDay().minusNanos(1);
        }

        List<Attendance> histories;
        boolean hasUser = (userId != null && !userId.isEmpty());
        boolean hasDate = (startDatetime != null && endDatetime != null);

        if (hasUser && hasDate) {
            histories = attendanceRepo.findAllByUserIdAndStartTimeBetweenOrderByStartTimeDesc(userId, startDatetime, endDatetime);
        } else if (hasUser) {
            histories = attendanceRepo.findAllByUserIdOrderByStartTimeDesc(userId);
        } else if (hasDate) {
            histories = attendanceRepo.findAllByStartTimeBetweenOrderByStartTimeDesc(startDatetime, endDatetime);
        } else {
            histories = attendanceRepo.findAllByOrderByStartTimeDesc();
        }

        model.addAttribute("selectedUserId", userId);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("selectedStartDate", startDate);
        model.addAttribute("selectedEndDate", endDate);
        model.addAttribute("histories", histories);

        // シフトデータの取得・復元処理
        List<Shift> dbShifts = shiftRepo.findAll();
        Map<String, String> savedShifts = new HashMap<>();
        DateTimeFormatter hmFormatter = DateTimeFormatter.ofPattern("HH:mm");

        for (Shift s : dbShifts) {
            String rawUid = s.getUserId();
            if (rawUid == null || s.getTargetMonth() == null) continue;

            String ymStr = s.getTargetMonth().trim();
            if (ymStr.isEmpty()) continue;

            String currentUid = rawUid.trim();
            if ("0".equals(currentUid) || "minus".equals(currentUid) || "none".equals(currentUid)) {
                currentUid = "minus";
                int day = (s.getStartTime() != null) ? s.getStartTime().getDayOfMonth() : 1;
                String minusVal = (s.getLeaveType() != null && !s.getLeaveType().trim().isEmpty())
                        ? s.getLeaveType().trim()
                        : ((s.getStartTime() != null) ? s.getStartTime().format(hmFormatter) : "");
                if (!minusVal.isEmpty()) {
                    savedShifts.put("shift_minus_" + currentUid + "_" + ymStr + "_" + day, minusVal);
                }
            } else {
                if (s.getStartTime() != null) {
                    int day = s.getStartTime().getDayOfMonth();
                    savedShifts.put("shift_in_" + currentUid + "_" + ymStr + "_" + day, s.getStartTime().format(hmFormatter));
                }
                if (s.getEndTime() != null) {
                    int day = s.getEndTime().getDayOfMonth();
                    savedShifts.put("shift_out_" + currentUid + "_" + ymStr + "_" + day, s.getEndTime().format(hmFormatter));
                }
            }
        }
        model.addAttribute("shifts", dbShifts);
        model.addAttribute("savedShifts", savedShifts);

        // 有給申請一覧とユーザーごとの有給残日数
        List<Shift> paidLeaveRequests = dbShifts.stream()
                .filter(s -> s.getLeaveType() != null && s.getLeaveType().trim().startsWith("有給"))
                .toList();

        Map<String, Integer> remainingLeaveMap = new HashMap<>();
        for (User u : userlist) {
            long approved = dbShifts.stream()
                    .filter(s -> u.getUserId().equals(s.getUserId())
                            && s.getLeaveType() != null
                            && "有給(承認)".equals(s.getLeaveType().trim()))
                    .count();
            remainingLeaveMap.put(u.getUserId(), Math.max(0, DEFAULT_PAID_LEAVE_DAYS - (int) approved));
        }

        model.addAttribute("paidLeaveRequests", paidLeaveRequests);
        model.addAttribute("remainingLeaveMap", remainingLeaveMap);

        return "admin_dash";
    }

    @PostMapping("/register")
    public String register(HttpSession session, HttpServletResponse response,
                           @RequestParam String name,
                           @RequestParam String password,
                           @RequestParam String role,
                           @RequestParam(required = false, defaultValue = "未設定") String department,
                           @RequestParam(required = false) String workPlace,
                           @RequestParam(required = false) String filterUserId,
                           @RequestParam(required = false) String filterMonth,
                           @RequestParam(required = false) String filterStartDate,
                           @RequestParam(required = false) String filterEndDate,
                           RedirectAttributes redirectAttributes) {
        if (isInvalidAdminSession(session)) return "redirect:/?error=already_logged_in";
        setNoCacheHeaders(response);

        String deptValue = (department == null || department.isBlank()) ? "未設定" : department.trim();
        String branchValue = (workPlace == null || workPlace.isBlank()) ? null : workPlace.trim();

        if (branchValue != null && branchValue.length() > 100) {
            redirectAttributes.addFlashAttribute("error", "【エラー】所属拠点は100文字以内で入力してください。");
            keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
            return "redirect:/admin";
        }

        User user = new User();//yoshino動作確認用
        user.setName(name.trim());
        user.setPasswordHash(password); // ※後ほどハッシュ化に置換
        user.setRole(role);
        user.setDepartment(deptValue);
        user.setWorkPlace(branchValue);
        user.setIsActive(true);

        int maxId = userRepo.findAll().stream()
                .map(u -> {
                    try {
                        return Integer.parseInt(u.getUserId());
                    } catch (NumberFormatException e) {
                        return 1000;
                    }
                })
                .max(Integer::compareTo)
                .orElse(1000);

        user.setUserId(String.valueOf(maxId + 1));
        userRepo.save(user);

        keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
        return "redirect:/admin";
    }

    @PostMapping("/update-department")
    public String updateDepartment(HttpSession session, HttpServletResponse response,
                                   @RequestParam String targetId,
                                   @RequestParam(required = false) String department,
                                   @RequestParam(required = false) String workPlace,
                                   @RequestParam(required = false) String filterUserId,
                                   @RequestParam(required = false) String filterMonth,
                                   @RequestParam(required = false) String filterStartDate,
                                   @RequestParam(required = false) String filterEndDate,
                                   RedirectAttributes redirectAttributes) {
        if (isInvalidAdminSession(session)) return "redirect:/?error=already_logged_in";
        setNoCacheHeaders(response);

        User targetUser = userRepo.findById(targetId).orElse(null);
        if (targetUser != null) {
            String deptValue = (department == null || department.isBlank()) ? "未設定" : department.trim();
            String branchValue = (workPlace == null || workPlace.isBlank()) ? null : workPlace.trim();

            if (deptValue.length() > 50) {
                redirectAttributes.addFlashAttribute("error", "【エラー】所属部署は50文字以内で入力してください。");
                keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
                return "redirect:/admin";
            }
            if (branchValue != null && branchValue.length() > 100) {
                redirectAttributes.addFlashAttribute("error", "【エラー】所属拠点は100文字以内で入力してください。");
                keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
                return "redirect:/admin";
            }

            targetUser.setDepartment(deptValue);
            targetUser.setWorkPlace(branchValue);
            userRepo.save(targetUser);
            redirectAttributes.addFlashAttribute("success", targetUser.getName() + " の所属情報を更新しました。");
        } else {
            redirectAttributes.addFlashAttribute("error", "【エラー】対象ユーザーが見つかりませんでした。");
        }

        keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
        return "redirect:/admin";
    }

    @PostMapping("/edit")
    public String edit(HttpSession session, HttpServletResponse response,
                       @RequestParam Long id, @RequestParam String startTime, @RequestParam String endTime,
                       @RequestParam(required = false, defaultValue = "0") Integer breakMinutes,
                       @RequestParam(required = false) String filterUserId, @RequestParam(required = false) String filterMonth,
                       @RequestParam(required = false) String filterStartDate, @RequestParam(required = false) String filterEndDate,
                       RedirectAttributes redirectAttributes) {
        if (isInvalidAdminSession(session)) return "redirect:/?error=already_logged_in";
        setNoCacheHeaders(response);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = LocalDateTime.parse(startTime);
        LocalDateTime end = null;

        if (start.isAfter(now)) {
            redirectAttributes.addFlashAttribute("error", "【エラー】未来の日時は登録・修正できません。");
            keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
            return "redirect:/admin";
        }

        if (!endTime.isEmpty()) {
            end = LocalDateTime.parse(endTime);
            if (end.isAfter(now)) {
                redirectAttributes.addFlashAttribute("error", "【エラー】未来の日時は登録・修正できません。");
                keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
                return "redirect:/admin";
            }
            if (end.isBefore(start)) {
                redirectAttributes.addFlashAttribute("error", "【エラー】退勤時間が、出勤時間より過去に設定されています。修正できませんでした。");
                keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
                return "redirect:/admin";
            }
            long totalMinutes = Duration.between(start, end).toMinutes();
            if (breakMinutes > totalMinutes) {
                redirectAttributes.addFlashAttribute("error", "【エラー】休憩時間が勤務時間（拘束時間）をオーバーしています。");
                keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
                return "redirect:/admin";
            }
        }

        Attendance a = attendanceRepo.findById(id).orElseThrow();
        a.setStartTime(start);
        a.setEndTime(end);
        a.setBreakMinutes(breakMinutes);
        attendanceRepo.save(a);

        redirectAttributes.addFlashAttribute("success", "打刻を上書き保存しました。");
        keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
        return "redirect:/admin";
    }

    @PostMapping("/delete-user")
    public String deleteUser(HttpSession session, HttpServletResponse response,
                             @RequestParam String targetId, @RequestParam String adminPassword,
                             @RequestParam(required = false) String filterUserId, @RequestParam(required = false) String filterMonth,
                             @RequestParam(required = false) String filterStartDate, @RequestParam(required = false) String filterEndDate,
                             RedirectAttributes redirectAttributes) {
        if (isInvalidAdminSession(session)) return "redirect:/?error=already_logged_in";
        setNoCacheHeaders(response);

        User admin = (User) session.getAttribute("user");
        if (admin != null) {
            if (!admin.getPasswordHash().equals(adminPassword)) {
                redirectAttributes.addFlashAttribute("error", "【エラー】パスワードが間違っています。削除できませんでした。");
                keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
                return "redirect:/admin";
            }
        }

        User targetUser = userRepo.findById(targetId).orElse(null);
        if (targetUser != null) {
            List<Attendance> userAttendances = attendanceRepo.findAllByUserIdOrderByStartTimeDesc(targetUser.getUserId());
            attendanceRepo.deleteAll(userAttendances);

            try {
                shiftRepo.deleteByUserId(targetUser.getUserId());
            } catch (Exception e) {
                System.out.println("シフト削除エラー: " + e.getMessage());
            }

            userRepo.delete(targetUser);
            redirectAttributes.addFlashAttribute("success", "ユーザー「" + targetUser.getName() + "」と、対応するシフト・勤怠データを削除しました。");
        }

        keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
        return "redirect:/admin";
    }

    @PostMapping("/create-attendance")
    public String createAttendance(HttpSession session, HttpServletResponse response,
                                   @RequestParam String targetUserId, @RequestParam String startTime, @RequestParam String endTime,
                                   @RequestParam(required = false, defaultValue = "0") Integer breakMinutes,
                                   @RequestParam(required = false) String filterUserId, @RequestParam(required = false) String filterMonth,
                                   @RequestParam(required = false) String filterStartDate, @RequestParam(required = false) String filterEndDate,
                                   RedirectAttributes redirectAttributes) {
        if (isInvalidAdminSession(session)) return "redirect:/?error=already_logged_in";
        setNoCacheHeaders(response);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = LocalDateTime.parse(startTime);
        LocalDateTime end = null;

        if (start.isAfter(now)) {
            redirectAttributes.addFlashAttribute("error", "【エラー】未来の日時は登録できません。");
            keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
            return "redirect:/admin";
        }

        if (endTime != null && !endTime.isEmpty()) {
            end = LocalDateTime.parse(endTime);
            if (end.isAfter(now)) {
                redirectAttributes.addFlashAttribute("error", "【エラー】未来の日時は登録できません。");
                keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
                return "redirect:/admin";
            }
            if (end.isBefore(start)) {
                redirectAttributes.addFlashAttribute("error", "【エラー】退勤時間が、出勤時間より過去に設定されています。登録できませんでした。");
                keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
                return "redirect:/admin";
            }
            long totalMinutes = Duration.between(start, end).toMinutes();
            if (breakMinutes > totalMinutes) {
                redirectAttributes.addFlashAttribute("error", "【エラー】休憩時間が勤務時間（拘束時間）をオーバーしています。");
                keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
                return "redirect:/admin";
            }
        }

        User targetUser = userRepo.findById(targetUserId).orElse(null);
        if (targetUser != null) {
            Attendance a = new Attendance();
            a.setUserId(targetUser.getUserId());
            a.setStartTime(start);
            a.setEndTime(end);
            a.setBreakMinutes(breakMinutes);
            attendanceRepo.save(a);
            redirectAttributes.addFlashAttribute("success", targetUser.getName() + " の打刻を新規登録しました。");
        }

        keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
        return "redirect:/admin";
    }

    @PostMapping("/delete-attendances")
    public String deleteAttendances(HttpSession session, HttpServletResponse response,
                                    @RequestParam(required = false) List<Long> attendanceIds,
                                    @RequestParam(required = false) String filterUserId, @RequestParam(required = false) String filterMonth,
                                    @RequestParam(required = false) String filterStartDate, @RequestParam(required = false) String filterEndDate,
                                    RedirectAttributes redirectAttributes) {
        if (isInvalidAdminSession(session)) return "redirect:/?error=already_logged_in";
        setNoCacheHeaders(response);

        if (attendanceIds != null && !attendanceIds.isEmpty()) {
            attendanceRepo.deleteAllById(attendanceIds);
            redirectAttributes.addFlashAttribute("success", attendanceIds.size() + "件の打刻履歴を削除しました。");
        } else {
            redirectAttributes.addFlashAttribute("error", "削除する項目が選択されていません。");
        }

        keepFilters(redirectAttributes, filterUserId, filterMonth, filterStartDate, filterEndDate);
        return "redirect:/admin";
    }

    @PostMapping("/save-shifts")
    public String saveShifts(HttpSession session, HttpServletResponse response,
                             @RequestParam Map<String, String> allParams,
                             @RequestParam(required = false) String month,
                             @RequestParam(required = false) String userId,
                             @RequestParam(required = false) String startDate,
                             @RequestParam(required = false) String endDate,
                             RedirectAttributes redirectAttributes) {

        if (isInvalidAdminSession(session)) return "redirect:/?error=already_logged_in";
        setNoCacheHeaders(response);

        try {
            LocalDateTime now = LocalDateTime.now();

            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                String key = entry.getKey();
                String value = trimOrNull(entry.getValue());

                if (key.startsWith("shift_")) {
                    String[] parts = key.split("_");
                    if (parts.length < 5) continue;

                    String type = parts[1];
                    int dayNum;
                    try {
                        dayNum = Integer.parseInt(parts[parts.length - 1]);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    String recordMonth = normalizeTargetMonth(parts[parts.length - 2]);
                    YearMonth ym = YearMonth.parse(recordMonth);
                    int safeDay = Math.max(1, Math.min(dayNum, ym.lengthOfMonth()));
                    LocalDate baseDate = ym.atDay(safeDay);

                    String shiftUserId;
                    if ("minus".equals(type)) {
                        shiftUserId = "0";
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 2; i < parts.length - 2; i++) {
                            if (i > 2) sb.append("_");
                            sb.append(parts[i]);
                        }
                        shiftUserId = sb.toString();
                    }

                    if (value == null) continue;

                    Shift shift = new Shift();
                    shift.setUserId(shiftUserId);
                    shift.setTargetMonth(recordMonth);
                    shift.setPeriodType(resolvePeriodType(allParams.get("periodType"), safeDay));
                    shift.setSubmittedAt(now);

                    if ("in".equals(type)) {
                        shift.setStartTime(parseTimeToDateTime(value, baseDate));
                    } else if ("out".equals(type)) {
                        shift.setEndTime(parseTimeToDateTime(value, baseDate));
                    } else if ("minus".equals(type)) {
                        shift.setLeaveType(value);
                        LocalDateTime dt = parseTimeToDateTime(value, baseDate);
                        if (dt != null) {
                            shift.setStartTime(dt);
                        }
                    }

                    shiftRepo.save(shift);
                }
            }
            redirectAttributes.addFlashAttribute("success", "変更されたシフトを保存しました。");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "保存中にエラーが発生しました: " + e.getMessage());
        }

        keepFilters(redirectAttributes, userId, month, startDate, endDate);
        return "redirect:/admin";
    }

    @PostMapping("/leave/approve")
    public String approveLeave(@RequestParam("shiftId") Long shiftId,
                               @RequestParam(required = false) String userId,
                               @RequestParam(required = false) String month,
                               @RequestParam(required = false) String startDate,
                               @RequestParam(required = false) String endDate,
                               HttpSession session,
                               HttpServletResponse response,
                               RedirectAttributes redirectAttributes) {
        if (isInvalidAdminSession(session)) return "redirect:/?error=already_logged_in";
        setNoCacheHeaders(response);

        Shift shift = shiftRepo.findById(shiftId).orElse(null);
        if (shift == null) {
            redirectAttributes.addFlashAttribute("error", "対象の有給申請が見つかりませんでした。");
        } else if (!"有給(申請)".equals(shift.getLeaveType())) {
            redirectAttributes.addFlashAttribute("error", "この有給申請はすでに処理済みです。");
        } else {
            shift.setLeaveType("有給(承認)");
            shift.setSubmittedAt(LocalDateTime.now());
            shiftRepo.save(shift);
            redirectAttributes.addFlashAttribute("success", "有給申請（ID:" + shiftId + "）を承認しました。");
        }

        keepFilters(redirectAttributes, userId, month, startDate, endDate);
        redirectAttributes.addFlashAttribute("activeTab", "leave");
        return "redirect:/admin#leave-pane";
    }

    @PostMapping("/leave/reject")
    public String rejectLeave(@RequestParam("shiftId") Long shiftId,
                              @RequestParam(required = false) String userId,
                              @RequestParam(required = false) String month,
                              @RequestParam(required = false) String startDate,
                              @RequestParam(required = false) String endDate,
                              HttpSession session,
                              HttpServletResponse response,
                              RedirectAttributes redirectAttributes) {
        if (isInvalidAdminSession(session)) return "redirect:/?error=already_logged_in";
        setNoCacheHeaders(response);

        Shift shift = shiftRepo.findById(shiftId).orElse(null);
        if (shift == null) {
            redirectAttributes.addFlashAttribute("error", "対象の有給申請が見つかりませんでした。");
        } else if (!"有給(申請)".equals(shift.getLeaveType())) {
            redirectAttributes.addFlashAttribute("error", "この有給申請はすでに処理済みです。");
        } else {
            shift.setLeaveType("有給(却下)");
            shift.setSubmittedAt(LocalDateTime.now());
            shiftRepo.save(shift);
            redirectAttributes.addFlashAttribute("success", "有給申請（ID:" + shiftId + "）を却下しました。");
        }

        keepFilters(redirectAttributes, userId, month, startDate, endDate);
        redirectAttributes.addFlashAttribute("activeTab", "leave");
        return "redirect:/admin#leave-pane";
    }

    @PostMapping("/leave/reopen")
    public String reopenLeave(@RequestParam("shiftId") Long shiftId,
                              @RequestParam(required = false) String userId,
                              @RequestParam(required = false) String month,
                              @RequestParam(required = false) String startDate,
                              @RequestParam(required = false) String endDate,
                              HttpSession session,
                              HttpServletResponse response,
                              RedirectAttributes redirectAttributes) {
        if (isInvalidAdminSession(session)) return "redirect:/?error=already_logged_in";
        setNoCacheHeaders(response);

        Shift shift = shiftRepo.findById(shiftId).orElse(null);
        if (shift == null) {
            redirectAttributes.addFlashAttribute("error", "対象の有給申請が見つかりませんでした。");
        } else if (!"有給(承認)".equals(shift.getLeaveType()) && !"有給(却下)".equals(shift.getLeaveType())) {
            redirectAttributes.addFlashAttribute("error", "差し戻しできる状態ではありません。");
        } else {
            shift.setLeaveType("有給(申請)");
            shift.setSubmittedAt(LocalDateTime.now());
            shiftRepo.save(shift);
            redirectAttributes.addFlashAttribute("success", "有給申請（ID:" + shiftId + "）を申請中に差し戻しました。");
        }

        keepFilters(redirectAttributes, userId, month, startDate, endDate);
        redirectAttributes.addFlashAttribute("activeTab", "leave");
        return "redirect:/admin#leave-pane";
    }
}