package org.my.firstcircletest.delivery.http.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@NotBlank(message = "User ID cannot be blank")
@Size(min = 1, max = 100, message = "User ID must be between 1 and 100 characters")
@Constraint(validatedBy = [])
annotation class ValidUserId(
    val message: String = "Invalid user ID",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)
