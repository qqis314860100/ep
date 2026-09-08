package com.tianshu.assets.ai.infrastructure;

import com.tianshu.assets.ai.domain.AiSuggestion;
import com.tianshu.assets.ai.domain.AiSuggestionRepository;
import com.tianshu.assets.ai.domain.AiSuggestionTargetType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("dev")
public class InMemoryAiSuggestionRepository implements AiSuggestionRepository {

    private final List<AiSuggestion> suggestions = new ArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public synchronized AiSuggestion save(AiSuggestion suggestion) {
        var saved = new AiSuggestion(
                nextId.getAndIncrement(),
                suggestion.targetType(),
                suggestion.targetId(),
                suggestion.targetTitle(),
                suggestion.status(),
                suggestion.source(),
                suggestion.proposed(),
                suggestion.evidence(),
                suggestion.confidence(),
                suggestion.scopes(),
                suggestion.createdBy(),
                suggestion.createdAt(),
                suggestion.resolvedBy(),
                suggestion.resolvedAt(),
                0);
        suggestions.add(saved);
        return saved;
    }

    @Override
    public synchronized AiSuggestion update(AiSuggestion suggestion, long expectedVersion) {
        var index = indexOf(suggestion.id());
        if (index < 0 || suggestions.get(index).version() != expectedVersion) {
            throw new IllegalStateException("版本不匹配");
        }
        var updated = new AiSuggestion(
                suggestion.id(),
                suggestion.targetType(),
                suggestion.targetId(),
                suggestion.targetTitle(),
                suggestion.status(),
                suggestion.source(),
                suggestion.proposed(),
                suggestion.evidence(),
                suggestion.confidence(),
                suggestion.scopes(),
                suggestion.createdBy(),
                suggestion.createdAt(),
                suggestion.resolvedBy(),
                suggestion.resolvedAt(),
                expectedVersion + 1);
        suggestions.set(index, updated);
        return updated;
    }

    @Override
    public synchronized Optional<AiSuggestion> findById(long id) {
        var index = indexOf(id);
        return index < 0 ? Optional.empty() : Optional.of(suggestions.get(index));
    }

    @Override
    public synchronized List<AiSuggestion> findByTarget(AiSuggestionTargetType targetType, long targetId) {
        return suggestions.stream()
                .filter(suggestion -> suggestion.targetType() == targetType && suggestion.targetId() == targetId)
                .sorted(Comparator.comparing(AiSuggestion::id))
                .toList();
    }

    @Override
    public synchronized List<AiSuggestion> findAll() {
        return suggestions.stream().sorted(Comparator.comparing(AiSuggestion::id)).toList();
    }

    private int indexOf(long id) {
        for (int i = 0; i < suggestions.size(); i++) {
            if (suggestions.get(i).id() == id) {
                return i;
            }
        }
        return -1;
    }
}
