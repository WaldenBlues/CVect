package com.walden.cvect.infra.storage;

import java.io.IOException;
import java.io.InputStream;

public interface FileStorageService {

    String save(String storageKey, InputStream inputStream) throws IOException;

    InputStream load(String storageKey) throws IOException;

    boolean exists(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
