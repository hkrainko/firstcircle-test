package org.my.firstcircletest.delivery.http.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import jakarta.validation.constraints.Positive
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Positive(message = "Amount must be positive")
@Constraint(validatedBy = [])
annotation class ValidAmount(
    val message: String = "Invalid amount",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)
