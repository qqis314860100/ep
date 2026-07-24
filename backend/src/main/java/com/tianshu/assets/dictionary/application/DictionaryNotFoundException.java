package com.tianshu.assets.dictionary.application;

public class DictionaryNotFoundException extends RuntimeException {
    public DictionaryNotFoundException(long id) {
        super("字典项不存在：" + id);
    }
}
