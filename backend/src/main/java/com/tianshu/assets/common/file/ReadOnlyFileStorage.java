package com.tianshu.assets.common.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("oceanbase")
public class ReadOnlyFileStorage implements FileStorage {

    @Override
    public String store(InputStream content, long size, String originalFilename, String contentType) throws IOException {
        throw new UnsupportedOperationException("OceanBase 只读适配器尚未配置对象存储");
    }

    @Override
    public Optional<StoredFile> open(String storageKey) {
        return Optional.empty();
    }
}
