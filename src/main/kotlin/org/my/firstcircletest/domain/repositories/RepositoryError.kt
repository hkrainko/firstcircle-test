package org.my.firstcircletest.domain.repositories

sealed class RepositoryError(open val message: String) {

    data class NotFound(
        override val message: String = "Entity not found"
    ) : RepositoryError(message)

    data class CreationFailed(
        override val message: String = "Failed to create entity"
    ) : RepositoryError(message)

    data class UpdateFailed(
        override val message: String = "Failed to update entity"
    ) : RepositoryError(message)

    data class RetrievalFailed(
        override val message: String = "Failed to retrieve entity"
    ) : RepositoryError(message)

    data class DatabaseError(
        override val message: String = "Database operation failed"
    ) : RepositoryError(message)
}
