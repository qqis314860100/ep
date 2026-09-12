package com.tianshu.assets.dictionary.application;

import com.tianshu.assets.dictionary.domain.DictionaryItem;
import java.util.List;
import java.util.Optional;

public interface DictionaryStore {
    List<DictionaryItem> findAll();

    Optional<DictionaryItem> findById(long id);

    DictionaryItem create(DictionaryItem item);

    DictionaryItem update(DictionaryItem item, long expectedVersion);
}
