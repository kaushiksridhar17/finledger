package com.kaushiksridhar.finledger.split;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByGroupIdOrderByIdAsc(Long groupId);

    /** Every member of every group the user owns (with their group), oldest first, so SELF comes first in each group. */
    @Query("select m from GroupMember m join fetch m.group g where g.user.id = :userId order by m.id")
    List<GroupMember> findAllForUser(@Param("userId") Long userId);

    Optional<GroupMember> findByIdAndGroupId(Long id, Long groupId);

    // Names are compared by the column's case-insensitive collation, so "rohan" clashes with "Rohan"
    boolean existsByGroupIdAndName(Long groupId, String name);

    boolean existsByGroupIdAndNameAndIdNot(Long groupId, String name, Long id);

    long countByGroupId(Long groupId);
}
