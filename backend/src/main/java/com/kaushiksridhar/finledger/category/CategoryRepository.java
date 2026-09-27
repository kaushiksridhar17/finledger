package com.kaushiksridhar.finledger.category;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// "left join" keeps built-in categories (no user) in the results. An inner join would silently drop them.
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Built-in categories plus this user's own. */
    @Query("""
            select c from Category c left join c.user u
            where u is null or u.id = :userId
            order by c.kind, c.name
            """)
    List<Category> findVisibleTo(@Param("userId") Long userId);

    /** A category this user is allowed to use: built-in or their own. */
    @Query("""
            select c from Category c left join c.user u
            where c.id = :id and (u is null or u.id = :userId)
            """)
    Optional<Category> findVisible(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
            select case when count(c) > 0 then true else false end
            from Category c left join c.user u
            where lower(c.name) = lower(:name) and (u is null or u.id = :userId)
            """)
    boolean nameTaken(@Param("name") String name, @Param("userId") Long userId);
}
