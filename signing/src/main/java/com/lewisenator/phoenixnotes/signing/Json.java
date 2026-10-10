package com.lewisenator.phoenixnotes.signing;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** JSON settings shared by release manifests and keys.json. */
final class Json {

    static final JsonMapper MAPPER = JsonMapper.builder()
            // Newer releases may add fields; older apps ignore what they don't know.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private Json() {}
}
