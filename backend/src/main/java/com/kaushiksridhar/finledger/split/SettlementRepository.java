package com.kaushiksridhar.finledger.split;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    @EntityGraph(attributePaths = { "fromMember", "toMember" })
    List<Settlement> findByGroupIdOrderBySettledOnDescIdDesc(Long groupId);

    @EntityGraph(attributePaths = { "fromMember", "toMember", "transaction" })
    Optional<Settlement> findByIdAndGroupId(Long id, Long groupId);

    @Query("""
            select s.fromMember.id as memberId, sum(s.amountPaise) as total
            from Settlement s
            where s.group.user.id = :userId
              and (:groupId is null or s.group.id = :groupId)
            group by s.fromMember.id
            """)
    List<MemberTotal> sentByMember(@Param("userId") Long userId, @Param("groupId") Long groupId);

    @Query("""
            select s.toMember.id as memberId, sum(s.amountPaise) as total
            from Settlement s
            where s.group.user.id = :userId
              and (:groupId is null or s.group.id = :groupId)
            group by s.toMember.id
            """)
    List<MemberTotal> receivedByMember(@Param("userId") Long userId, @Param("groupId") Long groupId);

    @Query("select count(s) from Settlement s where s.fromMember.id = :memberId or s.toMember.id = :memberId")
    long countInvolving(@Param("memberId") Long memberId);
}
