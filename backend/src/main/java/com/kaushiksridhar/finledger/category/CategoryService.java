package com.kaushiksridhar.finledger.category;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.user.UserRepository;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public CategoryService(CategoryRepository categoryRepository, UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(long userId) {
        return categoryRepository.findVisibleTo(userId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(long userId, CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.nameTaken(name, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "A category with this name already exists");
        }

        Category category = new Category();
        category.setUser(userRepository.getReferenceById(userId));
        category.setName(name);
        category.setKind(request.kind());
        category.setColor(request.color().toLowerCase(Locale.ROOT));

        return CategoryResponse.from(categoryRepository.save(category));
    }

    /** Built-in or the user's own. Anything else is reported as not found. */
    public Category getVisible(long userId, long categoryId) {
        return categoryRepository.findVisible(categoryId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found"));
    }
}
