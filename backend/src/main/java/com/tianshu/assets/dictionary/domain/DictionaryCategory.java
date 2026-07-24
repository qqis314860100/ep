package com.tianshu.assets.dictionary.domain;

public record DictionaryCategory(
        String code,
        String name,
        String groupName,
        String parentCategory,
        String description,
        int sortOrder) {}
