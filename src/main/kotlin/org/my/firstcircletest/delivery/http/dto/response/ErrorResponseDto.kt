package org.my.firstcircletest.delivery.http.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

data class ErrorResponseDto(
    @field:JsonProperty("error")
    val error: String,

    @field:JsonProperty("message")
    val message: String
)
