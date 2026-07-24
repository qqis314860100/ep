package com.tianshu.assets.dictionary.api;

import com.tianshu.assets.dictionary.application.DictionaryService;
import com.tianshu.assets.dictionary.domain.DictionaryCategory;
import com.tianshu.assets.dictionary.domain.DictionaryItem;
import com.tianshu.assets.dictionary.domain.DictionaryStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dictionaries")
public class DictionaryController {

    private final DictionaryService service;

    public DictionaryController(DictionaryService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public List<DictionaryCategory> categories() {
        return service.categories();
    }

    @GetMapping("/items")
    public List<DictionaryItem> items(
            @RequestParam(required = false) String category,
            @RequestParam(name = "parent_id", required = false) Long parentId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) DictionaryStatus status) {
        return service.list(category, parentId, q, status);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public DictionaryItem create(@Valid @RequestBody SaveDictionaryItemRequest request) {
        return service.create(request.category(), request.code(), request.name(), request.parentId(),
                request.sortOrder(), request.description(), request.forwardName(), request.reverseName(),
                request.directional(), request.allowDuplicate());
    }

    @PatchMapping("/items/{id}")
    public DictionaryItem update(@PathVariable long id, @Valid @RequestBody SaveDictionaryItemRequest request) {
        return service.update(id, request.version(), request.code(), request.name(), request.parentId(),
                request.status(), request.sortOrder(), request.description(), request.forwardName(),
                request.reverseName(), request.directional(), request.allowDuplicate());
    }

    @PostMapping("/items/{id}/merge")
    public DictionaryItem merge(@PathVariable long id, @Valid @RequestBody MergeDictionaryItemRequest request) {
        return service.merge(id, request.targetId(), request.version());
    }

    public record SaveDictionaryItemRequest(
            @NotBlank String category,
            @NotBlank String code,
            @NotBlank String name,
            Long parentId,
            @NotNull DictionaryStatus status,
            @Min(0) int sortOrder,
            String description,
            String forwardName,
            String reverseName,
            boolean directional,
            boolean allowDuplicate,
            @Min(0) long version) {}

    public record MergeDictionaryItemRequest(@Min(1) long targetId, @Min(0) long version) {}
}
