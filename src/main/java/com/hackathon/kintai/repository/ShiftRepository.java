package com.hackathon.kintai.repository;

import com.hackathon.kintai.model.Shift;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, Long> {

    @Query("""
            select s from Shift s
            where s.userId = :userId
              and trim(s.targetMonth) = :targetMonth
            order by s.startTime asc, s.shiftId asc
            """)
    List<Shift> findByUserIdAndTargetMonth(
            @Param("userId") String userId,
            @Param("targetMonth") String targetMonth);

    @Query("""
            select s from Shift s
            where trim(s.targetMonth) = :targetMonth
            order by s.userId asc, s.startTime asc, s.shiftId asc
            """)
    List<Shift> findByTargetMonth(@Param("targetMonth") String targetMonth);

    @Modifying
    @Transactional
    @Query("delete from Shift s where s.userId = :userId")
    void deleteByUserId(@Param("userId") String userId);
}