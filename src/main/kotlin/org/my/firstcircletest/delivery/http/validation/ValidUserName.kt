package org.my.firstcircletest.delivery.http.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@NotBlank(message = "Name cannot be blank")
@Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
@Constraint(validatedBy = [])
annotation class ValidUserName(
    val message: String = "Invalid user name",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)
