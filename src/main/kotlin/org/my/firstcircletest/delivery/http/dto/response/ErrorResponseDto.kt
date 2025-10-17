package org.my.firstcircletest.delivery.http.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class ErrorResponseDto(
    @JsonProperty("error")
    val error: String,

    @JsonProperty("message")
    val message: String
)
